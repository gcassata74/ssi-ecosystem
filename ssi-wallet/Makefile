.PHONY: help serve build add-android add-ios sync run-android run-android-live open-android open-ios devtools-forward chrome-dev

HOST_IP ?= $(shell ip route get 1.1.1.1 2>/dev/null | awk '{print $$7; exit}')
HOST_IP ?= 127.0.0.1
DEVTOOLS_PORT ?= 9222
DEVTOOLS_SOCKET ?=

help:
	@echo "Targets:"
	@echo "  serve              - Run 'ionic serve' inside mobile-app (local dev server)"
	@echo "  build              - Build Angular/Ionic app (dev config)"
	@echo "  add-android        - Add Android platform to Capacitor project"
	@echo "  add-ios            - Add iOS platform to Capacitor project"
	@echo "  sync               - Sync web build with native projects"
	@echo "  run-android        - Run Android app (embedded build, no live reload)"
	@echo "  run-android-live   - Run Android app with live reload via HOST_IP=$(HOST_IP)"
	@echo "  open-android       - Open Android project in Android Studio"
	@echo "  open-ios           - Open iOS project in Xcode (macOS only)"
	@echo "  devtools-forward   - Forward Android WebView devtools to localhost:$(DEVTOOLS_PORT)"
	@echo "  chrome-dev         - Launch Chrome with remote debugging enabled"

serve:
	@cd mobile-app &&  ionic serve --host=localhost --port=8100 --no-open --configuration development

build:
	@cd mobile-app && ionic build --configuration development

add-android:
	@cd mobile-app && npx cap add android

add-ios:
	@cd mobile-app && npx cap add ios

sync:
	@cd mobile-app && npx cap sync

run-android:
	@cd mobile-app && npx cap run android
