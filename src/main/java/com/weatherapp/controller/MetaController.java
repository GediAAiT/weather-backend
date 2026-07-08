package com.weatherapp.controller;

import com.weatherapp.service.LanguageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final LanguageService languageService;

    public MetaController(LanguageService languageService) {
        this.languageService = languageService;
    }

    /** List of supported languages with native names, for the language picker. */
    @GetMapping("/languages")
    public List<Map<String, String>> languages() {
        return languageService.languageList();
    }

    /** Simple health probe. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
