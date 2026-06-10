package com.example.travelplanner.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.travelplanner.data.local.AppDatabase;
import com.example.travelplanner.data.local.PackingDao;
import com.example.travelplanner.data.model.CountryResponse;
import com.example.travelplanner.data.model.GeocodingResponse;
import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PixabayResponse;
import com.example.travelplanner.data.model.SearchHistory;
import com.example.travelplanner.data.model.WeatherResponse;
import com.example.travelplanner.data.remote.RetrofitClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Single point of contact for the ViewModel.
 * Coordinates the chained calls: Geocoding → (Weather, Country, Image) in parallel.
 */
public class PackingRepository {

    private final PackingDao packingDao;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public PackingRepository(Context context) {
        this.packingDao = AppDatabase.getInstance(context).packingDao();
    }

    /**
     * Builds a packing list, checking local DB first.
     */
    public void buildPackingList(@NonNull String cityName, @Nullable String travelDate, @NonNull Callback callback) {
        executor.execute(() -> {
            // 1. Check Local DB
            List<PackingItem> cached = packingDao.getItemsForCity(cityName);
            
            // 2. Fetch Geocoding (always needed for coordinates and full city name)
            try {
                Call<List<GeocodingResponse>> geoCall =
                        RetrofitClient.geocoding().searchCity(cityName, "jsonv2", 1, 1, "pl");
                Response<List<GeocodingResponse>> geoResp = geoCall.execute();
                
                if (!geoResp.isSuccessful() || geoResp.body() == null || geoResp.body().isEmpty()) {
                    callback.onError("City not found: " + cityName);
                    return;
                }
                
                GeocodingResponse geo = geoResp.body().get(0);
                final double lat = geo.getLatDouble();
                final double lon = geo.getLonDouble();
                final String countryCode = geo.getCountryCode();
                final String fullCityName = geo.getCityOnly();

                // If cached exists, we use it but still might want to refresh weather for the specific date
                fetchMetadataAndItems(fullCityName, lat, lon, countryCode, travelDate, cached, callback);

            } catch (IOException e) {
                callback.onError("Geocoding failed: " + e.getMessage());
            }
        });
    }

    private void fetchMetadataAndSuccess(String cityName, List<PackingItem> items, Callback callback) {
        // Implementation for fetching just metadata (image, lat, lon) if items are cached
        // For now, let's keep it simple and just return items if cached, assuming caller handles UI.
        // In a real app, we'd store cityName, image, lat, lon in a separate 'Trip' entity.
        callback.onSuccess(items, null, cityName, 0, 0);
    }

    private void fetchMetadataAndItems(String fullCityName, double lat, double lon, String countryCode, String travelDate, List<PackingItem> cached, Callback callback) {
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

            List<PackingItem> items;
            if (cached != null && !cached.isEmpty()) {
                items = cached;
            } else {
                items = null;
                String apiKey = RetrofitClient.GROQ_API_KEY;
                if (apiKey != null && !apiKey.isEmpty() && !apiKey.startsWith("YOUR_")) {
                    items = getAiPackingList(fullCityName, weatherBox[0], countryBox[0]);
                }
                
                if (items == null || items.isEmpty()) {
                    items = buildList(weatherBox[0], countryBox[0]);
                }

                // Save new items to Local DB
                for (PackingItem item : items) {
                    item.setCityName(fullCityName);
                }
                packingDao.insertAll(items);
            }

            callback.onSuccess(items, imageBox[0], fullCityName, lat, lon);
        };

