package com.weatherapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LanguageService {

    @Value("${app.supported-languages}")
    private String supportedCsv;

    @Value("${app.default-language}")
    private String defaultLanguage;

    private static final Map<String, String> NATIVE_NAMES = Map.of(
            "en", "English",
            "fr", "Français",
            "es", "Español",
            "nl", "Nederlands",
            "de", "Deutsch",
            "it", "Italiano",
            "ru", "Русский",
            "am", "አማርኛ"
    );

    public List<String> supportedCodes() {
        return Arrays.asList(supportedCsv.split(","));
    }

    /** Returns the requested language if supported, otherwise the default. */
    public String resolve(String requested) {
        if (requested == null) {
            return defaultLanguage;
        }
        String normalized = requested.toLowerCase().trim();
        return supportedCodes().contains(normalized) ? normalized : defaultLanguage;
    }

    /** Ordered list of {code, name} for clients to build a language picker. */
    public List<Map<String, String>> languageList() {
        return supportedCodes().stream()
                .map(code -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("code", code);
                    m.put("name", NATIVE_NAMES.getOrDefault(code, code));
                    return m;
                })
                .toList();
    }
}
