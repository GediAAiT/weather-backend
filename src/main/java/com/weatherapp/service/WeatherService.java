package com.weatherapp.service;

import com.weatherapp.model.Country;
import com.weatherapp.model.DailyForecast;
import com.weatherapp.model.ForecastResponse;
import com.weatherapp.model.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WeatherService {

    private static final String CURRENT_PARAMS =
            "current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                    + "is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m";

    private static final String DAILY_PARAMS =
            "daily=weather_code,temperature_2m_max,temperature_2m_min,"
                    + "precipitation_sum,wind_speed_10m_max";

    private final WebClient webClient;
    private final MessageSource messageSource;

    @Value("${weather.base-url}")
    private String baseUrl;

    @Value("${weather.upstream-timeout:2s}")
    private Duration upstreamTimeout = Duration.ofSeconds(2);

    public WeatherService(WebClient webClient, MessageSource messageSource) {
        this.webClient = webClient;
        this.messageSource = messageSource;
    }

    /** Current weather for a country's capital. Cached per country + language. */
    @Cacheable(cacheNames = "weather", key = "#country.code + ':' + #lang")
    public WeatherResponse getWeather(Country country, String lang) {
        return buildCurrent(country.getLatitude(), country.getLongitude(),
                country.getName(), country.getCode(), country.getCapital(), lang);
    }

    /** Current weather for arbitrary coordinates / a geocoded city. Cached per location + language. */
    @Cacheable(cacheNames = "weather",
            key = "'geo:' + #latitude + ':' + #longitude + ':' + #lang")
    public WeatherResponse getWeatherAt(double latitude, double longitude,
                                        String placeName, String countryName,
                                        String countryCode, String lang) {
        return buildCurrent(latitude, longitude, countryName, countryCode, placeName, lang);
    }

    /** Multi-day forecast for a country's capital. Cached per country + days + language. */
    @SuppressWarnings("unchecked")
    @Cacheable(cacheNames = "forecast", key = "#country.code + ':' + #days + ':' + #lang")
    public ForecastResponse getForecast(Country country, int days, String lang) {
        String url = baseUrl
                + "?latitude=" + country.getLatitude()
                + "&longitude=" + country.getLongitude()
                + "&" + DAILY_PARAMS
                + "&forecast_days=" + days
                + "&timezone=auto";

        Map<String, Object> body = fetchJson(url);
        if (body == null || body.get("daily") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No forecast data returned for " + country.getName());
        }

        Map<String, Object> daily = (Map<String, Object>) body.get("daily");
        List<String> dates = (List<String>) daily.get("time");
        List<Object> codes = (List<Object>) daily.get("weather_code");
        List<Object> tMax = (List<Object>) daily.get("temperature_2m_max");
        List<Object> tMin = (List<Object>) daily.get("temperature_2m_min");
        List<Object> rain = (List<Object>) daily.get("precipitation_sum");
        List<Object> wind = (List<Object>) daily.get("wind_speed_10m_max");

        Locale locale = Locale.forLanguageTag(lang);
        List<DailyForecast> result = new ArrayList<>(dates.size());
        for (int i = 0; i < dates.size(); i++) {
            DailyForecast d = new DailyForecast();
            d.setDate(dates.get(i));
            d.setTemperatureMax(asDouble(tMax.get(i)));
            d.setTemperatureMin(asDouble(tMin.get(i)));
            d.setPrecipitation(asDouble(rain.get(i)));
            d.setWindSpeedMax(asDouble(wind.get(i)));

            int weatherCode = asInt(codes.get(i));
            String conditionKey = mapCodeToKey(weatherCode);
            d.setWeatherCode(weatherCode);
            d.setConditionKey(conditionKey.replace("weather.", ""));
            d.setCondition(messageSource.getMessage(conditionKey, null, conditionKey, locale));
            result.add(d);
        }

        ForecastResponse r = new ForecastResponse();
        r.setCountryName(country.getName());
        r.setCountryCode(country.getCode());
        r.setCapital(country.getCapital());
        r.setLatitude(country.getLatitude());
        r.setLongitude(country.getLongitude());
        r.setLanguage(lang);
        r.setDays(result);
        return r;
    }

    @SuppressWarnings("unchecked")
    private WeatherResponse buildCurrent(double latitude, double longitude,
                                         String countryName, String countryCode,
                                         String placeName, String lang) {
        String url = baseUrl
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&" + CURRENT_PARAMS
                + "&timezone=auto";

        Map<String, Object> body = fetchJson(url);
        if (body == null || body.get("current") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No weather data returned for " + placeName);
        }

        Map<String, Object> current = (Map<String, Object>) body.get("current");

        int weatherCode = asInt(current.get("weather_code"));
        String conditionKey = mapCodeToKey(weatherCode);
        Locale locale = Locale.forLanguageTag(lang);
        String condition = messageSource.getMessage(conditionKey, null, conditionKey, locale);

        WeatherResponse r = new WeatherResponse();
        r.setCountryName(countryName);
        r.setCountryCode(countryCode);
        r.setCapital(placeName);
        r.setLatitude(latitude);
        r.setLongitude(longitude);

        r.setTemperature(asDouble(current.get("temperature_2m")));
        r.setApparentTemperature(asDouble(current.get("apparent_temperature")));
        r.setHumidity(asInt(current.get("relative_humidity_2m")));
        r.setPrecipitation(asDouble(current.get("precipitation")));
        r.setWindSpeed(asDouble(current.get("wind_speed_10m")));
        r.setWindDirection(asInt(current.get("wind_direction_10m")));
        r.setDay(asInt(current.get("is_day")) == 1);

        r.setWeatherCode(weatherCode);
        // conditionKey returned to the client is the short form without the "weather." prefix
        r.setConditionKey(conditionKey.replace("weather.", ""));
        r.setCondition(condition);
        r.setLanguage(lang);
        r.setTime(String.valueOf(current.get("time")));

        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchJson(String url) {
        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(upstreamTimeout)
                    .block();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach the weather provider", ex);
        }
    }

    /** Maps a WMO weather interpretation code to an i18n message key. */
    private String mapCodeToKey(int code) {
        return switch (code) {
            case 0 -> "weather.clear";
            case 1 -> "weather.mainly_clear";
            case 2 -> "weather.partly_cloudy";
            case 3 -> "weather.overcast";
            case 45 -> "weather.fog";
            case 48 -> "weather.rime_fog";
            case 51 -> "weather.drizzle_light";
            case 53 -> "weather.drizzle_moderate";
            case 55 -> "weather.drizzle_dense";
            case 56 -> "weather.freezing_drizzle_light";
            case 57 -> "weather.freezing_drizzle_dense";
            case 61 -> "weather.rain_slight";
            case 63 -> "weather.rain_moderate";
            case 65 -> "weather.rain_heavy";
            case 66 -> "weather.freezing_rain_light";
            case 67 -> "weather.freezing_rain_heavy";
            case 71 -> "weather.snow_slight";
            case 73 -> "weather.snow_moderate";
            case 75 -> "weather.snow_heavy";
            case 77 -> "weather.snow_grains";
            case 80 -> "weather.rain_showers_slight";
            case 81 -> "weather.rain_showers_moderate";
            case 82 -> "weather.rain_showers_violent";
            case 85 -> "weather.snow_showers_slight";
            case 86 -> "weather.snow_showers_heavy";
            case 95 -> "weather.thunderstorm";
            case 96 -> "weather.thunderstorm_hail_slight";
            case 99 -> "weather.thunderstorm_hail_heavy";
            default -> "weather.unknown";
        };
    }

    private double asDouble(Object o) {
        return o == null ? 0d : ((Number) o).doubleValue();
    }

    private int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}
