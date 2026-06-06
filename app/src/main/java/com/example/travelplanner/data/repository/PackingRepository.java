package com.example.travelplanner.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.travelplanner.data.model.CountryResponse;
import com.example.travelplanner.data.model.GeocodingResponse;
import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PixabayResponse;
import com.example.travelplanner.data.model.WeatherResponse;
import com.example.travelplanner.data.remote.RetrofitClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
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
                            .searchCity(cityName, "jsonv2", 1);
            Response<List<GeocodingResponse>> geoResp = geoCall.execute();
            if (!geoResp.isSuccessful() || geoResp.body() == null || geoResp.body().isEmpty()) {
                callback.onError("City not found: " + cityName);
                return;
            }
            GeocodingResponse geo = geoResp.body().get(0);
            final double lat = geo.getLatDouble();
            final double lon = geo.getLonDouble();
            final String countryCode = geo.getCountryCode();
            final String fullCityName = geo.getDisplayName(); // Use display_name for full context

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
                
                // If we have an AI key, we try to get an AI list, otherwise fallback.
                if (!RetrofitClient.GROQ_API_KEY.equals("YOUR_GROQ_API_KEY_HERE")) {
                    List<PackingItem> aiItems = getAiPackingList(fullCityName, weatherBox[0], countryBox[0]);
                    if (aiItems != null && !aiItems.isEmpty()) {
                        callback.onSuccess(aiItems, imageBox[0], fullCityName, lat, lon);
                        return;
                    }
                }

                List<PackingItem> items = buildList(weatherBox[0], countryBox[0]);
                callback.onSuccess(items, imageBox[0], fullCityName, lat, lon);
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
                if (countryCode == null || countryCode.isEmpty()) {
                    onEachComplete.run();
                    return;
                }
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
     * Calls Groq AI to generate a packing list.
     */
    @Nullable
    private List<PackingItem> getAiPackingList(String city, @Nullable WeatherResponse weather, @Nullable CountryResponse country) {
        try {
            StringBuilder prompt = new StringBuilder("Generate a practical packing list for a trip to " + city + ". ");
            if (weather != null && weather.getCurrentWeather() != null) {
                prompt.append("Current temperature is ").append(weather.getCurrentWeather().getTemperature()).append("°C. ");
            }
            if (country != null) {
                prompt.append("Destination language is ").append(country.getFirstLanguageName()).append(". ");
            }
            prompt.append("Respond only with a list of items, one per line, maximum 15 items. No introduction or extra text.");

            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt.toString());

            JsonArray messages = new JsonArray();
            messages.add(message);

            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("model", "llama3-8b-8192");
            bodyJson.add("messages", messages);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    bodyJson.toString()
            );

            Response<ResponseBody> response = RetrofitClient.ai()
                    .getPackingList("Bearer " + RetrofitClient.GROQ_API_KEY, body)
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();
                JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
                String content = root.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();

                List<PackingItem> items = new ArrayList<>();
                for (String line : content.split("\n")) {
                    String clean = line.replaceAll("^[-*\\d.]+\\s*", "").trim();
                    if (!clean.isEmpty()) {
                        items.add(new PackingItem(clean));
                    }
                }
                return items;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Pure function that turns API responses into a packing list.
     * Kept package-private for testability.
     */
    @NonNull
    List<PackingItem> buildList(@Nullable WeatherResponse weather,
                                @Nullable CountryResponse country) {
        List<PackingItem> list = new ArrayList<>();
        // Base items - Increased to at least 10 items
        list.add(new PackingItem("Paszport / Dokumenty"));
        list.add(new PackingItem("Portfel i gotówka"));
        list.add(new PackingItem("Telefon i ładowarka"));
        list.add(new PackingItem("Powerbank"));
        list.add(new PackingItem("Szczoteczka i pasta do zębów"));
        list.add(new PackingItem("Bielizna i skarpetki"));
        list.add(new PackingItem("Koszulki i spodnie"));
        list.add(new PackingItem("Wygodne buty"));
        list.add(new PackingItem("Podstawowa apteczka"));
        list.add(new PackingItem("Przybory toaletowe"));
        list.add(new PackingItem("Słuchawki"));

        if (weather != null && weather.getCurrentWeather() != null) {
            double temp = weather.getCurrentWeather().getTemperature();
            int code   = weather.getCurrentWeather().getWeatherCode();

            if (temp < 10) {
                list.add(new PackingItem("Ciepła kurtka"));
                list.add(new PackingItem("Czapka i rękawiczki"));
            } else if (temp < 20) {
                list.add(new PackingItem("Lekka kurtka / Bluza"));
            }
            
            if (temp > 25) {
                list.add(new PackingItem("Krem z filtrem UV"));
                list.add(new PackingItem("Okulary przeciwsłoneczne"));
                list.add(new PackingItem("Krótkie spodenki"));
            }

            // WMO weather codes: 51-67 = drizzle/rain, 80-82 = showers,
            // 95-99 = thunderstorm.  We treat the "rainy" family as umbrella-worthy.
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) {
                list.add(new PackingItem("Parasol / Płaszcz przeciwdeszczowy"));
            }
        }

        if (country != null) {
            list.add(new PackingItem("Gotówka: " + country.getFirstCurrencyCode()));
            list.add(new PackingItem("Słownik/Tłumacz: " + country.getFirstLanguageName()));
        }

        return Collections.unmodifiableList(list);
    }

    /** Repository callback delivered to the ViewModel. */
    public interface Callback {
        void onSuccess(@NonNull List<PackingItem> items,
                       @Nullable String imageUrl,
                       @NonNull String cityName,
                       double lat,
                       double lon);
        void onError(@NonNull String message);
    }
}
