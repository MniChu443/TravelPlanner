package com.example.travelplanner.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.travelplanner.data.model.CountryResponse;
import com.example.travelplanner.data.model.GeocodingResponse;
import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PixabayResponse;
import com.example.travelplanner.data.model.WeatherResponse;
import com.example.travelplanner.data.remote.RetrofitClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Single point of contact for the ViewModel.
 * Coordinates the chained calls: Geocoding → (Weather, Country, Image) in parallel.
 *
 * All methods are synchronous-on-purpose: they block on the calling background thread.
 * The ViewModel is responsible for off-loading them off the main thread.
 */
public class PackingRepository {

    /**
     * Synchronously resolves a city and returns a result.
     * The Callback is invoked on the *same* thread that called the method.
     */
    public void buildPackingList(@NonNull String cityName,
                                 @NonNull Callback callback) {
        try {
            // ---------- Step 1: Geocoding ----------
            Call<List<GeocodingResponse>> geoCall =
                    RetrofitClient.geocoding()
                            .searchCity(cityName, "json", 1);
            Response<List<GeocodingResponse>> geoResp = geoCall.execute();
            if (!geoResp.isSuccessful() || geoResp.body() == null || geoResp.body().isEmpty()) {
                callback.onError("City not found: " + cityName);
                return;
            }
            GeocodingResponse geo = geoResp.body().get(0);
            final double lat = geo.getLatDouble();
            final double lon = geo.getLonDouble();
            final String countryCode = geo.getCountryCode();

            // ---------- Step 2: parallel calls ----------
            final WeatherResponse[] weatherBox = new WeatherResponse[1];
            final CountryResponse[] countryBox = new CountryResponse[1];
            final String[] imageBox = new String[1];
            final String[] firstError = new String[1];
            final AtomicInteger remaining = new AtomicInteger(3);

            Runnable onEachComplete = () -> {
                if (remaining.decrementAndGet() != 0) return;

                if (firstError[0] != null) {
                    callback.onError(firstError[0]);
                    return;
                }
                List<PackingItem> items = buildList(weatherBox[0], countryBox[0]);
                callback.onSuccess(items, imageBox[0], cityName);
            };

            // a) Weather
            new Thread(() -> {
                try {
                    Response<WeatherResponse> w = RetrofitClient.weather()
                            .getCurrentWeather(lat, lon, true)
                            .execute();
                    if (w.isSuccessful() && w.body() != null) {
                        weatherBox[0] = w.body();
                    } else if (firstError[0] == null) {
                        firstError[0] = "Weather request failed";
                    }
                } catch (IOException e) {
                    if (firstError[0] == null) firstError[0] = "Weather: " + e.getMessage();
                } finally {
                    onEachComplete.run();
                }
            }, "weather-call").start();

            // b) Country
            new Thread(() -> {
                try {
                    Response<List<CountryResponse>> c = RetrofitClient.country()
                            .getCountryByCode(countryCode)
                            .execute();
                    if (c.isSuccessful() && c.body() != null && !c.body().isEmpty()) {
                        countryBox[0] = c.body().get(0);
                    } else if (firstError[0] == null) {
                        firstError[0] = "Country request failed";
                    }
                } catch (IOException e) {
                    if (firstError[0] == null) firstError[0] = "Country: " + e.getMessage();
                } finally {
                    onEachComplete.run();
                }
            }, "country-call").start();

            // c) Image
            new Thread(() -> {
                try {
                    Response<PixabayResponse> p = RetrofitClient.pixabay()
                            .searchImage(RetrofitClient.PIXABAY_API_KEY,
                                    cityName, "photo")
                            .execute();
                    if (p.isSuccessful()
                            && p.body() != null
                            && p.body().getHits() != null
                            && !p.body().getHits().isEmpty()) {
                        imageBox[0] = p.body().getHits().get(0).getLargeImageURL();
                    } // No error: image is optional / has a fallback drawable.
                } catch (Exception e) {
                    // Image errors are silent – we just keep the placeholder.
                } finally {
                    onEachComplete.run();
                }
            }, "image-call").start();

        } catch (IOException e) {
            callback.onError("Geocoding failed: " + e.getMessage());
        }
    }

    /**
     * Pure function that turns API responses into a packing list.
     * Kept package-private for testability.
     */
    @NonNull
    List<PackingItem> buildList(@Nullable WeatherResponse weather,
                                @Nullable CountryResponse country) {
        List<PackingItem> list = new ArrayList<>();
        // Base items
        list.add(new PackingItem("Passport"));
        list.add(new PackingItem("Toothbrush"));
        list.add(new PackingItem("Phone Charger"));

        if (weather != null && weather.getCurrentWeather() != null) {
            double temp = weather.getCurrentWeather().getTemperature();
            int code   = weather.getCurrentWeather().getWeatherCode();

            if (temp < 15) list.add(new PackingItem("Jacket"));
            if (temp > 25) list.add(new PackingItem("Sunscreen"));

            // WMO weather codes: 51-67 = drizzle/rain, 80-82 = showers,
            // 95-99 = thunderstorm.  We treat the "rainy" family as umbrella-worthy.
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) {
                list.add(new PackingItem("Umbrella"));
            }
        }

        if (country != null) {
            list.add(new PackingItem("Cash: " + country.getFirstCurrencyCode()));
            list.add(new PackingItem("Offline dictionary: " + country.getFirstLanguageName()));
        }

        return Collections.unmodifiableList(list);
    }

    /** Repository callback delivered to the ViewModel. */
    public interface Callback {
        void onSuccess(@NonNull List<PackingItem> items,
                       @Nullable String imageUrl,
                       @NonNull String cityName);
        void onError(@NonNull String message);
    }
}
