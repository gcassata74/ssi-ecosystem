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
