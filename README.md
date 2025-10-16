# @ssi/issuer-auth-client

A lightweight TypeScript client that streamlines authentication against the SSI Issuer/Verifier authorization server. It provides a framework-agnostic core SDK plus Angular-specific helpers inspired by the ergonomics of `keycloak-angular`.

- 🔐 Handles OAuth2/OIDC authorization-code flow with PKCE.
- 🔄 Manages token persistence, refresh, and broadcast of authentication events.
- 🅰️ Ships with Angular providers, services, and an HTTP interceptor for easy drop-in usage from `main.ts`.
- 📦 Ready to publish on npm with generated type declarations and dual ESM/CJS bundles.

## Installation

```bash
npm install @ssi/issuer-auth-client
```

Peer dependencies (installed automatically by Angular CLI) must be available in the host application:

- `@angular/core` and `@angular/common` (optional, only required for Angular integration).
- `rxjs` (optional, only required when using the Angular service/interceptor).

## Quick Start (framework-agnostic)

```ts
import { SsiAuthClient } from '@ssi/issuer-auth-client';

const client = new SsiAuthClient({
  baseUrl: 'https://issuer.example.com',
  clientId: 'ssi-portal',
  redirectUri: `${window.location.origin}/auth/callback`,
  scopes: ['openid', 'profile', 'ssi:presentations']
});

await client.init({ onLoad: 'check-sso' });

if (!client.isAuthenticated()) {
  await client.login();
}

const token = await client.getAccessToken();
```

## Angular Integration

1. **Bootstrap with environment providers** (Angular v16+):

```ts
// main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { AppComponent } from './app/app.component';
import { provideSsiAuth } from '@ssi/issuer-auth-client/angular';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(withInterceptorsFromDi()),
    provideSsiAuth({
      config: {
        baseUrl: 'https://issuer.example.com',
        clientId: 'ssi-portal-ui',
        redirectUri: `${window.location.origin}/auth/callback`,
        scopes: ['openid', 'profile', 'ssi:presentations'],
        refreshSkewMs: 60_000
      },
      initOptions: {
        onLoad: 'check-sso',
        restoreOriginalUri: true
      },
      includeHttpInterceptor: true
    })
  ]
});
```

2. **Consume the service anywhere**:

```ts
import { Component } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { SsiAuthService } from '@ssi/issuer-auth-client/angular';

@Component({
  selector: 'app-auth-status',
  standalone: true,
  imports: [AsyncPipe],
  template: `
    <ng-container *ngIf="authStatus$ | async as status">
      Status: {{ status }}
    </ng-container>
  `
})
export class AuthStatusComponent {
  readonly authStatus$ = this.auth.authStatus$;

  constructor(private readonly auth: SsiAuthService) {}

  login() {
    this.auth.login();
  }

  logout() {
    this.auth.logout();
  }
}
```

The optional HTTP interceptor automatically annotates outgoing `HttpClient` requests with a `Bearer` token and triggers a silent refresh when the current token is within the configurable `refreshSkewMs` window. Override the header name by setting `interceptorHeader` inside `provideSsiAuth` options.

## API Highlights

### `SsiAuthClient`

- `init(options?: SsiInitOptions)` – restores sessions, handles redirect callbacks, and optionally enforces `login-required` flow.
- `login(options?: LoginOptions)` – starts the authorization-code flow (returns the authorization URL).
- `logout(options?: LogoutOptions)` – clears state and triggers the end-session endpoint.
- `getAccessToken()` / `getIdToken()` – fetches the latest tokens, refreshing when configured.
- `fetchWithAuth(input, init?)` – wraps `fetch` with automatic `Authorization` headers.
- `loadUserInfo()` – convenience helper for the UserInfo endpoint.
- `on(event, listener)` – subscribe to lifecycle events: `authenticated`, `token_refreshed`, `token_expired`, `logout`, `error`.

### Angular helpers

- `provideSsiAuth(options)` – registers the client, initializer, service, and (optionally) the HTTP interceptor.
- `SsiAuthService`
  - `authStatus$` / `tokens$` – observables of the authentication state.
  - `initialize()` – invoked automatically during bootstrap via `APP_INITIALIZER`.
  - `login()`, `logout()`, `getAccessToken()`, `fetchWithAuth()` – thin wrappers over the core client.
- `SsiAuthInterceptor` – adds tokens to `HttpClient` requests when `includeHttpInterceptor` is set.

## Configuration Reference

| Option | Description |
| ------ | ----------- |
| `baseUrl` | Base URL of the authorization server (e.g., `https://issuer.example.com`). |
| `clientId` | OAuth2 client identifier registered with the issuer/verifier portal. |
| `redirectUri` | Callback URL configured in the authorization server. |
| `postLogoutRedirectUri` | Optional post-logout redirect target. Defaults to `redirectUri`. |
| `scopes` | Additional scopes requested during login. Defaults to `['openid', 'profile', 'email']`. |
| `audience` | Custom audience parameter when supported by the server. |
| `endpoints` | Override default endpoint paths (`authorization`, `token`, `userInfo`, `endSession`). |
| `usePkce` | Enable/disable PKCE (defaults to `true`). |
| `refreshTokens` | Attempt refresh-token grants when available (defaults to `true`). |
| `refreshSkewMs` | Milliseconds before expiry to trigger an automatic refresh (defaults to `60000`). |

## Development

```bash
npm install
npm run lint
npm run build
```

The build pipeline produces CommonJS + ESM bundles alongside type declarations inside the `dist/` directory. Before publishing to npm, bump the version and run `npm publish --access public` (or the workflow that fits your release process).

## License

MIT
