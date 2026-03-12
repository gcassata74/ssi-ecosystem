package com.izylife.ssi.controller.admin;

import com.izylife.ssi.dto.admin.PresentationDefinitionRequest;
import com.izylife.ssi.dto.admin.PresentationDefinitionResponse;
import com.izylife.ssi.service.AdminPresentationDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/admin/tenants/{tenantId}/clients/{clientId}/definitions", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminPresentationDefinitionController {

    private final AdminPresentationDefinitionService definitionService;

    public AdminPresentationDefinitionController(AdminPresentationDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @GetMapping
    public List<PresentationDefinitionResponse> listDefinitions(@PathVariable String tenantId,
                                                                @PathVariable String clientId) {
        return definitionService.listDefinitions(tenantId, clientId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PresentationDefinitionResponse createDefinition(@PathVariable String tenantId,
                                                            @PathVariable String clientId,
                                                            @Valid @RequestBody PresentationDefinitionRequest request) {
        return definitionService.createDefinition(tenantId, clientId, request);
    }

    @GetMapping(path = "/{definitionId}")
    public PresentationDefinitionResponse getDefinition(@PathVariable String tenantId,
                                                         @PathVariable String clientId,
                                                         @PathVariable String definitionId) {
        return definitionService.getDefinition(tenantId, clientId, definitionId);
    }

    @PutMapping(path = "/{definitionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PresentationDefinitionResponse updateDefinition(@PathVariable String tenantId,
                                                            @PathVariable String clientId,
                                                            @PathVariable String definitionId,
                                                            @Valid @RequestBody PresentationDefinitionRequest request) {
        return definitionService.updateDefinition(tenantId, clientId, definitionId, request);
    }

    @DeleteMapping(path = "/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDefinition(@PathVariable String tenantId,
                                 @PathVariable String clientId,
                                 @PathVariable String definitionId) {
        definitionService.deleteDefinition(tenantId, clientId, definitionId);
    }
}
