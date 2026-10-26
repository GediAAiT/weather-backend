package com.weatherapp.service;

import com.weatherapp.model.Country;
import com.weatherapp.model.CountryHighlight;
import com.weatherapp.model.CountryHighlightsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fetches live country landmarks and natural features from Wikimedia data. */
@Service
public class CountryHighlightService {

    private static final Logger log = LoggerFactory.getLogger(CountryHighlightService.class);
    private static final String SOURCE = "Wikidata";
    private static final int DEFAULT_LIMIT = 8;

    private final WebClient webClient;
    private final Duration timeout;

    @Value("${country.highlights.wikidata-url}")
    private String wikidataUrl;

    public CountryHighlightService(WebClient webClient,
                                   @Value("${country.highlights.timeout:15s}") Duration timeout) {
        this.timeout = timeout;
        // The shared client enforces a 3s read/response timeout, too short for Wikidata SPARQL queries.
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(timeout);
        this.webClient = webClient.mutate()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Cacheable(cacheNames = "highlights", key = "#country.code")
    public CountryHighlightsResponse getHighlights(Country country) {
        try {
            CountryHighlightsResponse response = new CountryHighlightsResponse();
            response.setCountryName(country.getName());
            response.setCountryCode(country.getCode());
            response.setSource(SOURCE);
            response.setRetrievedAt(Instant.now().toString());
            response.setLive(true);
            response.setHighlights(fetchHighlights(country, DEFAULT_LIMIT));
            return response;
        } catch (Exception ex) {
            log.warn("Could not fetch live highlights for {}: {}", country.getCode(), ex.toString());
            return CountryHighlightsResponse.empty(country, SOURCE);
        }
    }

    @SuppressWarnings("unchecked")
    private List<CountryHighlight> fetchHighlights(Country country, int limit) {
        Map<String, Object> body = webClient.post()
                .uri(wikidataUrl)
                .header(HttpHeaders.ACCEPT, "application/sparql-results+json")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("query", buildQuery(country.getCode(), limit))
                        .with("format", "json"))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .block();

        if (body == null || !(body.get("results") instanceof Map<?, ?> results)
                || !(results.get("bindings") instanceof List<?> bindings)) {
            return List.of();
        }

        Map<String, CountryHighlight> byId = new LinkedHashMap<>();
        for (Object raw : bindings) {
            if (!(raw instanceof Map<?, ?> binding)) {
                continue;
            }
            CountryHighlight highlight = toHighlight((Map<String, Object>) binding);
            if (highlight.getWikidataId() != null && !byId.containsKey(highlight.getWikidataId())) {
                byId.put(highlight.getWikidataId(), highlight);
            }
            if (byId.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(byId.values());
    }

    private CountryHighlight toHighlight(Map<String, Object> binding) {
        String itemUrl = bindingValue(binding, "item");

        CountryHighlight highlight = new CountryHighlight();
        highlight.setTitle(bindingValue(binding, "itemLabel"));
        highlight.setCategory(normalizeCategory(bindingValue(binding, "typeLabel")));
        highlight.setDescription(bindingValue(binding, "itemDescription"));
        highlight.setImageUrl(bindingValue(binding, "image"));
        highlight.setSourceUrl(bindingValue(binding, "article"));
        highlight.setWikidataId(wikidataId(itemUrl));

        if (highlight.getSourceUrl() == null || highlight.getSourceUrl().isBlank()) {
            highlight.setSourceUrl(itemUrl);
        }
        return highlight;
    }

    @SuppressWarnings("unchecked")
    private String bindingValue(Map<String, Object> binding, String key) {
        Object raw = binding.get(key);
        if (!(raw instanceof Map<?, ?> value)) {
            return null;
        }
        Object text = ((Map<String, Object>) value).get("value");
        return text == null ? null : String.valueOf(text);
    }

    private String wikidataId(String itemUrl) {
        if (itemUrl == null || itemUrl.isBlank()) {
            return null;
        }
        int index = itemUrl.lastIndexOf('/');
        return index >= 0 ? itemUrl.substring(index + 1) : itemUrl;
    }

    private String normalizeCategory(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) {
            return "highlight";
        }
        return typeLabel;
    }

    private String buildQuery(String countryCode, int limit) {
        String safeCode = countryCode.replace("\\", "").replace("\"", "").toUpperCase();
        return """
                SELECT ?item ?itemLabel ?itemDescription ?typeLabel ?image ?article ?sitelinks WHERE {
                  ?country wdt:P297 "%s".
                  VALUES ?type {
                    wd:Q570116   # tourist attraction
                    wd:Q41176    # building
                    wd:Q11303    # skyscraper
                    wd:Q4989906  # monument
                    wd:Q839954   # archaeological site
                    wd:Q8502     # mountain
                    wd:Q34038    # waterfall
                    wd:Q23397    # lake
                    wd:Q4022     # river
                    wd:Q46169    # national park
                    wd:Q188524   # natural resource
                    wd:Q23442    # island
                  }
                  ?item wdt:P17 ?country;
                        wdt:P31/wdt:P279* ?type;
                        wikibase:sitelinks ?sitelinks.
                  FILTER(?sitelinks >= 5)
                  FILTER NOT EXISTS { ?item wdt:P31 wd:Q5 }
                  OPTIONAL { ?item wdt:P18 ?image. }
                  OPTIONAL {
                    ?article schema:about ?item;
                             schema:isPartOf <https://en.wikipedia.org/>.
                  }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
                }
                ORDER BY DESC(?sitelinks)
                LIMIT %d
                """.formatted(safeCode, Math.max(1, Math.min(limit * 3, 30)));
    }
}
