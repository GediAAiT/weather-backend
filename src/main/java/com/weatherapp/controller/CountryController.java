package com.weatherapp.controller;

import com.weatherapp.model.Country;
import com.weatherapp.model.CountryHighlightsResponse;
import com.weatherapp.service.CountryHighlightService;
import com.weatherapp.service.CountryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private final CountryService countryService;
    private final CountryHighlightService countryHighlightService;

    public CountryController(CountryService countryService,
                             CountryHighlightService countryHighlightService) {
        this.countryService = countryService;
        this.countryHighlightService = countryHighlightService;
    }

    /** All countries, alphabetically sorted. */
    @GetMapping
    public List<Country> all(@RequestParam(required = false) String continent) {
        if (continent != null && !continent.isBlank()) {
            return countryService.getByContinent(continent);
        }
        return countryService.getAll();
    }

    /** Live landmarks, buildings, natural features, and unique places for a country. */
    @GetMapping("/{code}/highlights")
    public CountryHighlightsResponse highlights(@PathVariable String code) {
        Country country = countryService.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown country code: " + code));
        return countryHighlightService.getHighlights(country);
    }
}
