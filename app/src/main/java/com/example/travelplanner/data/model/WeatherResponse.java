package com.example.travelplanner.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response from Open-Meteo current-weather API.
 * Endpoint: https://api.open-meteo.com/v1/forecast?latitude=...&longitude=...&current_weather=true
 */
public class WeatherResponse {

    @SerializedName("current_weather")
    private CurrentWeather currentWeather;

    public CurrentWeather getCurrentWeather() {
        return currentWeather;
    }

    public static class CurrentWeather {
        @SerializedName("temperature")
        private double temperature;

        @SerializedName("windspeed")
        private double windSpeed;

        /** WMO weather interpretation code (0=clear, 61=rain, 71=snow, 95=thunderstorm...). */
        @SerializedName("weathercode")
        private int weatherCode;

        public double getTemperature() { return temperature; }
        public double getWindSpeed()    { return windSpeed; }
        public int    getWeatherCode()  { return weatherCode; }
    }
}
