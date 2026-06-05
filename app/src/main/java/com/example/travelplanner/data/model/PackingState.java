package com.example.travelplanner.data.model;

import java.util.Collections;
import java.util.List;

/**
 * A single immutable "screen state" that the ViewModel exposes.
 * The Activity observes this and updates the UI accordingly.
 */
public class PackingState {

    public final boolean loading;
    public final List<PackingItem> items;
    public final String imageUrl;
    public final String errorMessage;   // null when no error
    public final String cityName;       // last searched city (for context in UI)
    public final double lat;
    public final double lon;

    private PackingState(boolean loading,
                         List<PackingItem> items,
                         String imageUrl,
                         String errorMessage,
                         String cityName,
                         double lat,
                         double lon) {
        this.loading = loading;
        this.items = items == null ? Collections.emptyList() : items;
        this.imageUrl = imageUrl;
        this.errorMessage = errorMessage;
        this.cityName = cityName;
        this.lat = lat;
        this.lon = lon;
    }

    public static PackingState loading() {
        return new PackingState(true, null, null, null, null, 0, 0);
    }

    public static PackingState success(List<PackingItem> items,
                                      String imageUrl,
                                      String cityName,
                                      double lat,
                                      double lon) {
        return new PackingState(false, items, imageUrl, null, cityName, lat, lon);
    }

    public static PackingState error(String message) {
        return new PackingState(false, Collections.emptyList(), null, message, null, 0, 0);
    }
}
