<!--
  SSI Issuer Verifier
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

# Izylife SSI Issuer & Verifier Portal

Combined Spring Boot + Angular application that enables Public Authorities (PAs) to issue and verify Self-Sovereign Identity (SSI) credentials within the Izylife ecosystem. The backend hosts REST APIs for credential issuing, tenant onboarding, and presentation verification, while the Angular SPA offers an operator-friendly UI. Maven orchestrates the entire build so a single command produces a runnable JAR containing the compiled frontend.

## Highlights

- Spring Boot 3.2 service with REST, WebSocket, Security (SAML2 SP), and MongoDB integrations.
- Angular 17 SPA bundled via `frontend-maven-plugin` during Maven builds.
- Demo credential issuing and presentation verification flows ready to be wired into a real SSI agent.
- Optional SPID Service Provider configuration for authenticating PAs via the Italian digital identity system.

## Project Layout

```
frontend/               Angular SPA (build output copied into Spring static resources)
src/main/java           Spring Boot controllers, services, and configuration
src/main/resources      `application.yml`, static assets, SPID metadata helpers
samples/                Example configuration artefacts and payloads
Makefile                Convenience tasks (see below)
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 20+ (automatically provisioned by Maven, but useful for standalone Angular work)
- MongoDB 7+ (Docker recipe below)

## Local Development

### Run the backend with hot reload Angular

1. Start the backend:
   ```bash
   mvn spring-boot:run
   ```
2. (Optional) Work on the SPA with the Angular dev server:
   ```bash
   cd frontend
   npm install    # first time only if you bypass the Maven-managed Node
   npm start
   ```
   Configure the Angular proxy to forward API calls to `http://localhost:9090`.

### Build the Angular assets through Maven

```bash
mvn clean package
```

The build performs the following steps automatically:

1. Download Node/npm locally for the project.
2. Run `npm install` inside `frontend/`.
3. Execute `npm run build` to produce the production bundle under `frontend/dist/izylife-ssi-portal`.
4. Copy the generated files into `target/classes/static`.
5. Assemble `target/ssi-issuer-verifier-0.0.1-SNAPSHOT.jar`.

Run the packaged app with:

```bash
java -jar target/ssi-issuer-verifier-0.0.1-SNAPSHOT.jar
```

The portal becomes available at `http://localhost:9090` (configurable via `server.port`).

## Persistence Setup

MongoDB stores tenant registrations and other dynamic data. Launch a local instance with Docker:

```bash
docker run --name ssi-mongo -p 27017:27017 -d mongo:7
```

Override connection details through environment variables:

- `SPRING_DATA_MONGODB_URI` (defaults to `mongodb://localhost:27017/ssi-issuer-verifier`)
- `SPRING_DATA_MONGODB_DATABASE`

## SPID Test IdP Integration

Enable the SPID Service Provider block in `application.yml` (or environment variables) to authenticate operators through the official demo IdP:

```yaml
app:
  spid:
    enabled: true
    registration-id: spid
    entity-id: https://<your-domain>/spid
    assertion-consumer-service: https://<your-domain>/login/saml2/sso/spid
    signing-certificate-location: file:/secure/path/to/sp-signing-cert.pem
    signing-key-location: file:/secure/path/to/sp-signing-key.pem
```

The default metadata URL already points to `https://demo.spid.gov.it/metadata.xml`. Provide a valid signing certificate/key pair through `APP_SPID_SIGNING_CERTIFICATE_LOCATION` and `APP_SPID_SIGNING_KEY_LOCATION`, keep the private key outside the repository, and ensure the ACS URL is reachable from the SPID network.

Example local setup:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out sp-signing-key.pem
openssl req -new -x509 -key sp-signing-key.pem -out sp-signing-cert.pem -days 365 \
  -subj "/CN=local-spid-dev"
export APP_SPID_SIGNING_CERTIFICATE_LOCATION="file:$(pwd)/sp-signing-cert.pem"
export APP_SPID_SIGNING_KEY_LOCATION="file:$(pwd)/sp-signing-key.pem"
```

Export the signed SP metadata required by the SPID validator once the backend is running:

```bash
curl -s http://localhost:9090/spid/metadata > spid-metadata.xml
```

(Adjust the port if you change `server.port`.)

## API Overview

- `GET /api/credentials/templates` – List available credential templates and their claim requirements.
- `POST /api/credentials/issue` – Issue a credential (demo implementation returns a Base64 payload and QR seed).
- `POST /api/verification/presentations` – Verify a presentation challenge against stored demos.
- `GET /api/tenants` / `POST /api/tenants` – Manage registered tenants.

> ⚠️ The shipping services contain demo logic. Replace them with integrations to your actual SSI agent, ledger, or credential registry before production use.

## Makefile Shortcuts

Common tasks are wrapped for convenience:

- `make build` – Run `mvn clean package`.
- `make run` – Launch the Spring Boot app.
- `make lint-frontend` – Execute Angular linting (ensure Node is installed).

Inspect `Makefile` for the full list and customise it to match your workflow.

## Next Steps

- Harden security by enabling OAuth2, mutual TLS, or DID-based access control.
- Replace the demo issuer/verifier services with the real credential lifecycle.
- Add persistence/auditing for issued credentials and verification attempts.
- Integrate observability (metrics, tracing, structured logging) to support production operations.

Use this portal as the authoritative front door for Public Authorities while the other projects in this repo demonstrate how client applications and wallets interact with it.
