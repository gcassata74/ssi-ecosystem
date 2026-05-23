/*
 * SSI Keycloak Provider
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.ssi.keycloak.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.RealmModel;

public class AdminUiResource {

    private final RealmModel realm;

    public AdminUiResource(RealmModel realm) {
        this.realm = realm;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getAdminUi() {
        String realmName = realm.getName();
        String baseApi = "/realms/" + realmName + "/ssi-issuer";
        String html = buildHtml(realmName, baseApi);
        return Response.ok(html).build();
    }

    private String buildHtml(String realmName, String baseApi) {
        return "<!DOCTYPE html>\n"
            + "<html lang=\"it\">\n"
            + "<head>\n"
            + "  <meta charset=\"UTF-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
            + "  <title>SSI Config — " + realmName + "</title>\n"
            + "  <style>\n"
            + "    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n"
            + "    body { font-family: system-ui, sans-serif; background: #f3f4f6; color: #111827; padding: 2rem; }\n"
            + "    h1 { font-size: 1.5rem; font-weight: 700; margin-bottom: 0.25rem; }\n"
            + "    .subtitle { color: #6b7280; font-size: 0.875rem; margin-bottom: 2rem; }\n"
            + "    .card { background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,.08); padding: 1.5rem; margin-bottom: 1.5rem; }\n"
            + "    h2 { font-size: 1.1rem; font-weight: 600; margin-bottom: 1rem; }\n"
            + "    .did-box { font-family: monospace; font-size: 0.8rem; background: #f9fafb; border: 1px solid #e5e7eb;\n"
            + "               border-radius: 6px; padding: 0.75rem 1rem; word-break: break-all; margin-bottom: 1rem; color: #1a56db; }\n"
            + "    .did-box.empty { color: #9ca3af; font-style: italic; }\n"
            + "    button { background: #1a56db; color: #fff; border: none; border-radius: 6px; padding: 0.45rem 1.1rem;\n"
            + "             font-size: 0.875rem; font-weight: 500; cursor: pointer; transition: background 0.15s; }\n"
            + "    button:hover:not(:disabled) { background: #3b82f6; }\n"
            + "    button:disabled { opacity: 0.5; cursor: not-allowed; }\n"
            + "    button.danger { background: #e02424; }\n"
            + "    button.danger:hover:not(:disabled) { background: #c81e1e; }\n"
            + "    button.small { padding: 0.25rem 0.65rem; font-size: 0.8rem; }\n"
            + "    .notice { font-size: 0.8rem; color: #d97706; margin-bottom: 0.75rem; }\n"
            + "    table { width: 100%; border-collapse: collapse; margin-bottom: 1rem; }\n"
            + "    th { text-align: left; font-size: 0.75rem; font-weight: 600; text-transform: uppercase;\n"
            + "         letter-spacing: 0.05em; color: #6b7280; padding: 0.5rem 0.5rem; border-bottom: 1px solid #e5e7eb; }\n"
            + "    td { padding: 0.45rem 0.5rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }\n"
            + "    td input[type=text] { width: 100%; border: 1px solid #d1d5db; border-radius: 4px;\n"
            + "                         padding: 0.3rem 0.5rem; font-size: 0.875rem; }\n"
            + "    td input[type=checkbox] { width: 16px; height: 16px; cursor: pointer; }\n"
            + "    .row-actions { display: flex; gap: 0.5rem; align-items: center; }\n"
            + "    .alert { border-radius: 6px; padding: 0.6rem 1rem; font-size: 0.875rem; margin-bottom: 1rem; }\n"
            + "    .alert.success { background: #def7ec; color: #03543f; }\n"
            + "    .alert.error   { background: #fde8e8; color: #9b1c1c; }\n"
            + "    .actions { display: flex; gap: 0.75rem; align-items: center; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <h1>SSI Realm Config</h1>\n"
            + "  <p class=\"subtitle\">Realm: <strong>" + realmName + "</strong></p>\n"
            + "\n"
            + "  <div id=\"alert\" class=\"alert\" style=\"display:none\"></div>\n"
            + "\n"
            + "  <!-- DID section -->\n"
            + "  <div class=\"card\">\n"
            + "    <h2>DID:key del realm</h2>\n"
            + "    <div id=\"did-box\" class=\"did-box empty\">Caricamento…</div>\n"
            + "    <p class=\"notice\">Il DID viene generato automaticamente al primo accesso e rimane stabile. Rigenera solo se necessario.</p>\n"
            + "    <div class=\"actions\">\n"
            + "      <button id=\"btn-regen\" onclick=\"regenerateDid()\" disabled>Rigenera DID:key</button>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "\n"
            + "  <!-- Claim config section -->\n"
            + "  <div class=\"card\">\n"
            + "    <h2>Claim mapping per lo staff credential</h2>\n"
            + "    <table>\n"
            + "      <thead>\n"
            + "        <tr>\n"
            + "          <th>Attributo Keycloak</th>\n"
            + "          <th>Campo credenziale</th>\n"
            + "          <th style=\"width:90px\">Obbligatorio</th>\n"
            + "          <th style=\"width:80px\">Azioni</th>\n"
            + "        </tr>\n"
            + "      </thead>\n"
            + "      <tbody id=\"claims-body\"></tbody>\n"
            + "    </table>\n"
            + "    <div class=\"actions\">\n"
            + "      <button onclick=\"addRow()\">+ Aggiungi riga</button>\n"
            + "      <button id=\"btn-save\" onclick=\"saveConfig()\">Salva</button>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "\n"
            + "  <script>\n"
            + "    const BASE = '" + baseApi + "';\n"
            + "    let token = '';\n"
            + "\n"
            + "    async function init() {\n"
            + "      token = await getAdminToken();\n"
            + "      await Promise.all([loadDid(), loadClaims()]);\n"
            + "    }\n"
            + "\n"
            + "    async function getAdminToken() {\n"
            + "      // Reuse the Keycloak cookie-based session by requesting a fresh token\n"
            + "      const resp = await fetch('/realms/" + realmName + "/protocol/openid-connect/token', {\n"
            + "        method: 'POST', credentials: 'include',\n"
            + "        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n"
            + "        body: 'grant_type=urn:ietf:params:oauth:grant-type:uma-ticket&audience=security-admin-console'\n"
            + "      });\n"
            + "      if (!resp.ok) {\n"
            + "        // Fallback: try to get token via Keycloak JS adapter if available\n"
            + "        return document.cookie.split(';')\n"
            + "          .find(c => c.trim().startsWith('KEYCLOAK_SESSION'))?.split('=')[1] ?? '';\n"
            + "      }\n"
            + "      const data = await resp.json();\n"
            + "      return data.access_token ?? '';\n"
            + "    }\n"
            + "\n"
            + "    async function apiFetch(path, opts = {}) {\n"
            + "      const resp = await fetch(BASE + path, {\n"
            + "        ...opts,\n"
            + "        headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json', ...(opts.headers || {}) }\n"
            + "      });\n"
            + "      if (!resp.ok) throw new Error(await resp.text());\n"
            + "      return resp.status === 204 ? null : resp.json();\n"
            + "    }\n"
            + "\n"
            + "    async function loadDid() {\n"
            + "      try {\n"
            + "        const data = await apiFetch('/did');\n"
            + "        const box = document.getElementById('did-box');\n"
            + "        box.textContent = data.did;\n"
            + "        box.classList.remove('empty');\n"
            + "        document.getElementById('btn-regen').disabled = false;\n"
            + "      } catch (e) { showAlert('Errore caricamento DID: ' + e.message, 'error'); }\n"
            + "    }\n"
            + "\n"
            + "    async function regenerateDid() {\n"
            + "      if (!confirm('Rigenerare il DID:key? Le credenziali già emesse con il vecchio DID non saranno più verificabili con questo realm.')) return;\n"
            + "      try {\n"
            + "        const data = await apiFetch('/did', { method: 'POST' });\n"
            + "        document.getElementById('did-box').textContent = data.did;\n"
            + "        showAlert('DID:key rigenerato con successo.', 'success');\n"
            + "      } catch (e) { showAlert('Errore rigenerazione DID: ' + e.message, 'error'); }\n"
            + "    }\n"
            + "\n"
            + "    async function loadClaims() {\n"
            + "      try {\n"
            + "        const data = await apiFetch('/credential-config');\n"
            + "        const claims = data.claims || data;\n"
            + "        renderRows(claims);\n"
            + "      } catch (e) { showAlert('Errore caricamento claim: ' + e.message, 'error'); }\n"
            + "    }\n"
            + "\n"
            + "    function renderRows(claims) {\n"
            + "      const tbody = document.getElementById('claims-body');\n"
            + "      tbody.innerHTML = '';\n"
            + "      (claims || []).forEach((c, i) => tbody.appendChild(buildRow(c, i)));\n"
            + "    }\n"
            + "\n"
            + "    function buildRow(c, i) {\n"
            + "      const tr = document.createElement('tr');\n"
            + "      tr.dataset.index = i;\n"
            + "      tr.innerHTML = `\n"
            + "        <td><input type=\"text\" value=\"${esc(c.keycloakClaim)}\" placeholder=\"es. given_name\" title=\"Attributo Keycloak\"></td>\n"
            + "        <td><input type=\"text\" value=\"${esc(c.credentialClaim)}\" placeholder=\"es. givenName\" title=\"Campo credenziale\"></td>\n"
            + "        <td style=\"text-align:center\"><input type=\"checkbox\" title=\"Obbligatorio\" ${c.mandatory ? 'checked' : ''}></td>\n"
            + "        <td><div class=\"row-actions\"><button class=\"small danger\" onclick=\"removeRow(this)\">Rimuovi</button></div></td>\n"
            + "      `;\n"
            + "      return tr;\n"
            + "    }\n"
            + "\n"
            + "    function addRow() {\n"
            + "      const tbody = document.getElementById('claims-body');\n"
            + "      const i = tbody.rows.length;\n"
            + "      tbody.appendChild(buildRow({ keycloakClaim: '', credentialClaim: '', mandatory: false }, i));\n"
            + "    }\n"
            + "\n"
            + "    function removeRow(btn) { btn.closest('tr').remove(); }\n"
            + "\n"
            + "    function esc(s) { return (s ?? '').replace(/&/g,'&amp;').replace(/\"/g,'&quot;'); }\n"
            + "\n"
            + "    function collectRows() {\n"
            + "      return [...document.getElementById('claims-body').rows].map(tr => {\n"
            + "        const inputs = tr.querySelectorAll('input');\n"
            + "        return { keycloakClaim: inputs[0].value.trim(), credentialClaim: inputs[1].value.trim(), mandatory: inputs[2].checked };\n"
            + "      }).filter(r => r.keycloakClaim && r.credentialClaim);\n"
            + "    }\n"
            + "\n"
            + "    async function saveConfig() {\n"
            + "      const btn = document.getElementById('btn-save');\n"
            + "      btn.disabled = true; btn.textContent = 'Salvataggio…';\n"
            + "      try {\n"
            + "        await apiFetch('/credential-config', { method: 'PUT', body: JSON.stringify(collectRows()) });\n"
            + "        showAlert('Claim mapping salvato.', 'success');\n"
            + "      } catch (e) { showAlert('Errore salvataggio: ' + e.message, 'error'); }\n"
            + "      finally { btn.disabled = false; btn.textContent = 'Salva'; }\n"
            + "    }\n"
            + "\n"
            + "    function showAlert(msg, type) {\n"
            + "      const el = document.getElementById('alert');\n"
            + "      el.textContent = msg; el.className = 'alert ' + type; el.style.display = 'block';\n"
            + "      setTimeout(() => { el.style.display = 'none'; }, 5000);\n"
            + "    }\n"
            + "\n"
            + "    init();\n"
            + "  </script>\n"
            + "</body>\n"
            + "</html>\n";
    }
}
