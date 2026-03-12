# Keycloak OIDC4VC Admin Prototype

This repository contains a proof-of-concept Keycloak plugin that adds a custom **OIDC4VC** tab to the client configuration screen in the admin console.

## Project layout

- `providers/oidc4vc-keycloak-plugin/` – Maven module packaging the server-side SPI and the admin-console bundle.
- `themes/oidc4vc/` – Admin theme that loads the bundle at runtime.
- `realm-import/oidc4vc-realm.json` – Sample realm that preselects the custom admin theme.
- `Dockerfile` – Multi-stage build that compiles the provider and produces a Keycloak image with the plugin and theme pre-installed.
- `docker-compose.yml` – Development setup for running the customized Keycloak instance.

## Building with Docker

```bash
docker build -t keycloak-oidc4vc .
```

## Running

```bash
docker compose up
```

The container exposes Keycloak on `http://localhost:8080`. Log in with the default admin credentials (`admin` / `admin`) and navigate to any client – the extra **OIDC4VC** tab renders a “Hello world” card courtesy of the plugin’s React bundle.

If you import the bundled realm (`/opt/keycloak/data/import/oidc4vc-realm.json`), the admin console for that realm automatically uses the `oidc4vc` theme so the tab loads without extra configuration.

## Notes

- The development environment used to author this plugin had no internet access, so the first Maven build in a new environment will need outbound network connectivity to download the Keycloak dependencies.
- The admin bundle intentionally keeps the JavaScript minimal. Replace the `MiniReact` block in `providers/oidc4vc-keycloak-plugin/src/main/resources/theme-resources/oidc4vc-admin/js/oidc4vc-client-tab.js` with your own React components as you expand the feature set.
