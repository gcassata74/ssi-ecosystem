package com.izylife.ssi.tools;

import com.izylife.ssi.config.AppProperties;
import org.junit.jupiter.api.Assumptions;

public final class SpidTestCredentials {

    private static final String CERTIFICATE_PROPERTY = "app.spid.signing-certificate-location";
    private static final String KEY_PROPERTY = "app.spid.signing-key-location";
    private static final String CERTIFICATE_ENV = "APP_SPID_SIGNING_CERTIFICATE_LOCATION";
    private static final String KEY_ENV = "APP_SPID_SIGNING_KEY_LOCATION";

    private SpidTestCredentials() {
    }

    public static void assumeConfigured(AppProperties.SpidProperties spid) {
        String certificateLocation = resolve(CERTIFICATE_PROPERTY, CERTIFICATE_ENV);
        String keyLocation = resolve(KEY_PROPERTY, KEY_ENV);
        Assumptions.assumeTrue(isPresent(certificateLocation) && isPresent(keyLocation), message());
        apply(spid, certificateLocation, keyLocation);
    }

    public static void requireConfigured(AppProperties.SpidProperties spid) {
        String certificateLocation = resolve(CERTIFICATE_PROPERTY, CERTIFICATE_ENV);
        String keyLocation = resolve(KEY_PROPERTY, KEY_ENV);
        if (!isPresent(certificateLocation) || !isPresent(keyLocation)) {
            throw new IllegalStateException(message());
        }
        apply(spid, certificateLocation, keyLocation);
    }

    private static void apply(AppProperties.SpidProperties spid, String certificateLocation, String keyLocation) {
        spid.setSigningCertificateLocation(certificateLocation);
        spid.setSigningKeyLocation(keyLocation);
    }

    private static String resolve(String propertyName, String envName) {
        String value = System.getProperty(propertyName);
        if (isPresent(value)) {
            return value.trim();
        }
        value = System.getenv(envName);
        if (isPresent(value)) {
            return value.trim();
        }
        return null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String message() {
        return "Configure SPID test credentials with " + CERTIFICATE_ENV + " and " + KEY_ENV
                + " (or -D" + CERTIFICATE_PROPERTY + " / -D" + KEY_PROPERTY + ")";
    }
}
