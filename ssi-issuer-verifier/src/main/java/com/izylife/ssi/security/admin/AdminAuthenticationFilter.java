package com.izylife.ssi.security.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private final AdminTokenService adminTokenService;

    public AdminAuthenticationFilter(AdminTokenService adminTokenService) {
        this.adminTokenService = adminTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .flatMap(adminTokenService::parseToken)
                    .ifPresent(principal -> {
                        AbstractAuthenticationToken authentication = new AbstractAuthenticationToken(principal.getAuthorities()) {
                            @Override
                            public Object getCredentials() {
                                return "";
                            }

                            @Override
                            public Object getPrincipal() {
                                return principal;
                            }
                        };
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        authentication.setAuthenticated(true);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AdminTokenService.COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
