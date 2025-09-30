package com.izylife.ssi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/", "/issuer", "/verifier"})
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
