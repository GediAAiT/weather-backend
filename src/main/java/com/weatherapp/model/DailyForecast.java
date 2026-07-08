package com.weatherapp.model;

/** One day of the forecast returned by /api/weather/forecast. */
public class DailyForecast {
    private String date;               // ISO date at the location
    private double temperatureMax;     // °C
    private double temperatureMin;     // °C
    private double precipitation;      // mm summed over the day
    private double windSpeedMax;       // km/h
    private int weatherCode;           // raw WMO code
    private String conditionKey;       // e.g. "clear", "rain" -> for client i18n / icons
    private String condition;          // localized text in the requested language

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public double getTemperatureMax() { return temperatureMax; }
    public void setTemperatureMax(double temperatureMax) { this.temperatureMax = temperatureMax; }

    public double getTemperatureMin() { return temperatureMin; }
    public void setTemperatureMin(double temperatureMin) { this.temperatureMin = temperatureMin; }

    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }

    public double getWindSpeedMax() { return windSpeedMax; }
    public void setWindSpeedMax(double windSpeedMax) { this.windSpeedMax = windSpeedMax; }

    public int getWeatherCode() { return weatherCode; }
    public void setWeatherCode(int weatherCode) { this.weatherCode = weatherCode; }

    public String getConditionKey() { return conditionKey; }
    public void setConditionKey(String conditionKey) { this.conditionKey = conditionKey; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}