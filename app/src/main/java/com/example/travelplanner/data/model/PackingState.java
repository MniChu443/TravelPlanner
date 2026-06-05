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

    private PackingState(boolean loading,
                         List<PackingItem> items,
                         String imageUrl,
                         String errorMessage,
                         String cityName) {
        this.loading = loading;
        this.items = items == null ? Collections.emptyList() : items;
        this.imageUrl = imageUrl;
        this.errorMessage = errorMessage;
        this.cityName = cityName;
    }

    public static PackingState loading() {
        return new PackingState(true, null, null, null, null);
    }

    public static PackingState success(List<PackingItem> items,
                                      String imageUrl,
                                      String cityName) {
        return new PackingState(false, items, imageUrl, null, cityName);
    }

    public static PackingState error(String message) {
        return new PackingState(false, Collections.emptyList(), null, message, null);
    }
}
