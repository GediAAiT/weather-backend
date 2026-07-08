package com.weatherapp.model;

import java.util.List;

/** Multi-day forecast payload returned by /api/weather/forecast. */
public class ForecastResponse {
    private String countryName;
    private String countryCode;
    private String capital;
    private double latitude;
    private double longitude;
    private String language;           // language the texts were localized to
    private List<DailyForecast> days;

    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCapital() { return capital; }
    public void setCapital(String capital) { this.capital = capital; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public List<DailyForecast> getDays() { return days; }
    public void setDays(List<DailyForecast> days) { this.days = days; }
}