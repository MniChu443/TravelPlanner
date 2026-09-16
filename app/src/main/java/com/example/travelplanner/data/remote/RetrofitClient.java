package com.example.travelplanner.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    public static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/";
    public static final String OPEN_METEO_BASE_URL = "https://api.open-meteo.com/";
    public static final String RESTCOUNTRIES_BASE_URL = "https://restcountries.com/";
    public static final String WIKIPEDIA_BASE_URL = "https://pl.wikipedia.org/";
    public static final String PIXABAY_BASE_URL = "https://pixabay.com/";
    public static final String PIXABAY_API_KEY = "57592526-c55db115b21";

    private static final Retrofit NOMINATIM;
    private static final Retrofit OPEN_METEO;
    private static final Retrofit REST_COUNTRIES;
    private static final Retrofit WIKIPEDIA;
    private static final Retrofit PIXABAY;

    static {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("User-Agent", "TravelPlannerApp/1.0 (android-app-support@travelplanner.com)")
                                .header("Referer", "https://www.openstreetmap.org/")
                                .build()
                ))
                .addInterceptor(new HttpLoggingInterceptor()
                        .setLevel(HttpLoggingInterceptor.Level.BASIC))
                .build();

        NOMINATIM = new Retrofit.Builder()
                .baseUrl(NOMINATIM_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        OPEN_METEO = new Retrofit.Builder()
                .baseUrl(OPEN_METEO_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        REST_COUNTRIES = new Retrofit.Builder()
                .baseUrl(RESTCOUNTRIES_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WIKIPEDIA = new Retrofit.Builder()
                .baseUrl(WIKIPEDIA_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        PIXABAY = new Retrofit.Builder()
                .baseUrl(PIXABAY_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static GeocodingApi geocoding() {
        return NOMINATIM.create(GeocodingApi.class);
    }

    public static WeatherApi weather() {
        return OPEN_METEO.create(WeatherApi.class);
    }

    public static CountryApi country() {
        return REST_COUNTRIES.create(CountryApi.class);
    }

    public static WikipediaApi wikipedia() {
        return WIKIPEDIA.create(WikipediaApi.class);
    }

    public static PixabayApi pixabay() {
        return PIXABAY.create(PixabayApi.class);
    }

    private RetrofitClient() { /* no instances */ }
}
