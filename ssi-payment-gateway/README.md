# SSI Payment Gateway

Gateway che orchestra un pagamento elettronico a partire da una presentazione SSI OIDC4VP. Il servizio:

1. espone un endpoint REST per creare una richiesta di pagamento;
2. genera automaticamente la redirect verso `ssi-issuer-verifier` usando OIDC Authorization Code + PKCE;
3. riceve il callback, valida il `credential_preview` restituito dal portale e
4. accende un pagamento di test su una sandbox gratuita (Stripe Test Mode) oppure ritorna un esito simulato se non sono state configurate chiavi reali.

È incluso un micro front-end statico (`/demo/index.html`) pronto per una demo live.

## Come funziona il flusso

```
merchant backend --> POST /api/payments
                  <-- paymentId + redirectUrl
browser --------> redirect al portale OIDC (issuer/verifier)
wallet ---------- scansione / presentazione OIDC4VP
portal ---------> redirect back /oidc/callback?code=...
gateway --------> token exchange + credential preview
gateway --------> sandbox Stripe (payment_intent)
merchant <------- polling GET /api/payments/{id} oppure redirect finale
```

Il `credential_preview` deve contenere, nel `credentialSubject`, l'attributo `paymentMethodId` (es. `pm_card_visa` in modalità test). Un esempio di credenziale compatibile:

```json
{
  "type": ["VerifiableCredential", "IzylifePaymentCredential"],
  "credentialSubject": {
    "id": "did:example:holder123",
    "givenName": "Alice",
    "familyName": "Demo",
    "paymentMethodId": "pm_card_visa",
    "riskLevel": "low",
    "spendingLimit": 50000
  }
}
```

## Configurazione

| chiave | default | descrizione |
| --- | --- | --- |
| `ssi.issuer.base-url` | `http://localhost:9090` | URL di `ssi-issuer-verifier` |
| `ssi.issuer.client-id` / `client-secret` | `payment-gateway` / `change-me` | Client OIDC creato sul portale |
| `ssi.issuer.redirect-uri` | `http://localhost:9092/oidc/callback` | Copiare nel client registrato |
| `ssi.payment-gateway.default-return-url` | `http://localhost:9092/demo/result.html` | Dove riportare il browser dopo il pagamento |
| `sandbox.stripe.secret-key` | `sk_test_change_me` | Chiave test Stripe; se lasciata al default, il gateway simula comunque un esito `success` |
| `sandbox.stripe.payment-method-id` | `pm_card_visa` | Metodo di pagamento che verrà passato al PaymentIntent |

> Stripe Test Mode è gratuito. Creare un account su [dashboard.stripe.com/test/apikeys](https://dashboard.stripe.com/test/apikeys) e impostare la chiave `sk_test_...` nelle `application.yml` o nelle variabili d'ambiente.

## Demo rapida

1. Avviare MongoDB + `ssi-issuer-verifier` (vedi README del monorepo) e assicurarsi che il portale esponga `/oauth2/authorize` e `/oauth2/token`.
2. Registrare un client `payment-gateway` sul portale con redirect `http://localhost:9092/oidc/callback`, grant type `authorization_code`, PKCE e scope `openid profile credential_preview`.
3. Configurare/emettere una credenziale holder con l'attributo `paymentMethodId`.
4. Lanciare il gateway:
   ```bash
   cd ssi-payment-gateway
   mvn spring-boot:run
   ```
5. Aprire [http://localhost:9092/demo/index.html](http://localhost:9092/demo/index.html), inserire importo e descrizione e cliccare “Start SSI Payment”.
6. Effettuare il login/scan nel portale, autorizzare il codice. Il gateway effettua il token exchange e, se `sandbox.stripe.secret-key` è valorizzato, apre un `PaymentIntent` di test su Stripe. Si viene reindirizzati su `/demo/result.html?paymentId=...` dove si può vedere:
   - stato del pagamento (`AUTHORIZATION_REQUIRED`, `CREDENTIAL_VERIFIED`, `SANDBOX_CONFIRMED`, `FAILED`);
   - DID e nome holder;
   - identificativo del pagamento sandbox (`payment_intent` Stripe oppure `demo-...`);
   - JSON completo del `credential_preview`.

## API principali

### POST `/api/payments`
```json
{
  "amount": 19.9,
  "currency": "EUR",
  "description": "Coffee beans + reusable cup",
  "returnUrl": "http://localhost:9092/demo/result.html"
}
```
Risposta:
```json
{
  "paymentId": "8f1d...",
  "status": "AUTHORIZATION_REQUIRED",
  "authorizationUrl": "http://localhost:9090/oauth2/authorize?...",
  "returnUrl": "http://localhost:9092/demo/result.html"
}
```
Redirectare il browser verso `authorizationUrl`.

### GET `/api/payments/{id}`
Consente al merchant di conoscere lo stato finale e i dati SSI associati.

### GET `/oidc/callback`
Endpoint interno che gestisce il redirect dal portale. Non va chiamato manualmente; si limita a completare il flusso e rimandare l'utente alla `returnUrl`.

## Estendere la logica

- **Persistenza**: `InMemoryPaymentRepository` è sufficiente per la demo. Per ambienti reali sostituirla con un repository (Mongo, Postgres, Redis) mantenendo l'interfaccia `PaymentRepository`.
- **Sandbox alternative**: implementare `SandboxPaymentClient` per collegarsi ad altri PSP (es. PagoPA, Nexi). Il gateway usa DI quindi basta un nuovo bean `@Primary`.
- **Validazioni credenziale**: `CredentialPreviewParser` è il punto in cui rafforzare regole (es. check `riskLevel`, `spendingLimit`, expiry ecc.).

## Test

```
cd ssi-payment-gateway
mvn test
```

Coprono la generazione PKCE e il parsing del `credential_preview`. Ulteriori test possono mockare `SandboxPaymentClient` per validare lo state machine.
