<!--
  SSI Client Application
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

# SSI Client Application

A starter workspace that couples a Spring Boot backend with an Angular 20 single-page application. The goal is to demonstrate how a client portal can consume the Izylife SSI issuer/verifier APIs through the shared `@ssi/issuer-auth-client` SDK while keeping backend and frontend codebases decoupled.

## Architecture

- **backend/** – Spring Boot 3.5 service listening on port `9091`. It currently acts as an API façade/stub that can proxy issuer calls or expose your own resources.
- **frontend/** – Angular 20 workspace that consumes the SDK and renders a playful login experience. Static assets are produced under `frontend/dist/frontend/browser`.
- **Maven build** – The parent `pom.xml` wires the `frontend-maven-plugin` so `mvn package` installs Node, builds the Angular app, and copies the artefacts into `backend/src/main/resources/static` for a single runnable JAR.

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ (only required for direct Angular development—the Maven build can provision a local runtime)
- npm 10+

## Project Layout

```
backend/   Spring Boot application, configuration, and Maven build logic
frontend/  Angular workspace using @ssi/issuer-auth-client from the sibling library
pom.xml    Parent POM that aggregates the backend and frontend modules
```

## Initial Setup

Install dependencies once per clone:

```bash
mvn -f backend/pom.xml generate-resources  # installs Node, npm deps, and builds the Angular app
```

This command bootstraps both modules so the backend can serve the static SPA.

## Day-to-Day Development

### Run the Spring Boot backend

```bash
mvn -f backend/pom.xml spring-boot:run
```

The service starts on `http://localhost:9091`. Update controller classes under `backend/src/main/java` as you add APIs.

### Iterate on the Angular frontend

Option 1: reuse the Maven-managed Node runtime and run the dev server from Maven:

```bash
mvn -f backend/pom.xml \
  com.github.eirslett:frontend-maven-plugin:npm \
  -Dfrontend.npm.arguments="run start"
```

Option 2: work directly inside `frontend/` with your global Node installation:

```bash
cd frontend
npm install    # first time only if you skip the Maven bootstrap
npm start
```

The Angular dev server listens on `http://localhost:4200` and proxies API calls as you configure them inside `proxy.conf.json` (create one if needed).

### Use the built frontend with the backend

Run both commands sequentially when you want the backend to serve the compiled SPA:

```bash
mvn -f backend/pom.xml generate-resources
mvn -f backend/pom.xml spring-boot:run
```

## Building for Release

```bash
mvn clean package
```

The resulting executable JAR (`backend/target/backend-0.0.1-SNAPSHOT.jar`) contains the Angular assets under `BOOT-INF/classes/static`. Deploy it like any Spring Boot application.

## Testing & Linting

- **Backend:** add tests under `backend/src/test/java` and run `mvn -f backend/pom.xml test`.
- **Frontend:** execute `npm test` or `npm run lint` inside `frontend/` once you've added specs or ESLint rules.

## Configuration

- The default port (`9091`) is set in `backend/src/main/resources/application.yml`.
- Inject issuer/verifier API URLs, OAuth2 client IDs, and other integration settings via `application.yml`, environment variables, or a dedicated configuration service when you wire the real backend.
- The Angular app consumes the `@ssi/issuer-auth-client` tarball from `../../ssi-client-lib/ssi-issuer-auth-client-0.1.3.tgz`. Update the dependency when you publish newer builds of the SDK.

## Troubleshooting

- **Node commands fail from Maven:** rerun `mvn -f backend/pom.xml generate-resources` to re-provision the local Node runtime under `backend/target`.
- **Angular dev server cannot reach the backend:** add an Angular proxy configuration or enable CORS on the backend while iterating locally.
- **Static assets missing in the JAR:** ensure the Angular build output folder matches the `maven-resources-plugin` configuration (`frontend/dist/frontend/browser`).

Use this workspace as a sandbox—replace the stub login view and augment the backend controllers to match your SSI-backed experiences.
