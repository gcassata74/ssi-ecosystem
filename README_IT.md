# Piattaforma Demo SSI Izylife (target MePA)

Mono‑repo che raccoglie tutti i componenti del dimostratore Izylife di Self‑Sovereign Identity (SSI): portale issuer/verifier in Spring Boot, applicazione client di esempio per i verifier, SDK TypeScript riutilizzabile per l’autenticazione, e wallet Ionic per i titolari. Questo workspace è pensato per un contesto MePA e Pubblica Amministrazione, con flussi end‑to‑end di emissione (OIDC4VCI), presentazione (OIDC4VP), onboarding e autorizzazione in stile OAuth per i verifier.

## Contenuti

- [Vista d’insieme](#vista-dinsieme)
- [Architettura di sistema](#architettura-di-sistema)
- [Flussi end‑to‑end](#flussi-endtoend)
- [Progetti](#progetti)
  - [ssi-issuer-verifier](#ssi-issuer-verifier)
  - [ssi-client-application](#ssi-client-application)
  - [ssi-client-lib](#ssi-client-lib)
  - [ssi-wallet](#ssi-wallet)
- [Avvio dello stack demo](#avvio-dello-stack-demo)
- [Workflow di sviluppo](#workflow-di-sviluppo)
- [Riferimento configurazioni](#riferimento-configurazioni)
- [Endpoint REST & OIDC principali](#endpoint-rest--oidc-principali)
- [Testing & quality gates](#testing--quality-gates)
- [Troubleshooting rapido](#troubleshooting-rapido)
- [Risorse aggiuntive](#risorse-aggiuntive)

## Vista d’insieme

| Componente | Scopo | Tech | Porta di default |
| --- | --- | --- | --- |
| `ssi-issuer-verifier` | Portale operatore issuer & verifier, API OIDC4VCI/OIDC4VP, tenancy, integrazione SPID | Spring Boot 3.2, Angular 17, MongoDB | 9090 |
| `ssi-client-application` | Client di esempio per relying party che usa il portale tramite SDK condiviso | Spring Boot 3.5, Angular 20 | 9091 |
| `ssi-client-lib` | SDK TypeScript `@ssi/issuer-auth-client` (core + helper Angular) | TypeScript, tsup | _n/a_ |
| `ssi-wallet` | Wallet Ionic per i titolari (web, Android, iOS via Capacitor) | Angular 20, Capacitor 7 | 8100 (Ionic dev server) |

Accanto ai progetti si trovano automazioni di alto livello (`Makefile`), file di log e placeholder ngrok.

## Architettura di sistema

```
                          +----------------------+
                          |  MongoDB (demo)      |
                          |  Dati tenant e stato |
                          +----------+-----------+
                                     ^
                                     |
                 +-------------------+-------------------+
                 | Spring Boot @ 9090 (ssi-issuer-verifier)|
                 | - OIDC4VCI issuer                      |
                 | - OIDC4VP verifier                     |
                 | - REST API & WebSocket                 |
                 +----------+-----------------------------+
                            ^
            OIDC4VP redirect|                       Credential offers
                            |
   +------------------------+------------------------+
   |   SPA Angular (frontend issuer-verifier)        |
   |   onboarding via QR, emissione credenziali,     |
   |   dashboard di verifica                         |
   +------------------------+------------------------+
                            |
         OAuth2 / SDK       |                        OIDC4VCI
                            v
+----------------+      +------------------+      +-------------------+
| Browser / UI   |      | ssi-client-lib   |      | Wallet Ionic      |
| (Angular 20)   |----->| @ssi/issuer-     |<-----| (ssi-wallet)      |
| Client App     |      | auth-client SDK  |      | grant OIDC4VCI    |
| (9091)         |      | (PKCE, token,    |      | response OIDC4VP  |
|                |      | portal verifier) |      | generazione       |
+----------------+      +------------------+      +-------------------+
```

Concetti chiave:

- Il portale issuer/verifier è il backend autorevole. Espone endpoint REST per template di credenziali, emissione demo, verifica presentazioni, registrazione tenant, e gestisce entrambe le specifiche OIDC4VCI/OIDC4VP.
- L’SDK (`ssi-client-lib`) incapsula le interazioni in stile OAuth2 (authorization code, PKCE, refresh token, helper per SPA), così qualunque front‑end può riusare i medesimi flussi.
- L’app client di esempio mostra come i verifier integrano l’SDK per avviare il portale, ottenere bearer token con anteprime di credenziali, e visualizzare i claim risultanti.
- Il wallet Ionic è l’agente del titolare: consuma le credential offer, esegue il grant, e invia le presentazioni al portale verifier.

## Flussi end‑to‑end

### Emissione credenziale (OIDC4VCI)

1. Un operatore in `ssi-issuer-verifier` crea o riusa una credential offer (`/oidc4vci/credential-offers/{id}`) e la mostra come QR.
2. Il wallet scansiona il QR, recupera l’offer JSON e segue il grant indicato (`authorization_code` o `pre-authorized_code`).
3. Lo scambio token avviene su `/oidc4vci/token`. Il portale restituisce `access_token`, `token_type`, `expires_in` e un `c_nonce` per il proof binding.
4. Il wallet richiede la credenziale su `/oidc4vci/credential` inviando il proof JWT firmato con la binding key. Il demo service risponde con un payload `jwt_vc_json` firmato con la chiave issuer configurata in `application.yml`.
5. Il wallet salva la credenziale e conferma la ricezione tramite `/api/onboarding/issuer/credentials-received`, avanzando il carousel di onboarding mostrato all’operatore.

### Verifica (OIDC4VP + OAuth del verifier)

1. Un verifier nell’app client richiama `SsiAuthService.beginVerifierFlow()` che reindirizza l’utente al portale issuer/verifier (personalizzabile via `portalPath`).
2. Il portale genera una request OIDC4VP (`/oidc4vp/requests/{requestId}`) e il payload QR (`app.verifier.qr-payload`). Il wallet lo scansiona e invia la presentazione a `/oidc4vp/responses`.
3. Il portale valida nonce, definition ID, descriptor map e delega i controlli di proof a `VerificationService`. In caso di esito positivo emette un authorization code gestito da `VerifierAuthorizationService`.
4. Il browser torna all’app client, che scambia l’authorization code su `/oauth2/token`. L’access token contiene claim `credential_preview` che la UI decodifica per mostrare DID, attributi del titolare e JWT raw.
5. Refresh token e logout sono gestiti dall’SDK (`SsiAuthClient`) per mantenere sessioni o effettuare sign‑out federato.

### Onboarding tenant & SPID (opzionale)

- `/api/onboarding/*` espone la macchina a stati per orchestrare la rotazione dei QR tra login verifier, emissione credenziali e conferme wallet. Gli aggiornamenti WebSocket (STOMP su SockJS) alimentano la UI Angular.
- Con `app.spid.enabled=true`, Spring Security opera da Service Provider SPID. Gli operatori si autenticano via SAML e il portale espone i metadata su `/spid/metadata`. Utility in `src/test/java/com/izylife/ssi/tools/` aiutano a diagnosticare le risposte SPID.

## Progetti

### ssi-issuer-verifier

- **Stack:** Spring Boot 3.2, Maven, Angular 17, MongoDB, SockJS/STOMP per aggiornamenti live.
- **Funzionalità:**
  - Implementa `.well-known/openid-credential-issuer` e `.well-known/oauth-authorization-server`.
  - Endpoint per template di credenziali (`/api/credentials/templates`), emissione (`/api/credentials/issue`), verifica (`/api/verification/presentations`), tenant CRUD (`/api/tenants`), onboarding (`/api/onboarding/*`), metadata/login SPID.
  - Pipeline OIDC4VCI token/authorization/offer/credential basate su `Oidc4vciService`.
  - Gestione OIDC4VP request/response con firma JWT (`/oidc4vp/requests/{id}`, `/oidc4vp/responses`, `/oidc4vp/jwks.json`).
  - Chiavi demo configurabili per issuer/verifier (vedi `app.issuer.signing-key` e `app.verifier.signing-key` in `src/main/resources/application.yml`).
- **Build:** `mvn clean package` compila la SPA Angular, copia `frontend/dist` in `target/classes/static` e produce `target/ssi-issuer-verifier-0.0.1-SNAPSHOT.jar`.
- **Run:** `mvn spring-boot:run` (porta `9090`). Richiede MongoDB (URI di default `mongodb://localhost:27017/ssi-issuer-verifier`). Usa lo snippet Docker incluso per una istanza locale.
- **Frontend:** workspace Angular in `frontend/` (dev server via `npm start`). L’onboarding in real‑time usa l’endpoint SockJS `/ws` esposto da `WebSocketConfig`.

### ssi-client-application

- **Stack:** backend Spring Boot 3.5 (facade stub) + SPA Angular 20 in un build Maven padre.
- **Scopo:** dimostra la consumazione lato verifier del portale tramite SDK condiviso.
- **Comportamento frontend:**
  - Inizializza `SsiAuthService` da `@ssi/issuer-auth-client/angular`.
  - La CTA principale (`Go to Verifier`) richiama `beginVerifierFlow()` e porta l’utente al portale per la presentazione credenziali.
  - Dopo il redirect, i token da `tokens$` aggiornano la UI con DID, claim del credential subject e JWT raw.
- **Build & run:** `mvn -f backend/pom.xml spring-boot:run` (porta `9091`). `mvn -f backend/pom.xml generate-resources` installa Node in locale e produce il bundle Angular di produzione.
- **Dev mode:** `npm start` dentro `frontend/` per live reload; configura un proxy Angular se accedi direttamente al portale issuer.

### ssi-client-lib

- **Package:** `@ssi/issuer-auth-client` (core SDK + helper Angular).
- **Highlights:**
  - Avvio Authorization Code + PKCE (`login`), storage token, refresh pianificato, helper logout.
  - Helper per portale verifier (`beginVerifierFlow`) che preserva stato, PKCE verifier e URL originaria per riprendere la navigazione SPA.
  - Astrazione storage per usare persistenze custom (default `localStorage`/`sessionStorage`).
  - Event emitter per ciclo di vita autenticazione (`authenticated`, `token_refreshed`, `token_expired`, `logout`, `error`).
  - Provider Angular (`provideSsiAuth`), servizi (`SsiAuthService`), interceptor HTTP per integrazione immediata.
- **Build:** `npm install && npm run build` (bundle in `dist/` come ESM+CJS con type declarations). Il package Angular è pubblicato insieme ai bundle core.
- **Uso locale:** `npm pack` produce `ssi-issuer-auth-client-*.tgz`. L’app client referenzia `../../ssi-client-lib/ssi-issuer-auth-client-0.1.3.tgz`.

### ssi-wallet

- **Stack:** Ionic 8 + Angular 20 con Capacitor 7 per build native.
- **Struttura:** `mobile-app/` (sorgenti Angular/Ionic) e `docs/` (guide integrazione issuer, troubleshooting Ionic, walkthrough emissione credenziali Spring Boot con Nimbus JOSE).
- **Dev workflow:**
  - `make serve` (alias di `ionic serve`) su `http://localhost:8100`.
  - `make add-android`, `make sync`, `make run-android` per gestire i container nativi.
  - In `mobile-app/`: `npm test`, `npm run lint`, `npx cap sync`.
- **Ruolo nell’ecosistema:** agisce da titolare delle credenziali. Scansiona QR pre‑autorizzati, esegue lo scambio `/oidc4vci/token`, invia proof a `/oidc4vci/credential` e risponde alle richieste OIDC4VP del portale verifier.
- **Doc consigliate:** `docs/ionic-dev.md` per la parte di sviluppo e `docs/spring-issuer-credential.md` per la logica di emissione.

## Avvio dello stack demo

1. **Prerequisiti**
   - Java 17+
   - Maven 3.9+
   - Node.js 18+ (Node 20+ per il frontend del portale issuer)
   - npm 10+
   - Docker (opzionale ma consigliato per MongoDB)
2. **Avvia MongoDB**
   ```bash
   docker run --name ssi-mongo -p 27017:27017 -d mongo:7
   ```
3. **Bootstrap asset Angular (prima volta)**
   ```bash
    # portale issuer
   (cd ssi-issuer-verifier && mvn generate-resources)

    # app client
   (cd ssi-client-application && mvn -f backend/pom.xml generate-resources)
   ```
4. **Esegui tutto via Makefile**
   ```bash
   make run-ssi-demo
   ```
   - Avvia il portale issuer su `9090` e l’app client su `9091` in background, con log su `issuer.out` / `issuer.err` e `client.out` / `client.err`.
   - Lo step `ngrok` è commentato: decommentalo quando servono URL pubblici.
5. **Interagisci**
   - Vai su `http://localhost:9090` per il portale issuer/verifier.
   - Vai su `http://localhost:9091` per la SPA client verifier.
   - Usa il wallet (web o mobile) per scansionare il QR del portale.
6. **Osserva & ferma**
   ```bash
   make logs           # tail dei log
   make stop-ssi-demo  # termina i JVM in background
   make clean          # rimuove PID + file di log
   ```

Alternativa manuale:

```bash
(cd ssi-issuer-verifier && mvn spring-boot:run)
(cd ssi-client-application && mvn -f backend/pom.xml spring-boot:run)
```

## Workflow di sviluppo

- **Hot reload UI Angular:** `npm start` in `ssi-issuer-verifier/frontend` o `ssi-client-application/frontend`. Configura `proxy.conf.json` per evitare CORS quando chiami il backend.
- **SDK hacking:** lavora in `ssi-client-lib`, esegui `npm run build -- --watch` (o `npm link`) e aggiorna `ssi-client-application/frontend/package.json` al tarball locale.
- **Build native wallet:** esegui `make add-android` una volta, poi `make sync` prima di ricompilare in Android Studio. I comandi Capacitor vanno lanciati da `mobile-app/`.
- **Test SPID:** modifica le proprietà `app.spid.*` in `ssi-issuer-verifier/src/main/resources/application.yml`. Esporta i metadata con `curl http://localhost:9090/spid/metadata > spid.xml`.
- **Debug WebSocket:** iscriviti a `/topic/onboarding` con il portale Angular o client STOMP esterni per osservare gli stati di onboarding.

## Riferimento configurazioni

| Impostazione | Posizione | Note |
| --- | --- | --- |
| `server.port=9090` | `ssi-issuer-verifier/src/main/resources/application.yml` | Cambia la porta del portale issuer. |
| `server.port=9091` | `ssi-client-application/backend/src/main/resources/application.yml` | Cambia la porta del client verifier. |
| `SPRING_DATA_MONGODB_URI` | Variabile ambiente | Override della connessione MongoDB del portale issuer. |
| `app.issuer.endpoint` | `application.yml` | URL pubblico base issuer (ready per ngrok). |
| `app.issuer.signing-key` | `application.yml` | JWK EC P-256 demo per firme credenziali. Sostituire in produzione. |
| `app.verifier.qr-payload` | `application.yml` | Contenuto QR per richieste OIDC4VP (custom audience + challenge). |
| `app.spid.*` | `application.yml` | Abilita il comportamento da Service Provider SPID. |
| `app.keycloak.*` | `application.yml` | Abilita integrazione admin Keycloak (ruoli + presentation definition). |
| Config `@ssi/issuer-auth-client` | `ssi-client-application/frontend/src/app/app.config.ts` | Base URL, client ID, scope, redirect URI per l’SDK. |
| `portalPath` / `portalParams` | Config SDK | Dove `beginVerifierFlow()` indirizza il verifier (default `/`). |

L’SDK accetta ulteriori override:

- `endpoints.authorization`, `endpoints.token`, `endpoints.endSession` per deployment personalizzati.
- `storageKey` per evitare collisioni quando più applicazioni condividono lo stesso browser.
- `refreshSkewMs` per regolare la finestra di refresh automatico (default 60 secondi).

### Presentation definition gestita da Keycloak

Il portale issuer può leggere la presentation definition OIDC4VP (`credentials.json`) direttamente da Keycloak (porta default `9080`). Questo consente agli enti MePA di governare quali credenziali debbano essere presentate senza ricompilare il servizio Spring.

1. **Prepara Keycloak** – avvia `docker-compose -f docker/keycloak/docker-compose.yml up -d` (admin default `admin/admin`). Crea o seleziona il realm indicato in `APP_KEYCLOAK_REALM` (default `ssi`).
2. **Service account del portale** – crea un client confidenziale (ID default `ssi-issuer-verifier`) con *Service Accounts Enabled* e copia il client secret. Assegna i ruoli realm `view-users` e `view-clients` così da risolvere utenti e attributi client.
3. **Client wallet verifier** – crea un client con ID `APP_KEYCLOAK_PD_CLIENT_ID` (default `wallet-verifier`). In *Attributes* aggiungi `credentials.json` con la presentation definition completa che il wallet deve soddisfare. Il nome dell’attributo è personalizzabile via `APP_KEYCLOAK_PD_ATTRIBUTE`.
4. **Collega il portale** – imposta `APP_KEYCLOAK_ENABLED=true` e `APP_KEYCLOAK_PD_ENABLED=true`, più `APP_KEYCLOAK_BASE_URL`, `APP_KEYCLOAK_CLIENT_ID`, `APP_KEYCLOAK_CLIENT_SECRET`, e cache opzionale (`APP_KEYCLOAK_PD_CACHE_TTL`).

Quando la feature è attiva il portale scarica e mette in cache il JSON da Keycloak. Le modifiche in Keycloak si propagano dopo il TTL configurato. Se l’integrazione è disabilitata o manca l’attributo, il portale usa il file `staff-credential.json` built‑in.

## Endpoint REST & OIDC principali

| Endpoint | Metodo | Descrizione |
| --- | --- | --- |
| `/.well-known/openid-credential-issuer` | GET | Metadata issuer OIDC4VCI |
| `/.well-known/oauth-authorization-server` | GET | Metadata authorization server |
| `/oidc4vci/credential-offers/{offerId}` | GET | Recupera l’offer JSON |
| `/oidc4vci/token` | POST (form) | Scambia authorization o pre‑authorized code |
| `/oidc4vci/credential` | POST (JSON) | Emissione credenziale demo (`jwt_vc_json`) |
| `/oidc4vci/jwks.json` | GET | JWK set issuer per verifica firme |
| `/oidc4vp/requests/{requestId}` | GET | Request object OIDC4VP firmato (JWT) |
| `/oidc4vp/responses` | POST (form) | Processa presentazioni wallet, emette authorization code |
| `/oidc4vp/jwks.json` | GET | JWKS verifier per validazione request object |
| `/oauth2/token` | POST (form) | Scambio authorization code verifier per access token |
| `/api/credentials/templates` | GET | Lista template credenziali del portale |
| `/api/credentials/issue` | POST | Emissione credenziale mock + seed QR demo |
| `/api/verification/presentations` | POST | Verifica presentazioni programmatica |
| `/api/onboarding/*` | GET/POST | Gestione rotazione QR onboarding e ack |
| `/api/tenants` | GET/POST | Registry tenant su MongoDB |
| `/spid/metadata` | GET | Metadata SPID Service Provider |

Il client verifier di esempio consuma `/oauth2/token` tramite SDK; le altre API sono esposte dal portale Angular.

## Testing & quality gates

- **Backend portale issuer:** `mvn test` (unit + integrazione) e `mvn verify` per la build completa.
- **Frontend portale issuer:** `npm test`, `npm run lint`, `npm run build`.
- **Backend app client:** `mvn -f backend/pom.xml test`.
- **Frontend app client:** `npm test`, `npm run lint` dentro `frontend/`.
- **SDK:** `npm run lint` e `npm run build` (`tsup` verifica i tipi). Aggiungi test in `src/__tests__/` e lancia `npm test` quando configurato.
- **Wallet:** `npm test`, `npm run lint`. I test end‑to‑end/device si eseguono via Capacitor quando configurati.

Le pipeline CI/CD possono concatenare questi comandi; ogni sotto‑progetto è indipendente, quindi puoi testare solo i moduli toccati.

## Troubleshooting rapido

- **Connessione Mongo rifiutata:** verifica che il container Docker sia in esecuzione o imposta `SPRING_DATA_MONGODB_URI` su un’istanza attiva.
- **Errori Angular CLI durante build Maven:** riesegui `mvn generate-resources` per reinstallare il runtime Node gestito da `frontend-maven-plugin`.
- **Wallet non completa il grant:** controlla che `app.issuer.endpoint` punti al dominio ngrok pubblico e che le signing key corrispondano ai JWKS pubblicati.
- **Flusso verifier bloccato dopo QR:** controlla `issuer.out` per mismatch nonce/state e che la `redirectUri` dell’SDK coincida con quella registrata nel portale.
- **Fallimenti integrazione SPID:** abilita il logging DEBUG (`org.springframework.security.saml2=DEBUG`, `org.opensaml=DEBUG`) e cattura la risposta SAML con `src/test/java/com/izylife/ssi/tools/VerifySpidResponse`.
- **Refresh token non in SPA client:** verifica `refreshTokens=true` e che `/oauth2/token` emetta refresh token. L’SDK logga eventi `token_expired` quando non riesce a fare refresh.

## Risorse aggiuntive

- `ssi-issuer-verifier/README.md`, `ssi-client-application/README.md`, `ssi-client-lib/README.md`, `ssi-wallet/README.md` — documentazione dettagliata per modulo.
- `brochure-ssi-izylife.pdf` — presentazione di alto livello per stakeholder.
- `ssi-wallet/docs/spring-issuer-credential.md` — tutorial approfondito per implementare `/credential` con Nimbus JOSE.
- `ssi-wallet/docs/ionic-dev.md` — guida passo‑passo per debug Ionic e QR scanning.

Per domande o nuove feature, apri issue direttamente in questo repository così le discussioni restano legate al componente corretto.
