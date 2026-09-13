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
    public final String errorMessage;   
    public final String cityName;       
    public final double lat;
    public final double lon;
    public final String tripId;
    public final long tripDateInMillis;

    private PackingState(boolean loading,
                         List<PackingItem> items,
                         String imageUrl,
                         String errorMessage,
                         String cityName,
                         double lat,
                         double lon,
                         String tripId,
                         long tripDateInMillis) {
        this.loading = loading;
        this.items = items == null ? Collections.emptyList() : items;
        this.imageUrl = imageUrl;
        this.errorMessage = errorMessage;
        this.cityName = cityName;
        this.lat = lat;
        this.lon = lon;
        this.tripId = tripId;
        this.tripDateInMillis = tripDateInMillis;
    }

    public static PackingState loading() {
        return new PackingState(true, null, null, null, null, 0, 0, null, 0);
    }

    public static PackingState success(List<PackingItem> items,
                                      String imageUrl,
                                      String cityName,
                                      double lat,
                                      double lon,
                                      String tripId,
                                      long tripDateInMillis) {
        return new PackingState(false, items, imageUrl, null, cityName, lat, lon, tripId, tripDateInMillis);
    }

    public static PackingState error(String message) {
        return new PackingState(false, Collections.emptyList(), null, message, null, 0, 0, null, 0);
    }

    public static PackingState idle() {
        return new PackingState(false, null, null, null, null, 0, 0, null, 0);
    }
}
