.PHONY: help serve cap-add-android cap-add-ios cap-sync cap-open-android cap-open-ios

help:
	@echo "Targets:"
	@echo "  serve             - Run ionic serve inside mobile-app"
	@echo "  cap-add-android   - Add Android platform (run inside mobile-app)"
	@echo "  cap-add-ios       - Add iOS platform (run inside mobile-app)"
	@echo "  cap-sync          - Sync web and native projects"
	@echo "  cap-open-android  - Open Android project in Android Studio"
	@echo "  cap-open-ios      - Open iOS project in Xcode (macOS only)"

serve:
	@cd mobile-app && ionic serve

cap-add-android:
	@cd mobile-app && npx cap add android

cap-add-ios:
	@cd mobile-app && npx cap add ios

cap-sync:
	@cd mobile-app && npx cap sync

cap-open-android:
	@cd mobile-app && npx cap open android

cap-open-ios:
	@cd mobile-app && npx cap open ios
