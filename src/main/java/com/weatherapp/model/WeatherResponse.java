package com.weatherapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Flat, client-friendly weather payload returned by /api/weather.
 * The `condition` and `conditionKey` fields are already localized / keyed
 * so Angular and Flutter can show a translated, human-readable description.
 */
public class WeatherResponse {
    private String countryName;
    private String countryCode;
    private String capital;
    private double latitude;
    private double longitude;

    private double temperature;        // °C
    private double apparentTemperature; // °C "feels like"
    private double windSpeed;           // km/h
    private int windDirection;          // degrees
    private int humidity;               // %
    private double precipitation;       // mm
    private boolean isDay;

    private int weatherCode;            // raw WMO code
    private String conditionKey;        // e.g. "clear", "rain" -> for client i18n / icons
    private String condition;           // localized text in the requested language
    private String language;            // language the text was localized to
    private String time;                // ISO local time at the location

    // --- getters & setters ---

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

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getApparentTemperature() { return apparentTemperature; }
    public void setApparentTemperature(double apparentTemperature) { this.apparentTemperature = apparentTemperature; }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    public int getWindDirection() { return windDirection; }
    public void setWindDirection(int windDirection) { this.windDirection = windDirection; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }

    @JsonProperty("isDay")
    public boolean isDay() { return isDay; }
    @JsonProperty("isDay")
    public void setDay(boolean day) { isDay = day; }

    public int getWeatherCode() { return weatherCode; }
    public void setWeatherCode(int weatherCode) { this.weatherCode = weatherCode; }

    public String getConditionKey() { return conditionKey; }
    public void setConditionKey(String conditionKey) { this.conditionKey = conditionKey; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
