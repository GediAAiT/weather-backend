package com.weatherapp.service;

import com.weatherapp.model.GeoPlace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves a free-text city/place name to coordinates via the Open-Meteo geocoding API. */
@Service
public class GeocodingService {

    private final WebClient webClient;

    @Value("${weather.geocoding-url}")
    private String geocodingUrl;

    @Value("${weather.upstream-timeout:2s}")
    private Duration upstreamTimeout = Duration.ofSeconds(2);

    public GeocodingService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Returns the best match for the given place name. Results are cached so repeated
     * searches for the same city never hit the upstream geocoder.
     */
    @SuppressWarnings("unchecked")
    @Cacheable(cacheNames = "geocode", key = "#query.trim().toLowerCase()")
    public Optional<GeoPlace> search(String query) {
        String url = geocodingUrl
                + "?name=" + UriUtils.encodeQueryParam(query.trim(), StandardCharsets.UTF_8)
                + "&count=1&format=json";

        Map<String, Object> body;
        try {
            body = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(upstreamTimeout)
                    .block();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach the geocoding provider", ex);
        }

        if (body == null || !(body.get("results") instanceof List<?> results) || results.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> first = (Map<String, Object>) results.get(0);
        return Optional.of(new GeoPlace(
                String.valueOf(first.get("name")),
                first.get("country") == null ? "" : String.valueOf(first.get("country")),
                first.get("country_code") == null ? "" : String.valueOf(first.get("country_code")),
                ((Number) first.get("latitude")).doubleValue(),
                ((Number) first.get("longitude")).doubleValue()));
    }
}
