package com.izylife.ssi.controller.admin;

import com.izylife.ssi.dto.TenantRegistrationRequest;
import com.izylife.ssi.dto.TenantResponse;
import com.izylife.ssi.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/admin/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminTenantController {

    private final TenantService tenantService;

    public AdminTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public List<TenantResponse> listTenants() {
        return tenantService.getAllTenants();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse registerTenant(@Valid @RequestBody TenantRegistrationRequest request) {
        return tenantService.registerTenant(request);
    }
}
