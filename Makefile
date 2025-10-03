# Makefile

# Define default shell to be used
SHELL := /bin/bash
LOCALE ?= en

# e.g. make run-i18n-build LOCALE=fr
run-i18n-build:
	ng build --configuration=$(LOCALE)

# e.g. make run-i18n-serve LOCALE=fr
run-i18n-serve:
	ng serve --configuration=$(LOCALE)

run-i18n-extract:
	cd frontend && ng extract-i18n --output-path src/locale --format xlf

run-chrome-dev:
	google-chrome \
      --new-window "http://127.0.0.1:4200" \
      --remote-debugging-port=9222 \
      --disable-web-security \
      --no-sandbox \
      --user-data-dir="/tmp/ChromeDevSession" \
      --no-first-run \
      --no-default-browser-check


run-angular-client:
	cd frontend && npx kill-port 4200 || true && npm start

run-spring-boot-server:
	cd backend && MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" mvn spring-boot:run

run-ngrok:
	ngrok config add-authtoken 33SSQOjcmhp8GLdSgYIFtT2U8su_3vd1F3EUh61ffkEEwMUC && \
	ngrok http 9090