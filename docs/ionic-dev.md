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

## Add QR Scanner button (files needed)

To add a “Start QR Scanner” button in Tab 1, please add these files to the chat so I can patch them:

- mobile-app/package.json
- mobile-app/capacitor.config.ts
- mobile-app/src/global.scss

And the Tab 1 page (your project may use one of these paths):
- mobile-app/src/app/pages/tab1/tab1.page.html
- mobile-app/src/app/pages/tab1/tab1.page.ts
- OR
- mobile-app/src/app/tab1/tab1.page.html
- mobile-app/src/app/tab1/tab1.page.ts

I will wire up the button and scanner logic using @capacitor-community/barcode-scanner and will not run cap sync (per your request). Please also confirm Tab 1 is the correct location for the button.
