# Issuer Spring Boot (/credential) con Nimbus

Questa guida mostra come generare e firmare una Verifiable Credential in formato JWT (`jwt_vc_json`) per l'endpoint `/credential` dell'issuer OIDC4VCI, usando Spring Boot e la libreria Nimbus JOSE + JWT.

## Dipendenze Maven

Aggiungi a `pom.xml` (se non l'hai già) le dipendenze necessarie:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>com.nimbusds</groupId>
  <artifactId>nimbus-jose-jwt</artifactId>
  <version>9.37.3</version>
</dependency>
```

## Configurazione applicativa

L'issuer deve conoscere:

- l'URL pubblico (nel nostro caso l'URL ngrok: **non cambiarlo**, usalo così come ti viene fornito);
- la chiave privata con cui firmare la credenziale (in formato JWK EC P-256);
- il tempo di validità della credenziale.

Esempio `application.yml`:

```yaml
issuer:
  base-url: https://<tuo-subdominio-ngrok>.ngrok.app
  signing-key: >-
    {"kty":"EC","d":"...","crv":"P-256","x":"...","y":"...","kid":"issuer-key-2025"}
  credential-lifetime-seconds: 3600
```

> Il `kid` deve corrispondere ad una voce nel JWKS pubblico esposto su `https://issuer.izylife.com/.well-known/jwks.json`.

## DTO per la richiesta e la risposta `/credential`

```java
package com.example.issuer.credential;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CredentialRequest(
    String format,
    @JsonProperty("credential_definition") CredentialDefinition credentialDefinition,
    Proof proof
) {
    public record CredentialDefinition(List<String> type) {}

    public record Proof(
        @JsonProperty("proof_type") String proofType,
        String jwt
    ) {}
}
```

```java
package com.example.issuer.credential;

public record CredentialResponse(
    String format,
    String credential
) {}
```

## Servizio che valida il proof e firma la credenziale

```java
package com.example.issuer.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CredentialIssuerService {

    private final ECKey signingKey;
    private final ECDSASigner signer;
    private final String issuerBaseUrl;
    private final Duration credentialLifetime;

    public CredentialIssuerService(
        @Value("${issuer.base-url}") String issuerBaseUrl,
        @Value("${issuer.signing-key}") String signingKeyJson,
        @Value("${issuer.credential-lifetime-seconds:3600}") long credentialLifetimeSeconds
    ) throws ParseException, JOSEException {
        this.signingKey = ECKey.parse(signingKeyJson);
        this.signer = new ECDSASigner(signingKey.toECPrivateKey());
        this.issuerBaseUrl = issuerBaseUrl;
        this.credentialLifetime = Duration.ofSeconds(credentialLifetimeSeconds);
    }

    public CredentialResponse issuePublicAuthorityStaffCredential(
        CredentialRequest request,
        String subjectDid,
        String expectedCNonce,
        JWK walletBindingKey
    ) throws ParseException, JOSEException, BadJOSEException {

        if (!"jwt_vc_json".equals(request.format())) {
            throw new IllegalArgumentException("Formato credenziale non supportato: " + request.format());
        }
        if (!request.credentialDefinition().type().contains("PublicAuthorityStaffCredential")) {
            throw new IllegalArgumentException("credential_definition.type inatteso");
        }

        SignedJWT proofJwt = SignedJWT.parse(request.proof().jwt());
        var verifierFactory = new DefaultJWSVerifierFactory();
        var verifier = verifierFactory.createJWSVerifier(proofJwt.getHeader(), walletBindingKey);
        if (!proofJwt.verify(verifier)) {
            throw new BadJOSEException("Proof JWT non valido: firma del wallet errata");
        }

        String nonce = proofJwt.getJWTClaimsSet().getStringClaim("nonce");
        if (!Objects.equals(nonce, expectedCNonce)) {
            throw new BadJOSEException("c_nonce non valido o scaduto");
        }

        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
            .issuer(issuerBaseUrl)
            .subject(subjectDid)
            .issueTime(java.util.Date.from(now))
            .notBeforeTime(java.util.Date.from(now))
            .expirationTime(java.util.Date.from(now.plus(credentialLifetime)))
            .claim("vc", Map.of(
                "@context", List.of("https://www.w3.org/2018/credentials/v1"),
                "type", List.of("VerifiableCredential", "PublicAuthorityStaffCredential"),
                "credentialSubject", Map.of(
                    "name", "Giuseppe Cassata",
                    "role", "Public Authority Staff",
                    "organization", "Izylife Solutions S.R.L."
                )
            ))
            .build();

        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType.JWT)
            .keyID(signingKey.getKeyID())
            .build();

        SignedJWT credentialJwt = new SignedJWT(header, claims);
        credentialJwt.sign(signer);

        return new CredentialResponse("jwt_vc_json", credentialJwt.serialize());
    }
}
```

### Cosa fa il servizio

1. Controlla `format` e `credential_definition.type` richiesti dal wallet.
2. Parsea e verifica il `proof.jwt` firmato dal wallet (devi risolvere il `JWK` pubblico del wallet, ad esempio dalla `did` registrata durante l'onboarding).
3. Confronta il `nonce` del proof con il `c_nonce` associato all'access token emesso nella fase `/token`.
4. Costruisce il payload JWT con le claim standard (`iss`, `sub`, `iat`, `nbf`, `exp`) e la sezione `vc`.
5. Firma il JWT con la chiave EC P-256 dell'issuer e restituisce la stringa compact nel campo `credential`.

## Controller REST `/credential`

```java
package com.example.issuer.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.proc.BadJOSEException;
import java.text.ParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CredentialController {

    private final CredentialIssuerService credentialIssuerService;
    private final CNonceStore cNonceStore;
    private final WalletBindingResolver walletBindingResolver;

    public CredentialController(
        CredentialIssuerService credentialIssuerService,
        CNonceStore cNonceStore,
        WalletBindingResolver walletBindingResolver
    ) {
        this.credentialIssuerService = credentialIssuerService;
        this.cNonceStore = cNonceStore;
        this.walletBindingResolver = walletBindingResolver;
    }

    @PostMapping("/credential")
    public ResponseEntity<CredentialResponse> issueCredential(
        @AuthenticationPrincipal AccessTokenPrincipal principal,
        @RequestBody CredentialRequest request
    ) throws ParseException, JOSEException, BadJOSEException {

        String subjectDid = principal.getWalletDid();
        String expectedCNonce = cNonceStore.consume(principal.getTokenId());
        JWK walletKey = walletBindingResolver.resolve(subjectDid);

        CredentialResponse response = credentialIssuerService.issuePublicAuthorityStaffCredential(
            request,
            subjectDid,
            expectedCNonce,
            walletKey
        );

        return ResponseEntity.ok(response);
    }
}
```

- `AccessTokenPrincipal` rappresenta le informazioni estratte dall'access token OAuth2 rilasciato al wallet (tipicamente contiene `sub = did` e l'identificativo del token).
- `CNonceStore` è un componente che salva il `c_nonce` generato in fase `/token` e lo invalida (`consume`) dopo l'uso.
- `WalletBindingResolver` recupera la chiave pubblica del wallet (es. da una tabella locale o risolvendo la DID) per validare il proof.

## Risposta finale del controller

Una volta firmato, il controller restituisce:

```json
{
  "format": "jwt_vc_json",
  "credential": "eyJraWQiOiJpc3N1ZXIta2V5LTIwMjUiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2lzc3Vlci5pemlsaWZlLmNvbSIsInN1YiI6ImRpZDprZXk6ejJvQXRmTG9mOUhWNlZKRjhmRlo5Slk5MUhWeGk2cnFrM3F3RGRUZEx4NlZneTlRYyIsImlhdCI6MTcwNzk1NjI4NywiZXhwIjoxNzA3OTU5ODg3LCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiUHVibGljQXV0aG9yaXR5U3RhZmZDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7Im5hbWUiOiJHaXVzZXBwZSBDYXNzYXRhIiwicm9sZSI6IlB1YmxpYyBBdXRob3JpdHkgU3RhZmYiLCJvcmdhbml6YXRpb24iOiJJemlsaWZlIFNvbHV0aW9ucyBTLlIuTC4ifX19.k7bGSm2A4fNsu4YJcTQWRVZfLNPZflbSW6R6XoCAd5JkEKKUtiuWnMz69KXqE3t00D3ZTJQolZVqO07wc6Tq4g"
}
```

Questa stringa compact JWT è la credenziale che il wallet deve conservare, dopo aver verificato la firma tramite la JWKS pubblica dell'issuer.

## Esempio di Credential Offer

```json
{
  "credential_issuer": "https://issuer.izylife.com",
  "credential_configuration_ids": [
    "public-authority-staff-v1"
  ],
  "grants": {
    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
      "pre-authorized_code": "9bff0d2b-64cf-4a53-9d1f-6da4a1b5be15",
      "user_pin_required": false
    }
  }
}
```

Un wallet che scansiona questo JSON (tipicamente codificato in QR) riconosce l’issuer, la configurazione della credenziale disponibile e usa il `pre-authorized_code` per avviare il flusso `/token` e ottenere `access_token` + `c_nonce`.
