package com.f1bets.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
            "service", "F1 Bets API",
            "version", "1.0.0",
            "documentation", "/swagger-ui.html"
        );
    }
}
