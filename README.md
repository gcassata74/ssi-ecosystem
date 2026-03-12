# Izylife SSI Demo Platform

A mono-repo that bundles every moving part of the Izylife Self-Sovereign Identity (SSI) demonstrator: the Spring Boot issuer/verifier portal, a sample verifier-facing client application, the reusable TypeScript authentication SDK, and an Ionic wallet for holders. Use this workspace to explore how issuance (OIDC4VCI), presentation (OIDC4VP), onboarding, and OAuth-style verifier authorization come together end-to-end.

## Contents

- [High-Level View](#high-level-view)
- [System Architecture](#system-architecture)
- [End-to-End Flows](#end-to-end-flows)
- [Projects](#projects)
  - [ssi-issuer-verifier](#ssi-issuer-verifier)
  - [ssi-client-application](#ssi-client-application)
  - [ssi-client-lib](#ssi-client-lib)
  - [ssi-wallet](#ssi-wallet)
- [Running the Demo Stack](#running-the-demo-stack)
- [Development Workflows](#development-workflows)
- [Configuration Reference](#configuration-reference)
- [Key REST & OIDC Endpoints](#key-rest--oidc-endpoints)
- [Testing & Quality Gates](#testing--quality-gates)
- [Troubleshooting Cheatsheet](#troubleshooting-cheatsheet)
- [Additional Resources](#additional-resources)

## High-Level View

| Component | Purpose | Tech | Default Port |
| --- | --- | --- | --- |
| `ssi-issuer-verifier` | Issuer & verifier operator portal, OIDC4VCI/OIDC4VP APIs, tenancy, SPID integration | Spring Boot 3.2, Angular 17, MongoDB | 9090 |
| `ssi-client-application` | Sample relying-party client that consumes the verifier portal through the shared SDK | Spring Boot 3.5, Angular 20 | 9091 |
| `ssi-client-lib` | `@ssi/issuer-auth-client` TypeScript SDK (core + Angular helpers) | TypeScript, tsup | _n/a_ |
| `ssi-wallet` | Ionic/Angular wallet for holders (web, Android, iOS via Capacitor) | Angular 20, Capacitor 7 | 8100 (Ionic dev server) |

Top-level automation (`Makefile`, log files, ngrok placeholders) live beside the projects.

## System Architecture

```
                          +----------------------+
                          |  MongoDB (demo)      |
                          |  Tenant & state data |
                          +----------+-----------+
                                     ^
                                     |
                 +-------------------+-------------------+
                 | Spring Boot @ 9090 (ssi-issuer-verifier)|
                 | - OIDC4VCI issuer                      |
                 | - OIDC4VP verifier                     |
                 | - REST APIs & WebSocket updates        |
                 +----------+-----------------------------+
                            ^
            OIDC4VP redirect|                       Credential offers
                            |
   +------------------------+------------------------+
   |   Angular SPA (issuer-verifier frontend)        |
   |   QR onboarding, credential issuance,           |
   |   verification dashboards                       |
   +------------------------+------------------------+
                            |
         OAuth2 / SDK       |                        OIDC4VCI
                            v
+----------------+      +------------------+      +-------------------+
| Browser / UI   |      | ssi-client-lib   |      | Ionic Wallet      |
| (Angular 20)   |----->| @ssi/issuer-     |<-----| (ssi-wallet)      |
| Client App     |      | auth-client SDK  |      | OIDC4VCI grant    |
| (9091)         |      | (PKCE, tokens,   |      | OIDC4VP response  |
|                |      | verifier portal) |      | generation        |
+----------------+      +------------------+      +-------------------+
```

Key ideas:

- The issuer/verifier portal is the authoritative backend. It exposes REST endpoints for credential templates, issuing demo credentials, verifying presentations, registering tenants, and handling both halves of the OIDC4VCI/OIDC4VP specifications.
- The reusable SDK (`ssi-client-lib`) wraps those OAuth2-style interactions (authorization code, PKCE, token refresh, SPA navigation helpers) so any front-end can piggy-back on the same flows.
- The sample client application demonstrates how verifiers embed the SDK to kick off the verification portal, obtain bearer tokens carrying credential previews, and display the resulting claims.
- The Ionic wallet is a relying-party agent that consumes the credential offers, performs the grant, and sends credential presentations back to the verifier portal.

## End-to-End Flows

### Credential Issuance (OIDC4VCI)

1. An operator in `ssi-issuer-verifier` creates or reuses a credential offer (`/oidc4vci/credential-offers/{id}`) and surfaces it as a QR code.
2. The wallet scans the QR, retrieves the offer JSON, and follows the grant indicated (`authorization_code` or `pre-authorized_code`).
3. Token exchange happens against `/oidc4vci/token`. The portal returns `access_token`, `token_type`, `expires_in`, and a `c_nonce` for proof binding.
4. The wallet requests a verifiable credential via `/oidc4vci/credential` by posting the proof JWT signed with the wallet binding key. The demo service returns a `jwt_vc_json` payload signed with the issuer key configured in `application.yml`.
5. The wallet persists the credential and acknowledges receipt via `/api/onboarding/issuer/credentials-received` to advance the onboarding carousel shown to the operator.

### Verification (OIDC4VP + Verifier OAuth)

1. A verifier using the client application triggers `SsiAuthService.beginVerifierFlow()` which navigates the user to the issuer/verifier portal (customizable via `portalPath`).
2. The portal generates an OIDC4VP request object (`/oidc4vp/requests/{requestId}`) and QR payload (`app.verifier.qr-payload`). The wallet scans it and submits the presentation to `/oidc4vp/responses`.
3. The portal validates nonce, definition ID, descriptor maps, and delegates proof checks to `VerificationService`. On success it issues an authorization code stored by `VerifierAuthorizationService`.
4. The browser is redirected back to the client application, which exchanges the authorization code at `/oauth2/token`. The resulting access token contains `credential_preview` claims that the sample UI decodes to show DID, holder attributes, and raw JWT payload.
5. Token refresh and logout are handled by the SDK (`SsiAuthClient`) so verifiers can maintain sessions or trigger federated sign-out.

### Tenant Onboarding & SPID (optional)

- `/api/onboarding/*` exposes the onboarding state machine used to orchestrate QR rotation between verifier login, issuer credentialing, and wallet acknowledgements. WebSocket updates (STOMP over SockJS) push changes to the Angular portal UI.
- When `app.spid.enabled=true`, Spring Security acts as an Italian SPID Service Provider. Operators authenticate via SAML, and the portal exports metadata at `/spid/metadata`. Supporting utilities in `src/test/java/com/izylife/ssi/tools/` help debug SPID responses.

## Projects

### ssi-issuer-verifier

- **Stack:** Spring Boot 3.2, Maven, Angular 17, MongoDB, SockJS/STOMP for live updates.
- **Features:**
  - Implements `.well-known/openid-credential-issuer` and `.well-known/oauth-authorization-server`.
  - Endpoints for credential templates (`/api/credentials/templates`), issuance (`/api/credentials/issue`), verification (`/api/verification/presentations`), tenant CRUD (`/api/tenants`), onboarding (`/api/onboarding/*`), and SPID SAML metadata/login.
  - OIDC4VCI token, authorization, offer, and credential pipelines backed by `Oidc4vciService`.
  - OIDC4VP request/response handling with JWT signing (`/oidc4vp/requests/{id}`, `/oidc4vp/responses`, `/oidc4vp/jwks.json`).
  - Configurable demo signing keys for issuer and verifier (see `app.issuer.signing-key` and `app.verifier.signing-key` in `src/main/resources/application.yml`).
- **Build:** `mvn clean package` compiles the Angular SPA, copies `frontend/dist` into `target/classes/static`, and produces `target/ssi-issuer-verifier-0.0.1-SNAPSHOT.jar`.
- **Run:** `mvn spring-boot:run` (port `9090`). Requires MongoDB (default URI `mongodb://localhost:27017/ssi-issuer-verifier`). Use the included Docker snippet to launch a local instance.
- **Frontend:** Angular workspace under `frontend/` (proxied dev server via `npm start`). Real-time onboarding progress uses the `/ws` SockJS endpoint exposed by `WebSocketConfig`.

### ssi-client-application

- **Stack:** Spring Boot 3.5 backend (stub façade) + Angular 20 SPA wired through a parent Maven build.
- **Purpose:** Demonstrates verifier-side consumption of the issuer portal through the shared SDK.
- **Frontend behaviour:** 
  - Bootstraps `SsiAuthService` from `@ssi/issuer-auth-client/angular`.
  - The primary CTA (`Go to Verifier`) calls `beginVerifierFlow()`, taking the user to the portal for credential presentation.
  - After redirect, tokens streamed from `tokens$` update the UI with holder DID, credential subject claims, and raw JWT payload.
- **Build & run:** `mvn -f backend/pom.xml spring-boot:run` (port `9091`). `mvn -f backend/pom.xml generate-resources` installs Node locally and produces the production Angular bundle.
- **Dev mode:** `npm start` inside `frontend/` for live reload; configure an Angular proxy if you reach the issuer portal directly.

### ssi-client-lib

- **Package:** `@ssi/issuer-auth-client` (core SDK + Angular helpers).
- **Highlights:**
  - Authorization Code + PKCE initiation (`login`), token storage, refresh scheduling, logout helpers.
  - Verifier portal helper (`beginVerifierFlow`) that preserves state, PKCE verifier, and original URL to resume SPA navigation.
  - Storage abstraction so consumers can plug custom persistence (defaults to `localStorage`/`sessionStorage`).
  - Event emitters for authentication lifecycle (`authenticated`, `token_refreshed`, `token_expired`, `logout`, `error`).
  - Angular providers (`provideSsiAuth`), services (`SsiAuthService`), and an HTTP interceptor for effortless integration.
- **Build:** `npm install && npm run build` (bundles emitted under `dist/` as ESM+CJS with type declarations). The Angular package is published beside the core bundles.
- **Local consumption:** `npm pack` produces `ssi-issuer-auth-client-*.tgz`. The client application references `../../ssi-client-lib/ssi-issuer-auth-client-0.1.3.tgz`.

### ssi-wallet

- **Stack:** Ionic 8 + Angular 20 wrapped with Capacitor 7 for native builds.
- **Structure:** `mobile-app/` (Angular/Ionic source) and `docs/` (issuer integration guides, Ionic troubleshooting, Spring Boot credential issuance walkthrough using Nimbus JOSE).
- **Dev workflow:** 
  - `make serve` (alias for `ionic serve`) on `http://localhost:8100`.
  - `make add-android`, `make sync`, `make run-android` to manage native shells.
  - Inside `mobile-app/`: `npm test`, `npm run lint`, and `npx cap sync`.
- **Role in the ecosystem:** Acts as the credential holder. Scans pre-authorized QR codes, performs the `/oidc4vci/token` exchange, submits proofs to `/oidc4vci/credential`, and later answers OIDC4VP requests from the verifier portal.
- **Docs to read first:** `docs/ionic-dev.md` for development ergonomics and `docs/spring-issuer-credential.md` for credential issuance logic.

## Running the Demo Stack

1. **Prerequisites**
   - Java 17+
   - Maven 3.9+
   - Node.js 18+ (Node 20+ for the issuer portal frontend)
   - npm 10+
   - Docker (optional but recommended for MongoDB)
2. **Start MongoDB**
   ```bash
   docker run --name ssi-mongo -p 27017:27017 -d mongo:7
   ```
3. **Bootstrap Angular assets (first time)**
   ```bash
    # issuer portal
   (cd ssi-issuer-verifier && mvn generate-resources)

    # client application
   (cd ssi-client-application && mvn -f backend/pom.xml generate-resources)
   ```
4. **Run everything via Makefile**
   ```bash
   make run-ssi-demo
   ```
   - Starts the issuer portal on `9090` and the client application on `9091` in the background, streaming logs to `issuer.out` / `issuer.err` and `client.out` / `client.err`.
   - The `ngrok` step is left commented—uncomment when you need public URLs.
5. **Interact**
   - Visit `http://localhost:9090` for the issuer/verifier portal UI.
   - Visit `http://localhost:9091` for the verifier client SPA.
   - Use the wallet (web or mobile) to scan the QR code presented by the portal.
6. **Observe & stop**
   ```bash
   make logs           # tail both app logs
   make stop-ssi-demo  # terminate background JVMs
   make clean          # remove PID + log files
   ```

Manual start alternative:

```bash
(cd ssi-issuer-verifier && mvn spring-boot:run)
(cd ssi-client-application && mvn -f backend/pom.xml spring-boot:run)
```

## Development Workflows

- **Hot reload Angular UIs:** `npm start` in `ssi-issuer-verifier/frontend` or `ssi-client-application/frontend`. Configure `proxy.conf.json` to avoid CORS headaches when hitting the backend directly.
- **SDK hacking:** Work inside `ssi-client-lib`, run `npm run build -- --watch` (or point `npm link`) and update `ssi-client-application/frontend/package.json` to reference the locally built tarball.
- **Wallet native builds:** Run `make add-android` once, then `make sync` before rebuilding in Android Studio. Remember that Capacitor commands must execute from `mobile-app/`.
- **SPID testing:** Adjust `app.spid.*` properties in `ssi-issuer-verifier/src/main/resources/application.yml`. Export metadata via `curl http://localhost:9090/spid/metadata > spid.xml`.
- **WebSocket debugging:** Subscribe to `/topic/onboarding` using the Angular portal or external STOMP clients to observe onboarding state transitions.

## Configuration Reference

| Setting | Location | Notes |
| --- | --- | --- |
| `server.port=9090` | `ssi-issuer-verifier/src/main/resources/application.yml` | Change issuer portal port. |
| `server.port=9091` | `ssi-client-application/backend/src/main/resources/application.yml` | Change verifier client port. |
| `SPRING_DATA_MONGODB_URI` | Environment variable | Overrides MongoDB connection string for the issuer portal. |
| `app.issuer.endpoint` | `application.yml` | Public issuer base URL (ngrok-ready). |
| `app.issuer.signing-key` | `application.yml` | Demo EC P-256 signing JWK for credentials. Replace in production. |
| `app.verifier.qr-payload` | `application.yml` | QR content for OIDC4VP requests (customize audience + challenge). |
| `app.spid.*` | `application.yml` | Toggle SPID Service Provider behaviour. |
| `app.keycloak.*` | `application.yml` | Enable Keycloak admin integration (roles + presentation definitions). |
| `@ssi/issuer-auth-client` config | `ssi-client-application/frontend/src/app/app.config.ts` | Defines base URL, client ID, scopes, redirect URIs for the SDK. |
| `portalPath` / `portalParams` | SDK configuration | Control where `beginVerifierFlow()` navigates verifiers (defaults to `/`). |

The SDK accepts additional overrides:

- `endpoints.authorization`, `endpoints.token`, `endpoints.endSession` to align with custom deployments.
- `storageKey` to avoid collisions when multiple applications reuse the same browser.
- `refreshSkewMs` to tune the automatic refresh window (default 60 seconds).

### Keycloak-managed presentation definitions

The issuer portal can now source the OIDC4VP presentation definition (`credentials.json`) directly from a Keycloak deployment (default port `9080`). This lets customers curate which credentials a wallet must present without rebuilding the Spring service.

1. **Prepare Keycloak** – run `docker-compose -f docker/keycloak/docker-compose.yml up -d` (administrator default `admin/admin`). Create or select the realm referenced by `APP_KEYCLOAK_REALM` (default `ssi`).
2. **Service account for the portal** – create a confidential client (default ID `ssi-issuer-verifier`) with *Service Accounts Enabled* and copy the client secret. Grant it `view-users` and `view-clients` realm roles so it can resolve users and read client attributes.
3. **Wallet verifier client** – create a client matching `APP_KEYCLOAK_PD_CLIENT_ID` (default `wallet-verifier`). In the *Attributes* tab add `credentials.json` with the full OIDC4VP presentation definition JSON you want wallets to satisfy. The attribute name can be customised via `APP_KEYCLOAK_PD_ATTRIBUTE`.
4. **Wire the portal** – set `APP_KEYCLOAK_ENABLED=true` and `APP_KEYCLOAK_PD_ENABLED=true`, plus `APP_KEYCLOAK_BASE_URL`, `APP_KEYCLOAK_CLIENT_ID`, `APP_KEYCLOAK_CLIENT_SECRET`, and optional cache tuning (`APP_KEYCLOAK_PD_CACHE_TTL`).

When the feature is active the portal fetches and caches the JSON definition from Keycloak. Updates in Keycloak propagate automatically after the configured cache TTL. If the integration is disabled or the attribute is missing, the portal falls back to the built-in `staff-credential.json` file.

## Key REST & OIDC Endpoints

| Endpoint | Method | Description |
| --- | --- | --- |
| `/.well-known/openid-credential-issuer` | GET | OIDC4VCI issuer metadata |
| `/.well-known/oauth-authorization-server` | GET | Authorization server metadata |
| `/oidc4vci/credential-offers/{offerId}` | GET | Retrieve credential offer JSON |
| `/oidc4vci/token` | POST (form) | Exchange authorization or pre-authorized codes |
| `/oidc4vci/credential` | POST (JSON) | Issue demo credential (`jwt_vc_json`) |
| `/oidc4vci/jwks.json` | GET | Issuer JWK set used to verify signed responses |
| `/oidc4vp/requests/{requestId}` | GET | Signed OIDC4VP request object (JWT) |
| `/oidc4vp/responses` | POST (form) | Process wallet presentations, mint authorization code |
| `/oidc4vp/jwks.json` | GET | Verifier JWKS for wallets validating request objects |
| `/oauth2/token` | POST (form) | Exchange verifier authorization code for access token |
| `/api/credentials/templates` | GET | List credential templates exposed by the portal |
| `/api/credentials/issue` | POST | Issue mock credential + QR seed for demos |
| `/api/verification/presentations` | POST | Verify credential presentations programmatically |
| `/api/onboarding/*` | GET/POST | Manage onboarding QR rotation and acknowledgments |
| `/api/tenants` | GET/POST | Simple tenant registry backed by MongoDB |
| `/spid/metadata` | GET | Export SPID Service Provider metadata |

The sample verifier client consumes `/oauth2/token` through the SDK; other APIs are surfaced by the Angular portal.

## Testing & Quality Gates

- **Issuer portal backend:** `mvn test` (unit + integration scaffolding) and `mvn verify` for the full build.
- **Issuer portal frontend:** `npm test`, `npm run lint`, `npm run build`.
- **Client application backend:** `mvn -f backend/pom.xml test`.
- **Client application frontend:** `npm test`, `npm run lint` inside `frontend/`.
- **SDK:** `npm run lint` and `npm run build` (`tsup` checks types). Add unit tests under `src/__tests__/` and run with `npm test` when configured.
- **Wallet:** `npm test`, `npm run lint`. End-to-end/device tests can be executed via Capacitor once configured.

CI/CD pipelines can stitch these commands together; each sub-project is self-contained so you can run only the modules you touch.

## Troubleshooting Cheatsheet

- **Mongo connection refused:** ensure the Docker container is running or point `SPRING_DATA_MONGODB_URI` at a live instance.
- **Angular CLI errors during Maven build:** rerun the corresponding `mvn generate-resources` to reinstall the local Node runtime managed by `frontend-maven-plugin`.
- **Wallet cannot complete credential grant:** confirm the issuer base URL (`app.issuer.endpoint`) matches the publicly reachable ngrok domain and that the signing keys align with published JWKS.
- **Verifier flow stuck after QR scan:** check `issuer.out` for OIDC4VP nonce/state mismatches and confirm the SDK `redirectUri` matches the one registered in the portal.
- **SPID integration failures:** enable DEBUG logging (`org.springframework.security.saml2=DEBUG`, `org.opensaml=DEBUG`) and capture the SAML response using `src/test/java/com/izylife/ssi/tools/VerifySpidResponse`.
- **Token refresh not happening in the client SPA:** verify `refreshTokens=true` and that refresh tokens are issued by `/oauth2/token`. The SDK logs `token_expired` events when it cannot refresh.

## Additional Resources

- `ssi-issuer-verifier/README.md`, `ssi-client-application/README.md`, `ssi-client-lib/README.md`, `ssi-wallet/README.md` — detailed module-level docs.
- `brochure-ssi-izylife.pdf` — high-level presentation you can share with stakeholders.
- `ssi-wallet/docs/spring-issuer-credential.md` — in-depth tutorial for implementing the `/credential` endpoint with Nimbus JOSE.
- `ssi-wallet/docs/ionic-dev.md` — step-by-step Ionic debugging and QR scanning guidance.

For questions or new feature ideas, open issues directly in this repository so discussions stay tied to the relevant component.
