package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Response from REST Countries API.
 * Endpoint: https://restcountries.com/v3.1/alpha/{countryCode}
 *
 * Example fields we care about:
 *   "languages": { "fra": "French" }
 *   "currencies": { "EUR": { "name": "Euro", "symbol": "€" } }
 *
 * These are top-level JSON objects, so we model them as Map<String, ...>.
 */
public class CountryResponse {

    @SerializedName("languages")
    private Map<String, String> languages;

    @SerializedName("currencies")
    private Map<String, CurrencyDetail> currencies;

    public Map<String, String> getLanguages() { return languages; }
    public Map<String, CurrencyDetail> getCurrencies() { return currencies; }

    /** Returns a human-readable language name, e.g. "French". */
    public String getFirstLanguageName() {
        if (languages == null || languages.isEmpty()) {
            return "Local language";
        }
        // Use the first key in the map (any one is fine for display).
        return languages.values().iterator().next();
    }

    /** Returns currency code, e.g. "EUR". */
    public String getFirstCurrencyCode() {
        if (currencies == null || currencies.isEmpty()) {
            return "Local currency";
        }
        return currencies.keySet().iterator().next();
    }

    public static class CurrencyDetail {
        @SerializedName("name")
        public String name;

        @SerializedName("symbol")
        public String symbol;
    }
}
