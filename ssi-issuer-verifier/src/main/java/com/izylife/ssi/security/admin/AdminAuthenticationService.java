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
