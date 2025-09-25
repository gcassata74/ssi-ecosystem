# Ionic/Capacitor Dev Notes

If you see “npm error could not determine executable to run” for commands like `npx cap add android`, it usually means you’re running them outside the Ionic project directory (where `node_modules` contains `@capacitor/cli`).

## Run Ionic and Capacitor commands inside the app directory

1) Change into the Ionic app directory created by `ionic start`:
```bash
cd mobile-app
```

2) Serve the app:
```bash
ionic serve
```

3) Add native platforms:
```bash
npx cap add android
npx cap add ios
```

4) Sync web code to native:
```bash
npx cap sync
```

5) Open native IDEs:
```bash
npx cap open android
npx cap open ios
```

## Verify Capacitor CLI is installed locally

From inside `mobile-app`:
```bash
npx cap -v
```
If this fails, install it:
```bash
npm i -D -E @capacitor/cli@latest
```

## Common prerequisites

- Android: Install Android Studio + SDKs, set ANDROID_HOME and add platform-tools to PATH.
- iOS: macOS with Xcode (building iOS cannot be done on Linux).
- Keep Node/npm updated. For npm:
```bash
npm install -g npm@latest
```
