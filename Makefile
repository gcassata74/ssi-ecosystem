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

.PHONY: run-ssi-demo stop-ssi-demo logs clean
PID_DIR := .pids
ISSUER_DIR := ssi-issuer-verifier
CLIENT_DIR := ssi-client-application  # <- adjust if name differs

run-ssi-demo:
	@mkdir -p $(PID_DIR)
	# Start issuer (background)
	@(cd $(ISSUER_DIR) && mvn -q spring-boot:run) \
		> issuer.out 2> issuer.err & echo $$! > $(PID_DIR)/issuer.pid
	@sleep 5
	# Start client (background)
	@(cd $(CLIENT_DIR) && mvn -q spring-boot:run) \
		> client.out 2> client.err & echo $$! > $(PID_DIR)/client.pid
	@sleep 5
	# Start ngrok in foreground so Ctrl-C stops it; apps keep running
	@echo "Starting ngrok..."
	#@ngrok start --all

stop-ssi-demo:
	-@kill $$(cat $(PID_DIR)/issuer.pid) 2>/dev/null || true
	-@kill $$(cat $(PID_DIR)/client.pid) 2>/dev/null || true
	@rm -f $(PID_DIR)/issuer.pid $(PID_DIR)/client.pid

logs:
	@echo "---- issuer.out ----";  tail -n +1 -f issuer.out &
	@echo "---- client.out ----";  tail -n +1 -f client.out

clean:
	@rm -rf $(PID_DIR) issuer.out issuer.err client.out client.err

