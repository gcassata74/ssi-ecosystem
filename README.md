# Izylife SSI Issuer & Verifier Portal

This project delivers a combined Spring Boot and Angular application that allows Public Authorities (PAs) to issue and verify Self-Sovereign Identity (SSI) credentials for the Izylife ecosystem.

The backend exposes REST APIs for credential issuing and presentation verification, while the Angular SPA provides an operator-friendly UI. The Angular build is orchestrated from Maven via the `frontend-maven-plugin`, so a single `mvn package` assembles both artefacts into a runnable Spring Boot executable JAR.

## Project layout

- `src/main/java` – Spring Boot application with REST controllers, DTOs, and demo services.
- `src/main/resources` – Spring configuration and (generated) static assets.
- `frontend/` – Angular 17 SPA that talks to the backend APIs.

## Prerequisites

- Java 17+
- Maven 3.9+

All Node/Angular tooling is downloaded automatically by the Maven build through the frontend plugin. You can also work inside `frontend/` with a locally installed Node 18+/npm if preferred.

## Useful commands

### Run everything in dev mode

1. **Backend**: `mvn spring-boot:run`
   - Runs the Angular build in watch mode? (No) – for live frontend dev start Angular separately (see below).
2. **Frontend (optional live dev)**:
   ```bash
   cd frontend
   npm install
   npm start
   ```
   The dev server proxies API calls to `http://localhost:8080`.

### Build a production JAR (includes Angular)

```bash
mvn clean package
```

The build sequence is:

1. Install Node/npm locally inside `frontend/`.
2. `npm install` (Angular dependencies).
3. `npm run build` (production bundle to `frontend/dist/izylife-ssi-portal`).
4. Copy the generated assets into `target/classes/static` so Spring Boot can serve the SPA.
5. Package the executable JAR `target/ssi-issuer-verifier-0.0.1-SNAPSHOT.jar`.

### Run the packaged app

```bash
java -jar target/ssi-issuer-verifier-0.0.1-SNAPSHOT.jar
```

Open `http://localhost:8080` to access the Izylife issuer/verifier portal.

## API overview

- `GET /api/credentials/templates` – Retrieve available credential templates with required claim fields.
- `POST /api/credentials/issue` – Issue a credential (demo implementation returns a Base64 payload and QR seed).
- `POST /api/verification/presentations` – Verify a presentation challenge (demo validation over encoded payload).

> ⚠️ The current services contain demo logic only; integrate with your actual SSI agent or ledger to replace the placeholders.

## Next steps

- Replace demo issuer/verifier services with real integrations (e.g., to Aries, Trinsic, Lissi, etc.).
- Secure the APIs (OAuth2, mutual TLS, or DID-based auth) according to PA requirements.
- Add persistence/auditing for issued credentials and verification attempts.
