package com.example.travelplanner.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.travelplanner.data.model.CountryResponse;
import com.example.travelplanner.data.model.GeocodingResponse;
import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.WikipediaResponse;
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

            // c) Image z Wikipedia API (Darmowe, bez klucza API)
            new Thread(() -> {
                try {
                    String searchTitle = cityName.split(",")[0].trim();
                    Response<WikipediaResponse> wiki = RetrofitClient.wikipedia()
                            .getSummary(searchTitle)
                            .execute();
                    if (wiki.isSuccessful() && wiki.body() != null) {
                        imageBox[0] = wiki.body().getImageUrl();
                    }
                } catch (Exception e) {
                    // Ciche obsłużenie błędu - w razie niepowodzenia pojawi się obraz zastępczy
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
        
        // --- Dokumenty i Finanse ---
        list.add(new PackingItem("Paszport / Dowód osobisty"));
        list.add(new PackingItem("Bilety na podróż (lot/pociąg)"));
        list.add(new PackingItem("Karty płatnicze i gotówka"));
        list.add(new PackingItem("Ubezpieczenie turystyczne"));
        
        // --- Elektronika ---
        list.add(new PackingItem("Smartfon"));
        list.add(new PackingItem("Ładowarka sieciowa (sprawdź przejściówki)"));
        list.add(new PackingItem("Powerbank z kablem"));
        list.add(new PackingItem("Słuchawki wygłuszające"));
        list.add(new PackingItem("Czytnik e-booków / Tablet"));
        
        // --- Odzież (Baza) ---
        list.add(new PackingItem("Bielizna (po jednej na każdy dzień + zapas)"));
        list.add(new PackingItem("Skarpetki (zwykłe i ciepłe)"));
        list.add(new PackingItem("Wygodne buty do chodzenia"));
        list.add(new PackingItem("Spodnie / Jeansy"));
        list.add(new PackingItem("T-shirty / Koszule"));
        list.add(new PackingItem("Klapki (pod prysznic/na basen)"));
        list.add(new PackingItem("Piżama / Ubranie do spania"));
        
        // --- Kosmetyczki i Zdrowie ---
        list.add(new PackingItem("Szczoteczka i pasta do zębów"));
        list.add(new PackingItem("Żel pod prysznic / Szampon (format podróżny)"));
        list.add(new PackingItem("Dezodorant / Perfumy"));
        list.add(new PackingItem("Podstawowa apteczka (leki przeciwbólowe, plastry)"));
        list.add(new PackingItem("Chusteczki higieniczne i nawilżane"));
        list.add(new PackingItem("Krem nawilżający"));

        // --- Zależne od pogody ---
        if (weather != null && weather.getCurrentWeather() != null) {
            double temp = weather.getCurrentWeather().getTemperature();
            int code   = weather.getCurrentWeather().getWeatherCode();

            if (temp < 12) {
                list.add(new PackingItem("Ciepła kurtka jesienno-zimowa"));
                list.add(new PackingItem("Czapka, szalik i grube rękawiczki"));
                list.add(new PackingItem("Sweter / Ciepły polar"));
                list.add(new PackingItem("Kalesony / Odzież termiczna"));
            } else if (temp < 20) {
                list.add(new PackingItem("Lekka kurtka / Wiatrówka"));
                list.add(new PackingItem("Bluza lub rozpinany sweter"));
            }
            
            if (temp > 22) {
                list.add(new PackingItem("Krem z wysokim filtrem UV"));
                list.add(new PackingItem("Okulary przeciwsłoneczne (z filtrem)"));
                list.add(new PackingItem("Krótkie spodenki / Szorty"));
                list.add(new PackingItem("Strój kąpielowy / Kąpielówki"));
                list.add(new PackingItem("Ręcznik szybkoschnący"));
                list.add(new PackingItem("Nakrycie głowy (kapelusz/czapka z daszkiem)"));
            }

            // WMO weather codes: 51-67 = drizzle/rain, 80-82 = showers,
            // 95-99 = thunderstorm.  We treat the "rainy" family as umbrella-worthy.
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) {
                list.add(new PackingItem("Parasol / Płaszcz przeciwdeszczowy"));
                list.add(new PackingItem("Nieprzemakalne obuwie"));
            }
        }

        // --- Zależne od kraju ---
        if (country != null) {
            list.add(new PackingItem("Lokalna waluta: " + country.getFirstCurrencyCode()));
            list.add(new PackingItem("Aplikacja do tłumaczeń (Język: " + country.getFirstLanguageName() + ")"));
        }

        // --- Przydatne akcesoria ---
        list.add(new PackingItem("Butelka filtrująca na wodę"));
        list.add(new PackingItem("Kłódka do walizki/szafki"));
        list.add(new PackingItem("Zatyczki do uszu i opaska na oczy"));

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
