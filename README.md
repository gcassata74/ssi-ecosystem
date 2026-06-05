<!--
  SSI Ecosystem
  Copyright (c) 2026-present Izylife Solutions s.r.l.
  Author: Giuseppe Cassata

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published
  by the Free Software Foundation, either version 3 of the License,
  or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->

# Izylife SSI Ecosystem

![SSI Ecosystem Architecture](./ssi-ecosystem-architecture.svg)

This repository is a mono-repo for an end-to-end Self-Sovereign Identity demo. It contains a credential issuer portal, a verifier portal, a sample verifier-facing client application, a reusable authentication SDK, a shared common library, and a holder wallet. Together these modules demonstrate credential issuance with OID4VCI, credential presentation with OID4VP, verifier authorization, onboarding orchestration, and optional SPID-based operator login.

## Table of Contents

- [What Is In This Repo](#what-is-in-this-repo)
- [Architecture Overview](#architecture-overview)
- [Keycloak Role In This Demo](#keycloak-role-in-this-demo)
- [Main Runtime Flows](#main-runtime-flows)
- [Module Details](#module-details)
  - [`ssi-issuer`](#ssi-issuer)
  - [`ssi-verifier`](#ssi-verifier)
  - [`ssi-common`](#ssi-common)
  - [`ssi-client-application`](#ssi-client-application)
  - [`ssi-client-lib`](#ssi-client-lib)
  - [`ssi-wallet`](#ssi-wallet)
- [How The Modules Work Together](#how-the-modules-work-together)
- [Build And Run](#build-and-run)
- [Configuration Summary](#configuration-summary)
- [Important Endpoints](#important-endpoints)
- [Project Governance](#project-governance)
- [Development Notes](#development-notes)
- [Troubleshooting](#troubleshooting)

## What Is In This Repo

| Module | Role | Main Tech | Default Port |
| --- | --- | --- | --- |
| `ssi-issuer` | Operator issuer portal. Hosts OID4VCI credential issuance APIs, onboarding state, SPID integration, and the issuer Angular UI. | Spring Boot 3.2, Angular 17, MongoDB | `9090` |
| `ssi-verifier` | Operator verifier portal. Hosts OID4VP verifier APIs, verifier authorization, and the verifier Angular UI. | Spring Boot 3.2, Angular 17, MongoDB | `9091` |
| `ssi-common` | Shared library with common DTOs, utilities, and domain types used by both the issuer and verifier backends. | Java | n/a |
| `ssi-client-application` | Sample verifier-side application that uses the shared SDK to start verifier flows and consume returned tokens. | Spring Boot 3.5, Angular 20 | `9092` |
| `ssi-client-lib` | Reusable TypeScript SDK that wraps auth, PKCE, token handling, redirect recovery, and Angular integration. | TypeScript, tsup | n/a |
| `ssi-wallet` | Holder wallet used to scan QR codes, receive credentials, store them, and submit presentations. | Ionic 8, Angular 20, Capacitor 7 | `8100` in dev |

At the root you also have:

- `Makefile`: convenience commands for starting and stopping the demo.
- `docker-compose.yml`: containerized launcher for issuer, verifier, client, and optional ngrok tunnels.

## Architecture Overview

The issuer and verifier are now separate services. The sample client application and the wallet both depend on the verifier, while the wallet also depends on the issuer:

- the client application depends on the verifier as an authorization server,
- the wallet depends on the issuer as an OID4VCI issuance endpoint,
- the wallet depends on the verifier as an OID4VP verifier endpoint,
- the SDK makes the verifier client integration reusable,
- `ssi-common` provides the shared domain types and utilities consumed by both backend services,
- MongoDB stores dynamic platform data such as tenants and related persisted configuration,
- Keycloak is used as the platform identity service for operator/admin authentication.

```mermaid
flowchart LR
    subgraph Operator Side
        OPI[Issuer Portal UI<br/>Angular]
        OPV[Verifier Portal UI<br/>Angular]
    end

    subgraph Core Platform
        ISS[ssi-issuer<br/>Spring Boot :9090]
        VER[ssi-verifier<br/>Spring Boot :9091]
        COM[ssi-common]
        DB[(MongoDB)]
    end

    subgraph Verifier Side
        CA[ssi-client-application<br/>Angular + Spring Boot :9092]
        SDK[ssi-client-lib<br/>TypeScript SDK]
    end

    subgraph Holder Side
        WAL[ssi-wallet<br/>Ionic/Angular]
    end

    OPI --> ISS
    OPV --> VER
    ISS --> COM
    VER --> COM
    ISS <--> DB
    VER <--> DB
    CA --> SDK
    SDK --> VER
    WAL --> ISS
    WAL --> VER
    CA -. redirect / OAuth-style verifier auth .-> VER
    WAL -. OID4VCI issuance .-> ISS
    WAL -. OID4VP presentation .-> VER
```

## Keycloak Role In This Demo

In this project, Keycloak is the platform authentication service for operator/admin identities.

- Correct naming in standards terms: Keycloak acts as an `Identity Provider (IdP)` and, for OIDC/OAuth2, as an `OpenID Provider (OP)` / `Authorization Server`.
- It is used for back-office authentication and claim/token management consumed by issuer-side admin/enrollment flows.
- It is not the authorization server used by verifier-facing SSI login. That role is implemented by `ssi-verifier` in the OID4VP + OAuth2/PKCE flow for `ssi-client-application`.

## Main Runtime Flows

### 1. Credential Issuance Flow

This is the holder onboarding path. The operator starts from the issuer portal, the wallet scans the generated QR code, and the backend completes an OID4VCI-style issuance exchange.

```mermaid
sequenceDiagram
    autonumber
    participant Operator as Issuer Portal UI
    participant Issuer as ssi-issuer
    participant Wallet as ssi-wallet

    Operator->>Issuer: Request credential offer / onboarding QR
    Issuer-->>Operator: QR code with credential_offer or credential_offer_uri
    Wallet->>Issuer: Resolve credential offer metadata
    Wallet->>Issuer: POST /oidc4vci/token with pre-authorized code
    Issuer-->>Wallet: access_token + c_nonce
    Wallet->>Wallet: Build proof JWT using wallet key material
    Wallet->>Issuer: POST /oidc4vci/credential
    Issuer->>Issuer: Sign credential with issuer signing key
    Issuer-->>Wallet: Verifiable credential
    Wallet->>Wallet: Store credential in secure storage
    Wallet->>Issuer: Notify onboarding credentials received
    Issuer-->>Operator: WebSocket/onboarding update
```

Important details:

- `ssi-issuer` exposes issuer metadata and credential offer endpoints.
- `ssi-wallet` resolves the offer, redeems the pre-authorized code, builds a proof JWT, and stores the resulting credential.
- onboarding is not only a QR rendering problem; it is a state machine managed by the backend and pushed to the issuer UI over WebSockets.

### 2. Verifier Authorization + OID4VP Flow

This is the verifier-facing path. A browser user starts in the sample client app, is redirected to the verifier portal, the wallet submits a presentation, and the client application receives an access token representing the verified holder context.

```mermaid
sequenceDiagram
    autonumber
    participant Browser as Client Browser
    participant Client as ssi-client-application
    participant SDK as ssi-client-lib
    participant Verifier as ssi-verifier
    participant Wallet as ssi-wallet

    Browser->>Client: Click "Go to Verifier"
    Client->>SDK: beginVerifierFlow()
    SDK->>SDK: Generate PKCE verifier + state
    SDK->>Verifier: Redirect browser to verifier portal
    Verifier-->>Browser: Render verifier QR / request flow
    Wallet->>Verifier: Fetch request object / presentation definition
    Wallet->>Wallet: Select matching credentials and build VP token
    Wallet->>Verifier: POST /oidc4vp/responses
    Verifier->>Verifier: Verify VP, nonce, state, descriptor map
    Verifier->>Verifier: Issue short-lived authorization code
    Verifier-->>Browser: Redirect back to client redirect_uri with code
    Browser->>SDK: Return to SPA with code + state
    SDK->>Verifier: POST /oauth2/token with PKCE verifier
    Verifier-->>SDK: access_token / refresh_token
    SDK-->>Client: tokens$
    Client->>Client: Decode credential_preview claims for display
```

Important details:

- the sample client does not directly implement OID4VP; it delegates that concern to the verifier portal plus the shared SDK,
- the SDK keeps PKCE, state, token persistence, refresh timing, and original URL restoration in one place,
- the final verifier-facing token contains preview claims that the Angular sample UI decodes and shows to the user.

### 3. Onboarding State Flow

The issuer portal UI does not just show a static page. It reacts to backend-managed onboarding transitions.

```mermaid
flowchart TD
    A[Start onboarding] --> B[Verifier QR generated]
    B --> C[Wallet scans verifier request]
    C --> D[Presentation accepted]
    D --> E[Issuer QR generated]
    E --> F[Wallet scans credential offer]
    F --> G[Credential issued]
    G --> H[Wallet confirms receipt]
    H --> I[Onboarding complete]

    E --> J[SPID prompt]
    J --> E
```

## Module Details

### `ssi-issuer`

The issuer portal is one of the two main platform services. It implements the credential issuance side of the demo and serves the operator-facing Angular UI.

#### Structure

```
ssi-issuer/
  backend/   — Spring Boot application (JAR packaging)
  frontend/  — Angular operator UI
  Dockerfile
  pom.xml    — Maven aggregator (packaging: pom)
```

#### What The Backend Does

1. OID4VCI issuer
   - exposes discovery metadata,
   - creates credential offers,
   - exchanges grants for access tokens,
   - signs and returns demo credentials.

2. Onboarding orchestrator
   - keeps track of which QR code or prompt should currently be shown,
   - publishes updates over SockJS/STOMP to the Angular UI,
   - coordinates verifier step, issuer step, and wallet acknowledgements.

3. Operator platform
   - serves the Angular SPA,
   - optionally authenticates operators via SPID SAML,
   - supports admin login and administration endpoints.

#### Frontend Responsibilities

| Frontend Area | Purpose |
| --- | --- |
| `onboarding-page` | Main issuer onboarding view. Shows current QR, errors, and step transitions. Rendered at `/`. |
| `issuer-page` | Issuer-side credential offer step. Displays issuer QR or SPID prompt and credential preview details. Rendered at `/issuer`. |
| `services/onboarding.service.ts` | Fetches current onboarding state via `GET /api/onboarding/issuer` and subscribes to backend updates over WebSocket. |

#### Key Backend Classes

| Class | Responsibility |
| --- | --- |
| `Oidc4vciService` | Core issuance state: credential offer records, authorization grants, access tokens, nonce handling, demo profile data. |
| `OnboardingStateService` | Onboarding state machine and WebSocket update emitter. |
| `IssuerSigningService` | Signs credentials with the configured EC key. |
| `SpidSamlConfiguration` | Spring Security SAML2 SP wiring for SPID operator login. |

#### Packaging

```mermaid
flowchart LR
    A[Angular frontend build] --> B[frontend/dist]
    B --> C[Maven resources copy]
    D[Spring Boot classes + resources] --> E[Executable JAR]
    C --> E
```

---

### `ssi-verifier`

The verifier portal is the second main platform service. It implements credential presentation verification and serves as the authorization server for verifier-facing client applications.

#### Structure

```
ssi-verifier/
  backend/   — Spring Boot application (JAR packaging)
  frontend/  — Angular operator UI
  Dockerfile
  pom.xml    — Maven aggregator (packaging: pom)
```

#### What The Backend Does

1. OID4VP verifier
   - generates request objects,
   - publishes verifier JWKS,
   - accepts wallet responses,
   - validates the VP submission payload,
   - turns a successful presentation into an authorization code.

2. Verifier authorization server
   - receives the authorization code returned after wallet presentation,
   - exchanges it at `/oauth2/token`,
   - returns access tokens used by verifier-side clients,
   - supports refresh-token based session continuation through the shared SDK.

#### Frontend Responsibilities

| Frontend Area | Purpose |
| --- | --- |
| `verifier-page` | Shows the verifier QR code for wallet scanning. Rendered at `/`. |
| `services/onboarding.service.ts` | Fetches the current verifier request QR and subscribes to backend updates over WebSocket. |

#### Key Backend Classes

| Class | Responsibility |
| --- | --- |
| `Oidc4VpRequestService` | Builds verifier request objects and tracks authorization sessions. |
| `Oidc4VpResponseController` + `VerificationService` | Accept and validate wallet-submitted VP data, then promote it into verifier auth state. |
| `VerifierAuthorizationService` | Stores short-lived authorization codes issued after a successful presentation. |
| `VerifierTokenService` | Exchanges those codes for access tokens consumed by verifier clients. |
| `PresentationDefinitionRegistry` | Resolves the active presentation definition, including fallback behavior. |

---

### `ssi-common`

A shared Java library consumed by both `ssi-issuer` and `ssi-verifier`. It holds common DTOs, domain types, and utilities that would otherwise be duplicated across the two backend services.

It is declared as a dependency in both backend `pom.xml` files and is built as part of the root Maven reactor.

---

### `ssi-client-application`

This module is the sample verifier integration. It demonstrates how a third-party application would use the shared SDK to delegate SSI-heavy work to the verifier portal.

#### Structure

```
ssi-client-application/
  backend/   — Spring Boot application (JAR packaging)
  frontend/  — Angular 20 SPA
  Dockerfile
  pom.xml    — Maven aggregator (packaging: pom)
```

#### What The Backend Does

Right now the backend is intentionally thin:

- starts a Spring Boot application on port `9092`,
- serves static files copied from the Angular build,
- gives you a place to add verifier-owned APIs later,
- is not where SSI protocol logic lives.

That separation is important: this module demonstrates a consuming application, not a second SSI server.

#### What The Frontend Does

The Angular app is where the useful demo behavior currently lives:

- configures the SDK with the verifier base URL,
- uses the current browser origin as the redirect URI and client identifier,
- sends the browser into the verifier portal via `beginVerifierFlow()`,
- listens to `tokens$` from the Angular auth service,
- decodes the returned JWT access token,
- extracts `credential_preview.subject` claims and renders them to the user.

#### Internal Runtime Flow

```mermaid
flowchart LR
    A[Angular app] --> B[provideSsiAuth]
    B --> C[SsiAuthService]
    C --> D["beginVerifierFlow()"]
    D --> E[Redirect to ssi-verifier]
    E --> F[Return with code]
    F --> G[Token exchange via SDK]
    G --> H[Angular UI decodes credential_preview]
```

---

### `ssi-client-lib`

This is the reusable integration layer. It packages the auth and redirect behavior required by verifier-side frontends so that application teams do not have to reimplement PKCE, token storage, refresh scheduling, or redirect recovery.

#### What It Contains

| File / Area | Purpose |
| --- | --- |
| `src/SsiAuthClient.ts` | Framework-agnostic core client. |
| `src/types.ts` | Shared TypeScript contracts for config, tokens, events, and options. |
| `src/utils.ts` | PKCE, URL building, storage helpers, JWT decoding, and expiration utilities. |
| `src/angular/service.ts` | Angular facade exposing observables and delegation methods. |
| `src/angular/interceptor.ts` | Optional `HttpClient` interceptor that injects bearer tokens. |
| `src/angular/tokens.ts` | Angular DI tokens. |
| `src/angular/index.ts` | `provideSsiAuth()` entry point. |

#### Installation And Consumption

Install from npm:

```bash
npm install '@izylife/ssi-auth-client'
```

For local development inside this repository:

```bash
cd ssi-client-lib
npm install
npm run build
npm pack
```

That produces a tarball which can be referenced by another frontend package. The sample client application demonstrates this model through a local file dependency in `ssi-client-application/frontend/package.json`.

#### Core Responsibilities

1. Session bootstrap — restore stored tokens, inspect the current URL for `code` and `state`, complete redirect callbacks.
2. Authorization flow management — create PKCE verifier/challenge pairs, generate and persist state, build authorization URLs, redirect the browser to the portal.
3. Verifier portal integration — `beginVerifierFlow()` targets the verifier portal and preserves the original browser location for post-redirect restoration.
4. Token lifecycle handling — persist tokens, schedule refresh before expiry, emit lifecycle events.
5. Angular integration — `provideSsiAuth()`, `SsiAuthService`, `SsiAuthInterceptor`.

#### SDK Redirect Handling Flow

```mermaid
sequenceDiagram
    autonumber
    participant App as Consumer App
    participant SDK as SsiAuthClient
    participant Storage as Browser Storage
    participant Verifier as ssi-verifier

    App->>SDK: beginVerifierFlow()
    SDK->>SDK: Generate state + PKCE verifier
    SDK->>Storage: Persist session payload
    SDK->>Verifier: Redirect browser
    Verifier-->>App: Return with code + state
    App->>SDK: init()
    SDK->>Storage: Load persisted session
    SDK->>Verifier: Exchange code for tokens
    Verifier-->>SDK: access_token / refresh_token
    SDK->>Storage: Persist tokens
    SDK-->>App: authenticated state + token stream
```

---

### `ssi-wallet`

This module is the holder side of the demo. It receives credentials and later presents them back to the verifier portal. It is an Ionic/Angular mobile application packaged through Capacitor.

#### What It Contains

| Area | Purpose |
| --- | --- |
| `mobile-app/src/app/tab1` | Main action screen for QR scanning, credential offer acceptance, and VP submission. |
| `mobile-app/src/app/services/oidc4vc.service.ts` | OID4VCI client logic: parse offer URIs, fetch issuer metadata, redeem codes, request credentials. |
| `mobile-app/src/app/services/oidc4vp.service.ts` | OID4VP client logic: parse requests, select credentials, build VP token, post presentation responses. |
| `mobile-app/src/app/services/credential.service.ts` | Secure persistence for verifiable credentials. |
| `mobile-app/src/app/services/did.service.ts` | Creates and stores a `did:key` document derived from wallet key material. |
| `mobile-app/src/app/services/key.service.ts` | Key pair generation and persistence used by issuance proofs and VP signing. |
| `mobile-app/src/app/services/biometric-auth.service.ts` | Local biometric gating support. |

#### Wallet Installation

```bash
cd ssi-wallet/mobile-app
npm install
```

For browser-only development:

```bash
cd ssi-wallet
make serve
```

#### Android Installation And First Run

1. Install dependencies: `cd ssi-wallet/mobile-app && npm install`
2. Add the Android platform the first time: `cd ssi-wallet && make add-android`
3. Build web assets: `make build`
4. Sync into native shell: `make sync`
5. Run on Android: `make run-android`

#### Wallet Processing Flow

```mermaid
flowchart TD
    A[Scan QR] --> B{Payload type}
    B -->|Credential offer| C[OID4VCI flow]
    B -->|Presentation request| D[OID4VP flow]
    C --> E[Fetch metadata]
    E --> F[Redeem token]
    F --> G[Build proof JWT]
    G --> H[Store credential]
    D --> I[Parse request object]
    I --> J[Select matching credentials]
    J --> K[Build VP token]
    K --> L[POST response to ssi-verifier]
```

## How The Modules Work Together

The repo is easiest to understand if you think of the modules by ownership boundary:

- `ssi-issuer` owns credential issuance and operator issuance UI.
- `ssi-verifier` owns credential presentation verification and operator verifier UI.
- `ssi-common` owns shared types consumed by both backend services.
- `ssi-client-lib` is the verifier integration toolkit for consuming applications.
- `ssi-client-application` is the verifier consumer example.
- `ssi-wallet` is the holder example.

Another way to read the same boundary is:

- operator uses `ssi-issuer` and `ssi-verifier` portals,
- verifier integrates `ssi-client-lib`,
- browser-based verifier app is shown by `ssi-client-application`,
- holder uses `ssi-wallet`.

## Build And Run

### Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ for Angular/Ionic work
- npm 10+
- MongoDB 7+ or Docker

### Start MongoDB

```bash
docker run --name ssi-mongo -p 27017:27017 -d mongo:7
```

### Recommended Manual Startup

Start each runtime explicitly the first time so it is clear what is happening.

#### 1. Build the shared common library first

```bash
mvn -pl ssi-common install -DskipTests
```

#### 2. Start the issuer portal

```bash
cd ssi-issuer
mvn -pl backend spring-boot:run
```

This runs the issuer on `http://localhost:9090`. The Maven build also manages the Angular frontend build when packaging.

#### 3. Start the verifier portal

```bash
cd ssi-verifier
mvn -pl backend spring-boot:run
```

This runs the verifier on `http://localhost:9091`.

#### 4. Start the sample client application

```bash
cd ssi-client-application
mvn -pl backend spring-boot:run
```

This runs the sample verifier app on `http://localhost:9092`.

#### 5. Start the wallet in web mode

```bash
cd ssi-wallet/mobile-app
npm install
npm start
```

This runs the wallet dev server on `http://localhost:8100`.

### Root-Level Convenience Commands

The root `Makefile` includes helper targets:

- `make build` — build all modules
- `make run-ssi-demo` — start issuer, verifier, and client in background
- `make stop-ssi-demo` — stop all background services
- `make logs` — tail the output of all background services
- `make clean` — remove PID files and log files

### Docker Compose

The root `docker-compose.yml` builds and starts:

- `ssi-issuer` — exposed at `http://localhost:9090`
- `ssi-verifier` — exposed at `http://localhost:9091`
- `ssi-client` — exposed at `http://localhost:9092`
- `keycloak` — Keycloak 26 in dev mode, exposed at `http://localhost:8180` (admin UI at `/admin`), used as IdP/OP authentication server for operator/admin identities
- `ngrok-issuer` — optional tunnel for public issuer access
- `ngrok-verifier` — optional tunnel for public verifier access

It expects MongoDB to be reachable through `host.docker.internal`. Configure public endpoints and ngrok tokens in a `.env` file at the repo root (see `.env.example`).

## Configuration Summary

### `ssi-issuer`

Main settings live in `ssi-issuer/backend/src/main/resources/application.yml`.

| Setting | Meaning |
| --- | --- |
| `server.port` | HTTP port (default `8080`; mapped to `9090` in Docker). |
| `app.issuer.endpoint` | Public issuer base URL used in metadata and offers. |
| `app.issuer.credential-issuer-id` | OID4VCI issuer identifier. |
| `app.issuer.signing-key.*` | Demo issuer signing JWK used to sign credentials. |
| `spring.data.mongodb.*` | MongoDB connection information. |
| `app.spid.*` | SPID SAML service-provider settings. |

### `ssi-verifier`

Main settings live in `ssi-verifier/backend/src/main/resources/application.yml`.

| Setting | Meaning |
| --- | --- |
| `server.port` | HTTP port (default `8080`; mapped to `9091` in Docker). |
| `app.verifier.endpoint` | Public verifier base URL. |
| `app.verifier.client-id` | Verifier response target used in OID4VP direct-post mode. |
| `app.verifier.signing-key.*` | Demo verifier signing JWK (EC P-256). |
| `app.verifier.presentation-definition-id` | Active presentation definition (e.g. `staff-credential`). |
| `spring.data.mongodb.*` | MongoDB connection information. |

### `ssi-client-application`

The Angular app configures the SDK in `frontend/src/app/app.config.ts`.

Key choices made there:

- `baseUrl` points to the verifier portal,
- `redirectUri` is the current browser origin,
- `clientId` is derived from that same origin,
- `portalPath` is `/verifier`,
- `client_id_scheme=redirect_uri` is passed as a portal parameter.

### `ssi-wallet`

Wallet runtime settings live under `mobile-app/src/environments/`. Most protocol behavior is driven by scanned payloads rather than a large static config object, keeping the wallet flexible during demos.

## Important Endpoints

### Issuer Endpoints (`ssi-issuer`, default `:9090`)

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/.well-known/openid-credential-issuer` | `GET` | OID4VCI issuer metadata |
| `/.well-known/oauth-authorization-server` | `GET` | OAuth/OIDC authorization server metadata |
| `/oidc4vci/credential-offers/{offerId}` | `GET` | Resolve a stored credential offer |
| `/oidc4vci/token` | `POST` | Exchange authorization or pre-authorized codes |
| `/oidc4vci/credential` | `POST` | Issue the actual credential |
| `/oidc4vci/jwks.json` | `GET` | Issuer JWKS |
| `/api/onboarding/issuer` | `GET` | Current issuer onboarding QR and state |
| `/api/onboarding/issuer/credentials-received` | `POST` | Wallet acknowledgement of credential receipt |
| `/api/onboarding/status` | `GET` | Full onboarding status |
| `/spid/metadata` | `GET` | Export SPID SP metadata |

### Verifier Endpoints (`ssi-verifier`, default `:9091`)

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/oidc4vp/requests/{requestId}` | `GET` | Request object for a wallet presentation flow |
| `/oidc4vp/responses` | `POST` | Wallet-submitted VP response |
| `/oidc4vp/jwks.json` | `GET` | Verifier JWKS |
| `/oauth2/token` | `POST` | Exchange verifier auth code for access token |
| `/api/verification/qr` | `GET` | Current verifier QR and request state |

## Project Governance

The repository includes the standard project governance documents at root:

- `LICENSE`
- `CODE_OF_CONDUCT.md`
- `CONTRIBUTING.md`

The repository root is licensed under `AGPL-3.0-only`, except where a subdirectory explicitly ships its own license file.

## Development Notes

### Frontend Development

- `ssi-issuer/frontend` contains the Angular issuer operator UI.
- `ssi-verifier/frontend` contains the Angular verifier operator UI.
- `ssi-client-application/frontend` contains the Angular verifier sample UI.
- `ssi-wallet/mobile-app` contains the Ionic wallet UI.

These frontends are intentionally separate because they represent different actors and trust boundaries.

### Maven Project Structure

Each backend module (issuer, verifier, client) follows the same layout:

```
<module>/
  pom.xml          — aggregator (packaging: pom, lists backend as <module>)
  backend/
    pom.xml        — JAR module with Spring Boot + frontend-maven-plugin
    src/
  frontend/
    package.json
    angular.json
```

The `frontend-maven-plugin` in `backend/pom.xml` uses `workingDirectory: ../frontend` to reach the sibling frontend directory. After the Angular build, the `maven-resources-plugin` copies the dist output into `backend/target/classes/static` so Spring Boot serves it.

### Packaging Strategy

- `ssi-issuer` packages its Angular build into one Spring Boot JAR.
- `ssi-verifier` packages its Angular build into one Spring Boot JAR.
- `ssi-client-application` packages its Angular build into the backend static resources.
- `ssi-client-lib` produces reusable npm bundles instead of a server artifact.
- `ssi-wallet` produces a web build and can be synchronized into native shells via Capacitor.

### Running With Public URLs (ngrok)

The wallet runs on a mobile device and must reach the issuer and verifier over a public URL. The `docker-compose.yml` includes `ngrok-issuer` and `ngrok-verifier` services for this purpose. Set `NGROK_AUTHTOKEN`, `NGROK_ISSUER_DOMAIN`, and `NGROK_VERIFIER_DOMAIN` in your `.env` file, then override `APP_ISSUER_ENDPOINT` and `APP_VERIFIER_ENDPOINT` accordingly.

## Troubleshooting

| Problem | Likely Cause | What To Check |
| --- | --- | --- |
| Wallet cannot obtain a credential | Issuer endpoint mismatch or unreachable public URL | `app.issuer.endpoint`, ngrok domain, issuer metadata |
| Wallet cannot submit presentation | Request object or response URI mismatch | `app.verifier.endpoint`, request URI, `response_mode`, nonce/state |
| Client app never becomes authenticated | Redirect URI or `client_id_scheme` mismatch | Angular app config in `ssi-client-application`, backend logs in `ssi-verifier` |
| Frontend build missing from Spring app | Angular build output not copied into static resources | run `mvn -pl backend generate-resources process-resources` inside the module |
| `global is not defined` in Angular console | `sockjs-client` loaded before global polyfill | check `frontend/src/polyfills.ts` for `(window as any).global = window` |
| White page on verifier or issuer | SpaForwardController wildcard intercepting `/ws/info` | ensure `@GetMapping` uses explicit paths, not a wildcard pattern |
| Mongo connection errors | Database not running or wrong URI | `SPRING_DATA_MONGODB_URI` env variable |
| SPID login problems | SAML metadata or signing cert/key mismatch | `app.spid.*`, metadata export at `/spid/metadata`, DEBUG SAML logs |
| Token refresh not happening | Missing refresh token or refresh timing config | SDK config and `/oauth2/token` behavior in `ssi-verifier` |
| Port conflict on startup | Stale process on 8080/9090/9091 | `lsof -ti:9090 | xargs kill` |

## Related Module READMEs

Each module also has its own README:

- `ssi-issuer/README.md`
- `ssi-verifier/README.md`
- `ssi-client-application/README.md`
- `ssi-client-lib/README.md`
- `ssi-wallet/README.md`

Those files are useful when you are working inside one module. This root README is the system-level view that explains how the modules relate to each other.
