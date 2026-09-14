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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class PackingRepository {

    public void buildPackingList(@NonNull String cityName,
                                 @NonNull Callback callback) {
        double lat = 0;
        double lon = 0;
        String countryCode = "";
        String fullCityName = cityName;

        // ---------- Step 1: Geocoding z obsługą trybu Offline / Błędów DNS ----------
        try {
            Call<List<GeocodingResponse>> geoCall =
                    RetrofitClient.geocoding()
                            .searchCity(cityName, "jsonv2", 1);
            Response<List<GeocodingResponse>> geoResp = geoCall.execute();
            if (geoResp.isSuccessful() && geoResp.body() != null && !geoResp.body().isEmpty()) {
                GeocodingResponse geo = geoResp.body().get(0);
                lat = geo.getLatDouble();
                lon = geo.getLonDouble();
                countryCode = geo.getCountryCode();
                fullCityName = geo.getDisplayName();
            }
        } catch (Exception e) {
            // Geokodowanie online nie powiodło się (brak połączenia/DNS). Ignorujemy i używamy fallback.
        }

        // Jeśli geokodowanie online zawiodło, stosujemy zapasowe koordynaty (fallback)
        if (lat == 0 && lon == 0) {
            double[] coords = getFallbackCoordinates(cityName);
            lat = coords[0];
            lon = coords[1];
        }

        final double finalLat = lat;
        final double finalLon = lon;
        final String finalCountryCode = countryCode;
        final String finalFullCityName = fullCityName;

        // ---------- Step 2: równoległe zapytania ----------
        final WeatherResponse[] weatherBox = new WeatherResponse[1];
        final CountryResponse[] countryBox = new CountryResponse[1];
        final String[] imageBox = new String[1];
        final AtomicInteger remaining = new AtomicInteger(3);

        Runnable onEachComplete = () -> {
            if (remaining.decrementAndGet() != 0) return;

            // Zawsze generujemy listę pakowania (nawet w trybie offline/fallback)
            List<PackingItem> items = buildList(weatherBox[0], countryBox[0]);
            callback.onSuccess(items, imageBox[0], finalFullCityName, finalLat, finalLon);
        };

        // a) Weather
        new Thread(() -> {
            try {
                Response<WeatherResponse> w = RetrofitClient.weather()
                        .getCurrentWeather(finalLat, finalLon, true)
                        .execute();
                if (w.isSuccessful() && w.body() != null) {
                    weatherBox[0] = w.body();
                }
            } catch (Exception ignored) {
            } finally {
                onEachComplete.run();
            }
        }, "weather-call").start();

        // b) Country
        new Thread(() -> {
            if (finalCountryCode == null || finalCountryCode.isEmpty()) {
                onEachComplete.run();
                return;
            }
            try {
                Response<List<CountryResponse>> c = RetrofitClient.country()
                        .getCountryByCode(finalCountryCode)
                        .execute();
                if (c.isSuccessful() && c.body() != null && !c.body().isEmpty()) {
                    countryBox[0] = c.body().get(0);
                }
            } catch (Exception ignored) {
            } finally {
                onEachComplete.run();
            }
        }, "country-call").start();

        // c) Image z Wikipedia API z pełną normalizacją polskich i zagranicznych nazw miast
        new Thread(() -> {
            try {
                String cleanGeoTitle = finalFullCityName.split(",")[0].trim().replace(" ", "_");
                String rawTitle = cityName.split(",")[0].trim().replace(" ", "_");
                
                // 1. Polska Wikipedia ze skorygowaną polską nazwą (np. "Paryż" gdy wpisano "Paryz")
                String plUrl = "https://pl.wikipedia.org/api/rest_v1/page/summary/" + cleanGeoTitle;
                Response<WikipediaResponse> wikiPl = RetrofitClient.wikipedia()
                        .getSummaryByUrl(plUrl)
                        .execute();
                if (wikiPl.isSuccessful() && wikiPl.body() != null && wikiPl.body().getImageUrl() != null) {
                    imageBox[0] = wikiPl.body().getImageUrl();
                } else {
                    // 2. Polska Wikipedia z wpisaną nazwą
                    String plRawUrl = "https://pl.wikipedia.org/api/rest_v1/page/summary/" + rawTitle;
                    Response<WikipediaResponse> wikiPlRaw = RetrofitClient.wikipedia()
                            .getSummaryByUrl(plRawUrl)
                            .execute();
                    if (wikiPlRaw.isSuccessful() && wikiPlRaw.body() != null && wikiPlRaw.body().getImageUrl() != null) {
                        imageBox[0] = wikiPlRaw.body().getImageUrl();
                    } else {
                        // 3. Angielska Wikipedia fallback (np. "Paris" lub "Rome")
                        String enUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + rawTitle;
                        Response<WikipediaResponse> wikiEn = RetrofitClient.wikipedia()
                                .getSummaryByUrl(enUrl)
                                .execute();
                        if (wikiEn.isSuccessful() && wikiEn.body() != null && wikiEn.body().getImageUrl() != null) {
                            imageBox[0] = wikiEn.body().getImageUrl();
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                onEachComplete.run();
            }
        }, "image-call").start();
    }

    private double[] getFallbackCoordinates(String city) {
        if (city == null) return new double[]{52.2297, 21.0122};
        String lower = city.toLowerCase().trim();
        if (lower.contains("paryż") || lower.contains("paris")) return new double[]{48.8566, 2.3522};
        if (lower.contains("rzym") || lower.contains("rome")) return new double[]{41.9028, 12.4964};
        if (lower.contains("tokio") || lower.contains("tokyo")) return new double[]{35.6762, 139.6503};
        if (lower.contains("londyn") || lower.contains("london")) return new double[]{51.5074, -0.1278};
        if (lower.contains("nowy jork") || lower.contains("new york")) return new double[]{40.7128, -74.0060};
        if (lower.contains("kraków") || lower.contains("krakow")) return new double[]{50.0647, 19.9450};
        if (lower.contains("warszawa") || lower.contains("warsaw")) return new double[]{52.2297, 21.0122};
        if (lower.contains("berlin")) return new double[]{52.5200, 13.4050};
        if (lower.contains("barcelona")) return new double[]{41.3851, 2.1734};
        if (lower.contains("madryt") || lower.contains("madrid")) return new double[]{40.4168, -3.7038};
        if (lower.contains("kalisz")) return new double[]{51.7608, 18.0869};
        return new double[]{52.2297, 21.0122}; // Domyślnie Warszawa
    }

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

    public interface Callback {
        void onSuccess(@NonNull List<PackingItem> items,
                       @Nullable String imageUrl,
                       @NonNull String cityName,
                       double lat,
                       double lon);
        void onError(@NonNull String message);
    }
}
