package com.weatherapp.model;

import java.util.List;

/** Country-specific live highlights suitable for display after selecting a country. */
public class CountryHighlightsResponse {
    private String countryName;
    private String countryCode;
    private String source;
    private String retrievedAt;
    private boolean live;
    private List<CountryHighlight> highlights;

    public static CountryHighlightsResponse empty(Country country, String source) {
        CountryHighlightsResponse response = new CountryHighlightsResponse();
        response.setCountryName(country.getName());
        response.setCountryCode(country.getCode());
        response.setSource(source);
        response.setRetrievedAt(java.time.Instant.now().toString());
        response.setLive(true);
        response.setHighlights(List.of());
        return response;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(String retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    public List<CountryHighlight> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<CountryHighlight> highlights) {
        this.highlights = highlights;
    }
}
