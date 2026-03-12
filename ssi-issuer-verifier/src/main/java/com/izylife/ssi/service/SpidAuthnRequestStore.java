package com.izylife.ssi.service;

import com.izylife.ssi.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SpidAuthnRequestStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpidAuthnRequestStore.class);

    public record StoredAuthnRequest(String xml, Instant capturedAt) {}

    private final AtomicReference<StoredAuthnRequest> latest = new AtomicReference<>();
    private final AppProperties appProperties;

    public SpidAuthnRequestStore(AppProperties appProperties) {
        this.appProperties = appProperties;
        loadExistingFromDisk();
    }

    public void update(String xml) {
        if (xml == null || xml.isBlank()) {
            return;
        }

        StoredAuthnRequest stored = new StoredAuthnRequest(xml, Instant.now());
        latest.set(stored);
        persistToDisk(stored);
    }

    public Optional<StoredAuthnRequest> latest() {
        return Optional.ofNullable(latest.get());
    }

    private void persistToDisk(StoredAuthnRequest stored) {
        AppProperties.SpidProperties spid = appProperties.getSpid();
        if (spid == null) {
            return;
        }
        String destination = spid.getAuthnRequestOutput();
        if (destination == null || destination.isBlank()) {
            return;
        }

        Path path = Path.of(destination);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, stored.xml(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.warn("Unable to write SPID AuthnRequest to {}", path, ex);
        }
    }

    private void loadExistingFromDisk() {
        AppProperties.SpidProperties spid = appProperties.getSpid();
        if (spid == null) {
            return;
        }
        String destination = spid.getAuthnRequestOutput();
        if (destination == null || destination.isBlank()) {
            return;
        }

        Path path = Path.of(destination);
        if (!Files.exists(path)) {
            return;
        }
        try {
            String xml = Files.readString(path, StandardCharsets.UTF_8);
            if (xml != null && !xml.isBlank()) {
                latest.set(new StoredAuthnRequest(xml, Instant.now()));
            }
        } catch (IOException ex) {
            LOGGER.warn("Unable to read existing SPID AuthnRequest from {}", path, ex);
        }
    }
}
