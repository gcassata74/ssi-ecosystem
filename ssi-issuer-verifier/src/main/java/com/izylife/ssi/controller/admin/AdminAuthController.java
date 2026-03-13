/*
 * SSI Issuer Verifier
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

package com.izylife.ssi.controller.admin;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.admin.AdminLoginRequest;
import com.izylife.ssi.dto.admin.AdminUserResponse;
import com.izylife.ssi.security.admin.AdminAuthenticationService;
import com.izylife.ssi.security.admin.AdminPrincipal;
import com.izylife.ssi.security.admin.AdminTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping(path = "/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthenticationService authenticationService;
    private final AdminTokenService tokenService;
    private final AppProperties.AdminProperties adminProperties;

    public AdminAuthController(AdminAuthenticationService authenticationService,
                               AdminTokenService tokenService,
                               AppProperties properties) {
        this.authenticationService = authenticationService;
        this.tokenService = tokenService;
        this.adminProperties = properties.getAdmin();
    }

    @PostMapping("/login")
    public ResponseEntity<AdminUserResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        if (!authenticationService.authenticate(request.username(), request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = tokenService.createToken(request.username());
        addCookie(httpResponse, token, resolveSecureFlag(httpRequest), tokenService.tokenTtl().getSeconds());
        return ResponseEntity.ok(new AdminUserResponse(request.username(), adminProperties.getRealm()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        addCookie(httpResponse, "", resolveSecureFlag(httpRequest), 0L);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AdminUserResponse> currentUser(Principal principal) {
        AdminPrincipal adminPrincipal = extractPrincipal(principal);
        if (adminPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new AdminUserResponse(adminPrincipal.getUsername(), adminProperties.getRealm()));
    }

    private void addCookie(HttpServletResponse response, String value, boolean secure, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(AdminTokenService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean resolveSecureFlag(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return StringUtils.hasText(forwardedProto) && forwardedProto.equalsIgnoreCase("https");
    }

    private AdminPrincipal extractPrincipal(Principal principal) {
        if (principal instanceof AdminPrincipal adminPrincipal) {
            return adminPrincipal;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal adminPrincipal) {
            return adminPrincipal;
        }
        return null;
    }
}
