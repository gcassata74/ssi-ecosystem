# SSI Ecosystem
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

.PHONY: build run-ssi-demo stop-ssi-demo logs clean
PID_DIR := .pids
CLIENT_DIR := ssi-client-application

build:
	mvn -B install -DskipTests

run-ssi-demo:
	@mkdir -p $(PID_DIR)
	# Start issuer (background)
	@(cd ssi-issuer && mvn -q -pl backend spring-boot:run) \
		> issuer.out 2> issuer.err & echo $$! > $(PID_DIR)/issuer.pid
	@sleep 5
	# Start verifier (background)
	@(cd ssi-verifier && mvn -q -pl backend spring-boot:run) \
		> verifier.out 2> verifier.err & echo $$! > $(PID_DIR)/verifier.pid
	@sleep 5
	# Start client (background) — spring-boot:run must target the backend module
	@(cd $(CLIENT_DIR) && mvn -q -pl backend spring-boot:run) \
		> client.out 2> client.err & echo $$! > $(PID_DIR)/client.pid
	@echo "All services started. Run 'make logs' to follow output."

stop-ssi-demo:
	-@kill $$(cat $(PID_DIR)/issuer.pid) 2>/dev/null || true
	-@kill $$(cat $(PID_DIR)/verifier.pid) 2>/dev/null || true
	-@kill $$(cat $(PID_DIR)/client.pid) 2>/dev/null || true
	@rm -f $(PID_DIR)/issuer.pid $(PID_DIR)/verifier.pid $(PID_DIR)/client.pid

logs:
	@echo "---- issuer.out ----";  tail -n +1 -f issuer.out &
	@echo "---- verifier.out ----"; tail -n +1 -f verifier.out &
	@echo "---- client.out ----";  tail -n +1 -f client.out

clean:
	@rm -rf $(PID_DIR) issuer.out issuer.err verifier.out verifier.err client.out client.err

