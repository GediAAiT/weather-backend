package com.weatherapp.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.weatherapp.model.Country;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CountryService {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private List<Country> countries = List.of();
    private Map<String, Country> byCode = Map.of();

    @PostConstruct
    public void load() throws Exception {
        ClassPathResource resource = new ClassPathResource("countries.json");
        try (InputStream is = resource.getInputStream()) {
            Country[] arr = objectMapper.readValue(is, Country[].class);
            countries = List.of(arr).stream()
                    .sorted(Comparator.comparing(Country::getName))
                    .collect(Collectors.toList());
            byCode = countries.stream()
                    .collect(Collectors.toMap(c -> c.getCode().toUpperCase(), c -> c));
        }
    }

    public List<Country> getAll() {
        return countries;
    }

    public List<Country> getByContinent(String continent) {
        return countries.stream()
                .filter(c -> c.getContinent().equalsIgnoreCase(continent))
                .collect(Collectors.toList());
    }

    public Optional<Country> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code.toUpperCase()));
    }
}
