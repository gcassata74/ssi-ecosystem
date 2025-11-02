# @ssi/issuer-auth-client

A lightweight TypeScript SDK that streamlines authentication against the Izylife SSI issuer/verifier authorization server. The package exposes a framework-agnostic core client and optional Angular helpers that mirror the ergonomics of `keycloak-angular`.

## Features

- 🔐 Handles OAuth2/OIDC authorization-code flows with PKCE.
- 🔄 Manages token storage, refresh, and lifecycle events.
- 🧩 Provides a `fetchWithAuth` helper for standards-based API calls.
- 🅰️ Ships Angular providers, services, and an HTTP interceptor for quick drop-in integration.
- 📦 Builds dual ESM/CJS bundles with type declarations—ready for npm publishing.

## Package Structure

```
dist/index.js        # Core framework-agnostic client (ESM)
dist/index.cjs       # Core client (CommonJS)
dist/angular/*       # Angular service, providers, and HTTP interceptor wrappers
```

The public exports are declared in `package.json` so bundlers can tree-shake unused helpers.

## Installation

```bash
npm install '@izylife/ssi-auth-client'
```

Peer dependencies are optional unless you use the Angular integration:

- `@angular/core` and `@angular/common`
- `rxjs`

## Quick Start (Core SDK)

```ts
import { SsiAuthClient } from '@izylife/ssi-auth-client';

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

Use `client.fetchWithAuth()` for convenience when calling issuer/verifier APIs.

## Angular Integration

Register the SDK with Angular environment providers (Angular 16+):

```ts
// main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { AppComponent } from './app/app.component';
import { provideSsiAuth } from '@izylife/ssi-auth-client/angular';

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

Inject the service anywhere in your component tree:

```ts
import { Component } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { SsiAuthService } from '@izylife/ssi-auth-client/angular';

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

Enabling the optional HTTP interceptor annotates outgoing `HttpClient` requests with a `Bearer` token and triggers silent refreshes when the token is close to expiring.

## API Highlights

### `SsiAuthClient`

- `init(options?: SsiInitOptions)` – Restores sessions, handles redirect callbacks, and supports `login-required` flows.
- `login(options?: LoginOptions)` / `logout(options?: LogoutOptions)` – Start or end the authorization session.
- `getAccessToken()` / `getIdToken()` – Retrieve the latest tokens with automatic refresh when enabled.
- `fetchWithAuth(input, init?)` – Lightweight wrapper around `fetch` that injects the `Authorization` header.
- `loadUserInfo()` – Convenience helper for the UserInfo endpoint.
- `on(event, listener)` – Subscribe to lifecycle events: `authenticated`, `token_refreshed`, `token_expired`, `logout`, `error`.

### Angular Helpers

- `provideSsiAuth(options)` – Registers the core client, initializer, service, and optional interceptor.
- `SsiAuthService` – Surface observables (`authStatus$`, `tokens$`) and delegation methods (`login`, `logout`, `getAccessToken`, `fetchWithAuth`).
- `SsiAuthInterceptor` – Adds tokens to `HttpClient` calls when `includeHttpInterceptor` is enabled.

## Development

Install dependencies and run the quality gates before publishing:

```bash
npm install
npm run lint
npm run build
```

The `build` script invokes `tsup` to generate CommonJS and ES Module bundles plus type declarations under `dist/`.

To test the package locally from another project, either use `npm link` or point to the generated tarball (`npm pack`) from your consumer's `package.json`.

## Publishing

1. Update the version in `package.json` following semver.
2. Run `npm run build` and validate the artefacts under `dist/`.
3. Publish to npm (or your private registry):

   ```bash
   npm publish --access public
   ```

4. Tag the release in Git and notify downstream projects (e.g., `ssi-client-application/frontend`) to bump the dependency.

## Troubleshooting

- **Token refresh not happening:** ensure `refreshTokens: true` and `refreshSkewMs` are configured. Check developer tools for blocked third-party cookies if you rely on hidden iframes.
- **Angular injector errors:** verify that you call `provideSsiAuth()` only once during bootstrap and that Angular peers (`@angular/core`, `@angular/common`, `rxjs`) satisfy the peer dependency ranges.

Feel free to extend the SDK with additional framework bindings or expose more granular events as new requirements emerge.
