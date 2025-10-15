# Makefile

# Define default shell to be used
SHELL := /bin/bash
LOCALE ?= en
PORT ?= 9090
SPID_IDP_ENTITY ?= https://demo.spid.gov.it


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

run-serveo:
	ssh -R izylife:80:localhost:9090 serveo.net

export-spid-artifacts:
	mvn -q -DskipTests -Dexec.classpathScope=test exec:java -Dexec.mainClass=com.izylife.ssi.tools.ExportSpidArtifacts

test-metadata: export-spid-artifacts
	. venv/bin/activate && spid_sp_test --metadata-url "file:///home/gcassata/gitrepos/ssi/ssi-issuer-verifier/build/spid-export/metadata.xml" --profile spid-sp-public -rf json -o /tmp/spid_metadata_report.json -d INFO

test-authnrequest: export-spid-artifacts
	. venv/bin/activate && IDP_ENTITYID=$(SPID_IDP_ENTITY) spid_sp_test \
	  --metadata-url "file:///home/gcassata/gitrepos/ssi/ssi-issuer-verifier/build/spid-export/metadata.xml" \
	  --authn-url "file:///home/gcassata/gitrepos/ssi/ssi-issuer-verifier/build/spid-export/authn-request.xml" \
	  --profile spid-sp-public \
	  -rf json \
	  -o /tmp/spid_full_report.json

verify-spid-response:
	mvn -q -Dexec.classpathScope=test exec:java \
	  -Dexec.mainClass=com.izylife.ssi.tools.VerifySpidResponse \
	  -Dexec.args="$(RESPONSE) $(METADATA)"
