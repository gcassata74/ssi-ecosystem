# Spring Boot + Angular Starter

This workspace is split into two projects:

- `backend/` – Spring Boot 3.5 application that exposes the API layer and
  embeds the `frontend-maven-plugin` so you can bootstrap the Angular client from Maven.
- `frontend/` – Angular 20 application that renders a playful landing page with an intentionally useless login button.

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ (optional because Maven can download a local Node for you)

## Useful Commands

### Run the Angular dev server

From the repository root run (after the first `generate-resources` so Node and dependencies are in place):

```bash
mvn -f backend/pom.xml \
  com.github.eirslett:frontend-maven-plugin:npm \
  -Dfrontend.npm.arguments="run start"
```

The execution uses the locally provisioned Node runtime and starts `ng serve` from the `frontend/` folder. Use `Ctrl+C` to stop it. Combine it with `generate-resources` for a one-liner on a fresh checkout:

```bash
mvn -f backend/pom.xml generate-resources \
  com.github.eirslett:frontend-maven-plugin:npm \
  -Dfrontend.npm.arguments="run start"
```

### Build the Angular assets via Maven

```bash
mvn -f backend/pom.xml generate-resources
```

This installs Node and runs `npm install` inside `frontend/` through the configured plugin.

### Run the Spring Boot backend

```bash
mvn -f backend/pom.xml spring-boot:run
```

## Frontend Preview

The default page centres a single card with a login button that only writes to the browser console when clicked—no authentication wiring involved.
