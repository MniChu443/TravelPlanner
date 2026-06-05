package com.example.travelplanner.data.remote;

import com.example.travelplanner.data.model.GeocodingResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Nominatim (OpenStreetMap) Geocoding API.
 * Docs: https://nominatim.org/release-docs/develop/api/Search/
 */
public interface GeocodingApi {

    @GET("search")
    Call<List<GeocodingResponse>> searchCity(
            @Query("q") String cityName,
            @Query("format") String format,        // "json"
            @Query("limit") int limit              // usually 1
    );
}
