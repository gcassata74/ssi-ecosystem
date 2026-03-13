<!--
  SSI Wallet
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

## Android WebView debugging (web-native UI)

1. Enable USB debugging on the Android device/emulator and connect it with `adb`.
2. Start the app with live reload so the embedded WebView loads local code:
   ```bash
   make run-android-live HOST_IP=<your_dev_machine_ip>
   ```
   The Ionic CLI must be installed (`npm install -g @ionic/cli`). Set `HOST_IP` to an address reachable by the device (the Makefile auto-detects one for most setups).
3. Find the active WebView devtools socket:
   ```bash
   adb shell cat /proc/net/unix | grep webview_devtools_remote
   ```
   Copy the socket name that corresponds to the Ionic app process.
4. Forward the socket to a local port (defaults to 9222):
   ```bash
   make devtools-forward DEVTOOLS_SOCKET=<socket_from_step_3> [DEVTOOLS_PORT=9222]
   ```
5. Launch Chrome/Chromium with remote debugging enabled:
   ```bash
   make chrome-dev [DEVTOOLS_PORT=9222]
   ```
   Set `CHROME_BIN` if the browser is not auto-detected.
6. Open `http://localhost:9222` (or `chrome://inspect`) to access the WebView devtools.

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

## Next step to add the button on Tab 1

Please add these files to the chat so I can patch them directly:
- mobile-app/src/app/pages/tab1/tab1.page.html
- mobile-app/src/app/pages/tab1/tab1.page.ts

What I will insert:
- In tab1.page.html (inside ion-content), add:
```html
<ion-button expand="block" color="primary" (click)="startQrScan()">
  Start QR Scanner
</ion-button>
```

- In tab1.page.ts, add a placeholder handler (safe stub until plugins are installed):
```ts
startQrScan() {
  // TODO: integrate @capacitor-community/barcode-scanner
  console.log('Start QR Scanner clicked');
}
```
