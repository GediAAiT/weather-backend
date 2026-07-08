package com.weatherapp.model;

/** Combined payload for frontends that want weather plus country highlights in one call. */
public class WeatherOverviewResponse {
    private WeatherResponse weather;
    private CountryHighlightsResponse highlights;

    public WeatherOverviewResponse() {
    }

    public WeatherOverviewResponse(WeatherResponse weather, CountryHighlightsResponse highlights) {
        this.weather = weather;
        this.highlights = highlights;
    }

    public WeatherResponse getWeather() {
        return weather;
    }

    public void setWeather(WeatherResponse weather) {
        this.weather = weather;
    }

    public CountryHighlightsResponse getHighlights() {
        return highlights;
    }

    public void setHighlights(CountryHighlightsResponse highlights) {
        this.highlights = highlights;
    }
}
