package com.example.travelplanner.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.travelplanner.data.model.CountryResponse;
import com.example.travelplanner.data.model.GeocodingResponse;
import com.example.travelplanner.data.model.PackingItem;
import com.example.travelplanner.data.model.PixabayResponse;
import com.example.travelplanner.data.model.WikipediaResponse;
import com.example.travelplanner.data.model.WeatherResponse;
import com.example.travelplanner.data.remote.RetrofitClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import retrofit2.Call;
import retrofit2.Response;

public class PackingRepository {

    public void buildPackingList(@NonNull String cityName,
                                 @NonNull Callback callback) {

        // Cale zadanie pakujemy w jeden wątek w tle, aby zapobiec NetworkOnMainThreadException
        new Thread(() -> {
            double lat = 0;
            double lon = 0;
            String countryCode = "";
            String fullCityName = cityName;

            // ---------- Step 1: Geocoding synchroniczny ----------
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
                e.printStackTrace(); // Brak internetu / błąd DNS
            }

            // Fallback
            if (lat == 0 && lon == 0) {
                double[] coords = getFallbackCoordinates(cityName);
                lat = coords[0];
                lon = coords[1];
            }

            final double finalLat = lat;
            final double finalLon = lon;
            final String finalCountryCode = countryCode;
            final String finalFullCityName = fullCityName;

            // ---------- Step 2: Równoległe zapytania ----------
            final WeatherResponse[] weatherBox = new WeatherResponse[1];
            final CountryResponse[] countryBox = new CountryResponse[1];
            final String[] imageBox = new String[1];

            // CountDownLatch pozwoli zablokować główny wątek w tle, dopóki 3 podwątki nie skończą pracy
            CountDownLatch latch = new CountDownLatch(3);

            // a) Weather
            new Thread(() -> {
                try {
                    Response<WeatherResponse> w = RetrofitClient.weather()
                            .getCurrentWeather(finalLat, finalLon, true)
                            .execute();
                    if (w.isSuccessful() && w.body() != null) {
                        weatherBox[0] = w.body();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "weather-call").start();

            // b) Country
            new Thread(() -> {
                try {
                    if (finalCountryCode != null && !finalCountryCode.isEmpty()) {
                        Response<List<CountryResponse>> c = RetrofitClient.country()
                                .getCountryByCode(finalCountryCode)
                                .execute();
                        if (c.isSuccessful() && c.body() != null && !c.body().isEmpty()) {
                            countryBox[0] = c.body().get(0);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "country-call").start();

            // c) Image (Pixabay -> Wikipedia fallback)
            new Thread(() -> {
                try {
                    String cleanGeoTitle = finalFullCityName.split(",")[0].trim();
                    String rawTitle = cityName.split(",")[0].trim();

                    // 1. Pixabay (oczyszczona nazwa)
                    imageBox[0] = fetchPixabayImage(cleanGeoTitle);

                    // 2. Pixabay (surowa nazwa)
                    if (imageBox[0] == null) {
                        imageBox[0] = fetchPixabayImage(rawTitle);
                    }

                    // 3. Fallback: Polska Wikipedia (oczyszczona nazwa)
                    if (imageBox[0] == null) {
                        imageBox[0] = fetchWikipediaImage(cleanGeoTitle, "pl");
                    }

                    // 4. Fallback: Polska Wikipedia (surowa nazwa)
                    if (imageBox[0] == null) {
                        imageBox[0] = fetchWikipediaImage(rawTitle, "pl");
                    }

                    // 5. Fallback: Angielska Wikipedia (oczyszczona nazwa)
                    if (imageBox[0] == null) {
                        imageBox[0] = fetchWikipediaImage(cleanGeoTitle, "en");
                    }

                    // 6. Fallback: Angielska Wikipedia (surowa nazwa)
                    if (imageBox[0] == null) {
                        imageBox[0] = fetchWikipediaImage(rawTitle, "en");
                    }
                } catch (Exception e) {
                    Log.e("PackingRepository", "Error in image-call thread", e);
                } finally {
                    latch.countDown();
                }
            }, "image-call").start();

            // ---------- Step 3: Oczekiwanie na zakończenie i powrót na wątek główny ----------
            try {
                latch.await(); // Czeka aż licznik spadnie do 0
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            List<PackingItem> items = buildList(weatherBox[0], countryBox[0]);

            // Aktualizacja UI MUSI odbyć się na głównym wątku (Main Looper)
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onSuccess(items, imageBox[0], finalFullCityName, finalLat, finalLon)
            );

        }, "packing-repository-main").start();
    }

    @Nullable
    private String fetchPixabayImage(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        try {
            Response<PixabayResponse> response = RetrofitClient.pixabay()
                    .searchImage(RetrofitClient.PIXABAY_API_KEY, query.trim(), "photo", "horizontal")
                    .execute();
            if (response.isSuccessful() && response.body() != null) {
                String imgUrl = response.body().getFirstImageUrl();
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    Log.d("PackingRepository", "Found Pixabay image for [" + query + "]: " + imgUrl);
                    return imgUrl;
                }
            } else {
                Log.w("PackingRepository", "Pixabay search failed for [" + query + "]: code=" + response.code());
            }
        } catch (Exception e) {
            Log.e("PackingRepository", "Error fetching Pixabay image for [" + query + "]", e);
        }
        return null;
    }

    @Nullable
    private String fetchWikipediaImage(String title, String lang) {
        if (title == null || title.trim().isEmpty()) return null;
        try {
            String cleanTitle = title.trim().replace(" ", "_");
            String encodedTitle = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                    .replace("+", "%20");

            String url = "https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + encodedTitle;
            Response<WikipediaResponse> response = RetrofitClient.wikipedia()
                    .getSummaryByUrl(url)
                    .execute();
            if (response.isSuccessful() && response.body() != null) {
                String imgUrl = response.body().getImageUrl();
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    Log.d("PackingRepository", "Found Wikipedia image for [" + title + "] (" + lang + "): " + imgUrl);
                    return imgUrl;
                }
            } else {
                Log.w("PackingRepository", "Wikipedia summary call failed for [" + title + "] (" + lang + "): code=" + response.code());
            }
        } catch (Exception e) {
            Log.e("PackingRepository", "Error fetching Wikipedia image for [" + title + "] (" + lang + ")", e);
        }
        return null;
    }

    private double[] getFallbackCoordinates(String city) {
        if (city == null) return new double[]{52.2297, 21.0122};
        String lower = city.toLowerCase().trim();

        // Zastąpiono contains() przez equalsIgnoreCase() dla większego bezpieczeństwa
        if (lower.equalsIgnoreCase("paryż") || lower.equalsIgnoreCase("paris")) return new double[]{48.8566, 2.3522};
        if (lower.equalsIgnoreCase("rzym") || lower.equalsIgnoreCase("rome")) return new double[]{41.9028, 12.4964};
        if (lower.equalsIgnoreCase("tokio") || lower.equalsIgnoreCase("tokyo")) return new double[]{35.6762, 139.6503};
        if (lower.equalsIgnoreCase("londyn") || lower.equalsIgnoreCase("london")) return new double[]{51.5074, -0.1278};
        if (lower.equalsIgnoreCase("nowy jork") || lower.equalsIgnoreCase("new york")) return new double[]{40.7128, -74.0060};
        if (lower.equalsIgnoreCase("kraków") || lower.equalsIgnoreCase("krakow")) return new double[]{50.0647, 19.9450};
        if (lower.equalsIgnoreCase("warszawa") || lower.equalsIgnoreCase("warsaw")) return new double[]{52.2297, 21.0122};
        if (lower.equalsIgnoreCase("berlin")) return new double[]{52.5200, 13.4050};
        if (lower.equalsIgnoreCase("barcelona")) return new double[]{41.3851, 2.1734};
        if (lower.equalsIgnoreCase("madryt") || lower.equalsIgnoreCase("madrid")) return new double[]{40.4168, -3.7038};
        if (lower.equalsIgnoreCase("kalisz")) return new double[]{51.7608, 18.0869};

        return new double[]{52.2297, 21.0122}; // Domyślnie Warszawa
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