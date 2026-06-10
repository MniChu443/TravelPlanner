package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from Nominatim OpenStreetMap Geocoding API.
 * Endpoint: https://nominatim.openstreetmap.org/search?q=Paris&format=json&limit=1
 *
 * We only model the fields we actually use; extra fields are ignored by Gson.
 */
public class GeocodingResponse {

    @SerializedName("lat")
    private String lat;

    @SerializedName("lon")
    private String lon;

    @SerializedName("country_code")
    private String countryCode;

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("address")
    private Address address;

    public static class Address {
        @SerializedName("city") public String city;
        @SerializedName("town") public String town;
        @SerializedName("village") public String village;
        @SerializedName("municipality") public String municipality;
    }

    public String getCityOnly() {
        if (address == null) return displayName;
        if (address.city != null) return address.city;
        if (address.town != null) return address.town;
        if (address.village != null) return address.village;
        if (address.municipality != null) return address.municipality;
        return displayName;
    }

    public String getLat() {
        return lat;
    }

    public String getLon() {
        return lon;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Helper: convert string lat/lon to doubles. */
    public double getLatDouble() {
        try { return Double.parseDouble(lat); } catch (Exception e) { return 0.0; }
    }

    public double getLonDouble() {
        try { return Double.parseDouble(lon); } catch (Exception e) { return 0.0; }
    }
}
