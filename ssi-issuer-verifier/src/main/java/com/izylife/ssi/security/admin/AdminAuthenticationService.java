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

package com.izylife.ssi.security.admin;

import com.izylife.ssi.config.AppProperties;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminAuthenticationService {

    private final AppProperties.AdminProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthenticationService(AppProperties properties) {
        this.properties = properties.getAdmin();
        this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    public boolean authenticate(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return false;
        }
        if (properties == null) {
            return false;
        }
        if (!username.equals(properties.getUsername())) {
            return false;
        }
        String expectedPassword = properties.getPassword();
        if (!StringUtils.hasText(expectedPassword)) {
            return false;
        }
        try {
            return passwordEncoder.matches(password, expectedPassword);
        } catch (IllegalArgumentException ex) {
            return expectedPassword.equals(password);
        }
    }

    public AppProperties.AdminProperties properties() {
        return properties;
    }
}
