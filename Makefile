.PHONY: help serve build cap-add-android cap-add-ios cap-sync cap-run-android cap-run-android-live cap-open-android cap-open-ios adb-forward-devtools

HOST_IP ?= $(shell ip route get 1.1.1.1 2>/dev/null | awk '{print $$7; exit}')
HOST_IP ?= 127.0.0.1
DEVTOOLS_PORT ?= 9222
DEVTOOLS_SOCKET ?=

help:
	@echo "Targets:"
	@echo "  serve             - Run ionic serve inside mobile-app"
	@echo "  cap-add-android   - Add Android platform (run inside mobile-app)"
	@echo "  cap-add-ios       - Add iOS platform (run inside mobile-app)"
	@echo "  cap-sync          - Sync web and native projects"
	@echo "  cap-run-android   - Run Android build without live reload"
	@echo "  cap-run-android-live - Run Android build with live reload on HOST_IP=$(HOST_IP)"
	@echo "  cap-open-android  - Open Android project in Android Studio"
	@echo "  cap-open-ios      - Open iOS project in Xcode (macOS only)"
	@echo "  adb-forward-devtools - Forward Android WebView devtools to localhost:$(DEVTOOLS_PORT)"

serve:
	@cd mobile-app && ionic serve

build:
	@cd mobile-app && ionic build --configuration development

cap-add-android:
	@cd mobile-app && npx cap add android

cap-add-ios:
	@cd mobile-app && npx cap add ios

cap-sync:
	@cd mobile-app && npx cap sync

cap-run-android:
	@cd mobile-app && npx cap run android

cap-open-android:
	@cd mobile-app && npx cap open android

cap-open-ios:
	@cd mobile-app && npx cap open ios

run-chrome-dev:
	google-chrome \
      --remote-debugging-port=9222 \
      --disable-web-security \
      --no-sandbox \
      --user-data-dir="/tmp/ChromeDevSession" \
      --no-first-run \
      --no-default-browser-check