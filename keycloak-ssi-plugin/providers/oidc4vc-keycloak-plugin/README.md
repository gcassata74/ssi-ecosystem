# OIDC4VC Keycloak Admin Extension

This module packages a Keycloak server provider together with a lightweight React bundle that injects an `OIDC4VC` tab into the client configuration screen of the Keycloak admin console.

The implementation is intentionally minimal:

- A `ThemeResourceProvider` exposes the static JavaScript bundle under `/resources/oidc4vc-admin-ui/…`
- A companion admin theme (`themes/oidc4vc`) loads the bundle during console startup
- A tiny React helper renders a “Hello world” card inside the injected tab

> **Note:** Because the execution environment has no internet access the dependencies are not cached locally. The first `mvn package` will need network access to fetch the Keycloak artifacts.

## Building

```bash
mvn -f providers/oidc4vc-keycloak-plugin/pom.xml clean package
```

## Installing into Keycloak

1. Copy `providers/oidc4vc-keycloak-plugin/target/oidc4vc-keycloak-plugin-0.1.0-SNAPSHOT.jar` into `${KEYCLOAK_HOME}/providers/`
2. Copy the `themes/oidc4vc` directory into `${KEYCLOAK_HOME}/themes/`
3. Enable the theme, either via `keycloak.conf`:

   ```
   # keycloak.conf
   admin-console-theme=oidc4vc
   ```

   or through the UI in *Realm Settings → Themes → Admin Theme*.

4. Rebuild/start Keycloak: `kc.sh build && kc.sh start-dev`

After logging into the admin console, open any client. A new “OIDC4VC” tab should appear and display the React-based “Hello world” card.

## Updating the React markup

The bundle lives in `src/main/resources/theme-resources/oidc4vc-admin/js/oidc4vc-client-tab.js`. Replace the `MiniReact` tree with your own logic to expand the tab.
