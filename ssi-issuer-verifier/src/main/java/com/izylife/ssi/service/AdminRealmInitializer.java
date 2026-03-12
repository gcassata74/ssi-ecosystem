package com.izylife.ssi.service;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.model.Tenant;
import com.izylife.ssi.repository.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class AdminRealmInitializer implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final AppProperties.AdminProperties adminProperties;

    public AdminRealmInitializer(TenantRepository tenantRepository, AppProperties properties) {
        this.tenantRepository = tenantRepository;
        this.adminProperties = properties.getAdmin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminProperties == null || !StringUtils.hasText(adminProperties.getRealm())) {
            return;
        }

        tenantRepository.findByNameIgnoreCase(adminProperties.getRealm())
                .ifPresentOrElse(this::ensureMetadata, this::createDefaultRealm);
    }

    private void ensureMetadata(Tenant tenant) {
        boolean updated = false;
        if (!StringUtils.hasText(tenant.getContactEmail()) && StringUtils.hasText(adminProperties.getDefaultContactEmail())) {
            tenant.setContactEmail(adminProperties.getDefaultContactEmail());
            updated = true;
        }
        if (!StringUtils.hasText(tenant.getDescription()) && StringUtils.hasText(adminProperties.getDefaultDescription())) {
            tenant.setDescription(adminProperties.getDefaultDescription());
            updated = true;
        }
        if (updated) {
            tenant.setUpdatedAt(Instant.now());
            tenantRepository.save(tenant);
        }
    }

    private void createDefaultRealm() {
        Tenant tenant = new Tenant();
        tenant.setName(adminProperties.getRealm());
        tenant.setContactEmail(adminProperties.getDefaultContactEmail());
        tenant.setDescription(adminProperties.getDefaultDescription());
        Instant now = Instant.now();
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantRepository.save(tenant);
    }
}
