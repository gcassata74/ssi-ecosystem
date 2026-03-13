# SSI Issuer Verifier
# Copyright (c) 2026-present Izylife Solutions s.r.l.
# Author: Giuseppe Cassata
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License,
# or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

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
	APP_SPID_AUTHN_REQUEST_OUTPUT=/tmp/spid-authn-request.xml \
	APP_SPID_ENABLED=true \
	APP_VERIFIER_ENDPOINT=https://izylife-issuer.eu.ngrok.io \
	mvn spring-boot:run

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