        executor.execute(() -> fetchWeather(lat, lon, travelDate, weatherBox, firstError, onEachComplete));
        executor.execute(() -> fetchCountry(countryCode, countryBox, onEachComplete));
        executor.execute(() -> fetchImage(fullCityName, imageBox, onEachComplete));
    }

    private void fetchWeather(double lat, double lon, String travelDate, WeatherResponse[] box, String[] error, Runnable onComplete) {
        try {
            Call<WeatherResponse> call;
            if (travelDate != null) {
                // If date is provided, we fetch forecast for that specific day
                call = RetrofitClient.weather().getCurrentWeather(lat, lon, true, travelDate, travelDate);
            } else {
                call = RetrofitClient.weather().getCurrentWeather(lat, lon, true, null, null);
            }
            Response<WeatherResponse> w = call.execute();
            if (w.isSuccessful()) box[0] = w.body();
            else if (error[0] == null) error[0] = "Weather failed";
        } catch (IOException e) {
            if (error[0] == null) error[0] = e.getMessage();
        } finally { onComplete.run(); }
    }

    private void fetchCountry(String code, CountryResponse[] box, Runnable onComplete) {
        if (code == null || code.isEmpty()) {
            onComplete.run();
            return;
        }
        try {
            Response<List<CountryResponse>> c = RetrofitClient.country().getCountryByCode(code).execute();
            if (c.isSuccessful() && c.body() != null && !c.body().isEmpty()) {
                box[0] = c.body().get(0);
            }
        } catch (IOException ignored) { }
        finally { onComplete.run(); }
    }

    private void fetchImage(String city, String[] box, Runnable onComplete) {
        try {
            Response<PixabayResponse> p = RetrofitClient.pixabay()
                    .searchImage(RetrofitClient.PIXABAY_API_KEY, city, "photo").execute();
            if (p.isSuccessful() && p.body() != null && p.body().getHits() != null && !p.body().getHits().isEmpty()) {
                box[0] = p.body().getHits().get(0).getLargeImageURL();
            }
        } catch (Exception ignored) { }
        finally { onComplete.run(); }
    }

    public void updateItem(PackingItem item) {
        executor.execute(() -> packingDao.updateItem(item));
    }

    public void deleteForCity(String cityName) {
        executor.execute(() -> packingDao.deleteAllForCity(cityName));
    }

    public void deleteSearch(String city) {
        executor.execute(() -> packingDao.deleteSearch(city));
    }

    public void saveSearch(String city) {
        executor.execute(() -> {
            packingDao.deleteSearch(city);
            packingDao.insertSearch(new SearchHistory(city));
        });
    }

    public void loadSearchHistory(OnHistoryLoadedCallback callback) {
        executor.execute(() -> {
            List<String> history = packingDao.getSearchHistory();
            callback.onLoaded(history);
        });
    }

    public interface OnHistoryLoadedCallback {
        void onLoaded(List<String> history);
    }

    @Nullable
    private List<PackingItem> getAiPackingList(String city, @Nullable WeatherResponse weather, @Nullable CountryResponse country) {
        try {
            StringBuilder prompt = new StringBuilder("Generate a practical packing list for a trip to " + city + " (use Polish language). ");
            if (weather != null && weather.getCurrentWeather() != null) {
                prompt.append("Current temperature is ").append(weather.getCurrentWeather().getTemperature()).append("°C. ");
            }
            if (country != null) {
                prompt.append("Destination language is ").append(country.getFirstLanguageName()).append(". ");
            }
            prompt.append("Respond only with a list of items, in Polish, one per line, maximum 15 items. No introduction or extra text.");

            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt.toString());

            JsonArray messages = new JsonArray();
            messages.add(message);

            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("model", "llama3-8b-8192");
            bodyJson.add("messages", messages);

            RequestBody body = RequestBody.create(
                    bodyJson.toString(),
                    MediaType.parse("application/json")
            );

            Response<ResponseBody> response = RetrofitClient.ai()
                    .getPackingList("Bearer " + RetrofitClient.GROQ_API_KEY, body).execute();

            if (response.isSuccessful() && response.body() != null) {
                try (ResponseBody responseBody = response.body()) {
                    String content = JsonParser.parseString(responseBody.string())
                            .getAsJsonObject().getAsJsonArray("choices")
                            .get(0).getAsJsonObject().getAsJsonObject("message")
                            .get("content").getAsString();

                    List<PackingItem> items = new ArrayList<>();
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    
                    for (String line : content.split("\n")) {
                        String clean = line.replaceAll("^[-*\\d.]+\\s*", "").trim();
                        if (!clean.isEmpty()) {
                            String lower = clean.toLowerCase();
                            if (!seen.contains(lower)) {
                                items.add(new PackingItem(clean));
                                seen.add(lower);
                            }
                        }
                    }
                    return items;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    @NonNull
    List<PackingItem> buildList(@Nullable WeatherResponse weather, @Nullable CountryResponse country) {
        List<PackingItem> list = new ArrayList<>();
        list.add(new PackingItem("Paszport / Dokumenty"));
        list.add(new PackingItem("Portfel i gotówka"));
        list.add(new PackingItem("Telefon i ładowarka"));
        list.add(new PackingItem("Powerbank"));
        list.add(new PackingItem("Szczoteczka i pasta do zębów"));
        list.add(new PackingItem("Bielizna i skarpetki"));
        list.add(new PackingItem("Koszulki i spodnie"));
        list.add(new PackingItem("Wygodne buty"));
        list.add(new PackingItem("Podstawowa apteczka"));
        list.add(new PackingItem("Słuchawki"));

        if (weather != null && weather.getCurrentWeather() != null) {
            double temp = weather.getCurrentWeather().getTemperature();
            int code   = weather.getCurrentWeather().getWeatherCode();
            if (temp < 15) list.add(new PackingItem("Ciepła bluza / Kurtka"));
            if (temp > 25) {
                list.add(new PackingItem("Krem z filtrem UV"));
                list.add(new PackingItem("Okulary przeciwsłoneczne"));
            }
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) {
                list.add(new PackingItem("Parasol / Płaszcz przeciwdeszczowy"));
            }
        }
        return list;
    }

    public interface Callback {
        void onSuccess(@NonNull List<PackingItem> items, @Nullable String imageUrl, @NonNull String cityName, double lat, double lon);
        void onError(@NonNull String message);
    }
}
