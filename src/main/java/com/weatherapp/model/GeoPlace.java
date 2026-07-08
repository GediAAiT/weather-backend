package com.weatherapp.model;

/** A place resolved by the geocoding API (city search). */
public record GeoPlace(
        String name,
        String country,
        String countryCode,
        double latitude,
        double longitude) {
}