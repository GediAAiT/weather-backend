package com.weatherapp.controller;

import com.weatherapp.model.Country;
import com.weatherapp.model.CountryHighlightsResponse;
import com.weatherapp.model.ForecastResponse;
import com.weatherapp.model.GeoPlace;
import com.weatherapp.model.WeatherOverviewResponse;
import com.weatherapp.model.WeatherResponse;
import com.weatherapp.service.CountryHighlightService;
import com.weatherapp.service.CountryService;
import com.weatherapp.service.GeocodingService;
import com.weatherapp.service.LanguageService;
import com.weatherapp.service.WeatherService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;
    private final CountryService countryService;
    private final CountryHighlightService countryHighlightService;
    private final LanguageService languageService;
    private final GeocodingService geocodingService;
    private final ExecutorService weatherExecutor;

    public WeatherController(WeatherService weatherService,
                             CountryService countryService,
                             CountryHighlightService countryHighlightService,
                             LanguageService languageService,
                             GeocodingService geocodingService,
                             ExecutorService weatherExecutor) {
        this.weatherService = weatherService;
        this.countryService = countryService;
        this.countryHighlightService = countryHighlightService;
        this.languageService = languageService;
        this.geocodingService = geocodingService;
        this.weatherExecutor = weatherExecutor;
    }

    /**
     * Current weather for a single country's capital.
     * Example: GET /api/weather?code=FR&lang=fr
     */
    @GetMapping
    public WeatherResponse byCountry(@RequestParam String code,
                                     @RequestParam(required = false, defaultValue = "en") String lang) {
        String resolvedLang = languageService.resolve(lang);
        Country country = countryService.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown country code: " + code));
        return weatherService.getWeather(country, resolvedLang);
    }

    /**
     * Current weather plus live country highlights in one frontend-friendly call.
     * Example: GET /api/weather/overview?code=FR&lang=fr
     */
    @GetMapping("/overview")
    public WeatherOverviewResponse overview(@RequestParam String code,
                                            @RequestParam(required = false, defaultValue = "en") String lang) {
        String resolvedLang = languageService.resolve(lang);
        Country country = countryService.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown country code: " + code));

        CompletableFuture<WeatherResponse> weatherFuture = CompletableFuture.supplyAsync(
                () -> weatherService.getWeather(country, resolvedLang), weatherExecutor);
        CompletableFuture<CountryHighlightsResponse> highlightsFuture = CompletableFuture.supplyAsync(
                () -> countryHighlightService.getHighlights(country), weatherExecutor);

        try {
            return new WeatherOverviewResponse(weatherFuture.join(), highlightsFuture.join());
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw ex;
        }
    }

    /**
     * Current weather for several countries at once. Upstream calls run in parallel.
     * Example: GET /api/weather/batch?codes=FR,DE,ET&lang=de
     */
    @GetMapping("/batch")
    public List<WeatherResponse> batch(@RequestParam String codes,
                                       @RequestParam(required = false, defaultValue = "en") String lang) {
        String resolvedLang = languageService.resolve(lang);
        List<Country> countries = java.util.Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .map(countryService::findByCode)
                .flatMap(java.util.Optional::stream)
                .toList();

        List<CompletableFuture<WeatherResponse>> futures = countries.stream()
                .map(country -> CompletableFuture.supplyAsync(
                        () -> weatherService.getWeather(country, resolvedLang), weatherExecutor))
                .toList();

        try {
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw ex;
        }
    }

    /**
     * Current weather for any city or place name (not just capitals).
     * Example: GET /api/weather/city?q=Hamburg&lang=de
     */
    @GetMapping("/city")
    public WeatherResponse byCity(@RequestParam String q,
                                  @RequestParam(required = false, defaultValue = "en") String lang) {
        String resolvedLang = languageService.resolve(lang);
        GeoPlace place = geocodingService.search(q)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown place: " + q));
        return weatherService.getWeatherAt(place.latitude(), place.longitude(),
                place.name(), place.country(), place.countryCode(), resolvedLang);
    }

    /**
     * Current weather for raw coordinates.
     * Example: GET /api/weather/coords?lat=48.85&lon=2.35&lang=fr
     */
    @GetMapping("/coords")
    public WeatherResponse byCoords(@RequestParam double lat,
                                    @RequestParam double lon,
                                    @RequestParam(required = false, defaultValue = "en") String lang) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Coordinates out of range: lat must be -90..90, lon must be -180..180");
        }
        String resolvedLang = languageService.resolve(lang);
        return weatherService.getWeatherAt(lat, lon,
                lat + "," + lon, "", "", resolvedLang);
    }

    /**
     * Multi-day forecast for a country's capital.
     * Example: GET /api/weather/forecast?code=FR&days=5&lang=fr
     */
    @GetMapping("/forecast")
    public ForecastResponse forecast(@RequestParam String code,
                                     @RequestParam(required = false, defaultValue = "7") int days,
                                     @RequestParam(required = false, defaultValue = "en") String lang) {
        if (days < 1 || days > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "days must be between 1 and 16");
        }
        String resolvedLang = languageService.resolve(lang);
        Country country = countryService.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown country code: " + code));
        return weatherService.getForecast(country, days, resolvedLang);
    }
}
