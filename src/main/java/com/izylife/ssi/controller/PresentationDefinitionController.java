package com.izylife.ssi.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/definitions", produces = MediaType.APPLICATION_JSON_VALUE)
public class PresentationDefinitionController {

    private final Resource staffDefinition = new ClassPathResource("definitions/staff-credential.json");

    @GetMapping("/staff-credential.json")
    public ResponseEntity<Resource> getStaffCredentialDefinition() {
        if (!staffDefinition.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.APPLICATION_JSON)
                .body(staffDefinition);
    }
}
