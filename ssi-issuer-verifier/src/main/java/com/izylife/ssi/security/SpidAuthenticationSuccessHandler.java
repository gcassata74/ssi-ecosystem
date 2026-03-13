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

package com.izylife.ssi.security;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.service.Oidc4vciService;
import com.izylife.ssi.service.OnboardingStateService;
import com.izylife.ssi.service.SpidAttributeMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Optional;

public class SpidAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OnboardingStateService onboardingStateService;
    private final SpidAttributeMapper attributeMapper;
    private final AppProperties appProperties;

    public SpidAuthenticationSuccessHandler(OnboardingStateService onboardingStateService,
                                            SpidAttributeMapper attributeMapper,
                                            AppProperties appProperties) {
        this.onboardingStateService = onboardingStateService;
        this.attributeMapper = attributeMapper;
        this.appProperties = appProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Oidc4vciService.StaffProfile profile = attributeMapper.map(authentication);
        onboardingStateService.completeIssuerEnrollmentWithSpid(profile);

        String redirectTarget = Optional.ofNullable(appProperties.getSpid())
                .map(AppProperties.SpidProperties::getPostLoginRedirect)
                .filter(path -> path != null && !path.isBlank())
                .orElse("/issuer");

        response.sendRedirect(redirectTarget);
    }
}
