# SSI Wallet

Ionic/Angular mobile wallet that acts as the relying party for the Izylife SSI ecosystem. The app is built with Capacitor so you can run it as a web app, Android APK, or iOS build while sharing the same Angular core. This directory also contains companion documentation for issuer integration and QR-based onboarding flows.

## Structure

```
mobile-app/   Ionic + Angular workspace (Tabs starter, Capacitor project)
docs/         Developer notes, issuer sample code, and debugging guides
Makefile      Shortcuts for common CLI tasks (ionic, capacitor, android tooling)
```

## Prerequisites

- Node.js 18+ and npm 10+
- Ionic CLI (`npm install -g @ionic/cli`)
- Capacitor CLI (installed locally via `npm install`)
- Android Studio + SDKs for Android builds (set `ANDROID_HOME` and add `platform-tools` to `PATH`)
- macOS + Xcode for iOS builds (not available on Linux containers)

## Install Dependencies

```bash
cd mobile-app
npm install
```

This pulls Angular 20, Ionic 8, Capacitor 7, biometric plugins, and other runtime dependencies defined in `package.json`.

## Development Workflow

The top-level `Makefile` wraps common Ionic/Capacitor commands so you can stay in the repository root:

- `make serve` – Run `ionic serve` on `http://localhost:8100` with live reload.
- `make build` – Produce a development build (outputs to `www/`).
- `make add-android` / `make add-ios` – Generate native platform projects.
- `make sync` – Copy the web build into the native shells (`npx cap sync`).
- `make run-android` – Build and launch the Android app without live reload.

Additional targets (`run-android-live`, `open-android`, `open-ios`, `devtools-forward`, `chrome-dev`) are listed as placeholders—extend the Makefile with the commands shown in `docs/ionic-dev.md` when you need them.

You can continue using the Ionic CLI directly from `mobile-app/` if you prefer—the Makefile simply provides shortcuts.

## Native Platform Notes

- Always execute Capacitor commands from inside `mobile-app/` so the local CLI (`node_modules/@capacitor/cli`) is picked up. Running the commands elsewhere leads to errors such as “could not determine executable to run”.
- After modifying the Angular code run `make sync` (or `npx cap sync`) to push the latest assets into the native project before rebuilding with Android Studio/Xcode.
- For Android WebView inspection, follow the detailed steps in `docs/ionic-dev.md` to forward the devtools socket and launch Chrome with remote debugging.

## Testing & Linting

Angular and Ionic provide the standard scripts:

```bash
npm test   # Karma + Jasmine unit tests
npm run lint
```

End-to-end or Capacitor plugin tests can be added later as the wallet matures.

## Additional Documentation

- `docs/ionic-dev.md` – Troubleshooting the Ionic CLI, adding platforms, and WebView debugging.
- `docs/spring-issuer-credential.md` – Spring Boot walkthrough for generating `jwt_vc_json` credentials served to the wallet.

## Roadmap Ideas

- Integrate the `@ssi/issuer-auth-client` SDK for wallet-side authentication flows.
- Add QR scanning (see `docs/ionic-dev.md` for a proposed implementation plan).
- Implement secure credential storage using `capacitor-secure-storage-plugin` and biometric prompts.
- Build UX around credential offers, presentation requests, and push notifications.

Treat this wallet as a reference shell—it is intentionally minimal so new SSI features can be added iteratively.
