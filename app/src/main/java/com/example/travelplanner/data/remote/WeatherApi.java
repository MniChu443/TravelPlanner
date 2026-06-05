package com.example.travelplanner.data.remote;

import com.example.travelplanner.data.model.WeatherResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Open-Meteo current-weather API.
 * Docs: https://open-meteo.com/en/docs
 */
public interface WeatherApi {

    @GET("v1/forecast")
    Call<WeatherResponse> getCurrentWeather(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current_weather") boolean currentWeather   // true
    );
}
