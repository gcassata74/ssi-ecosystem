package com.izylife.ssi.spid;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@Service
public class SpidIntegrationService {

    private static final String PROVIDERS_RESOURCE = "spid/idplist.properties";
    private static final String IDP_KEYS = "spid.spring.integration.idp.keys";
    private static final String IDP_PREFIX = "spid.spring.integration.idp.";

    public List<SpidProvider> loadProviders() {
        Properties properties = new Properties();
        try (InputStream inputStream = new ClassPathResource(PROVIDERS_RESOURCE).getInputStream()) {
            properties.load(inputStream);
        } catch (IOException ex) {
            return Collections.emptyList();
        }

        String rawKeys = properties.getProperty(IDP_KEYS);
        if (rawKeys == null || rawKeys.isBlank()) {
            return Collections.emptyList();
        }

        String[] keys = rawKeys.split(",");
        List<SpidProvider> providers = new ArrayList<>(keys.length);
        for (String key : keys) {
            String trimmedKey = key.trim();
            if (trimmedKey.isEmpty()) {
                continue;
            }
            providers.add(toProvider(properties, trimmedKey));
        }
        return providers;
    }

    private SpidProvider toProvider(Properties properties, String key) {
        String propertyBase = IDP_PREFIX + key + ".";
        String name = properties.getProperty(propertyBase + "name", key);
        String entityId = properties.getProperty(propertyBase + "entityId", "");
        String imageUrl = properties.getProperty(propertyBase + "imageUrl", "");
        return new SpidProvider(key, entityId, name, imageUrl);
    }
}
