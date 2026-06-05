package com.example.travelplanner.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Holds the three Retrofit instances and one inner class for Pixabay.
 * Singleton – one OkHttp client is shared for connection pooling.
 */
public final class RetrofitClient {

    // Base URLs for the free, key-less APIs.
    public static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/";
    public static final String OPEN_METEO_BASE_URL = "https://api.open-meteo.com/";
    public static final String RESTCOUNTRIES_BASE_URL = "https://restcountries.com/";
    public static final String PIXABAY_BASE_URL = "https://pixabay.com/";

    /**
     * 👉 Replace this with your own free Pixabay key from
     *    https://pixabay.com/api/docs/ before running the app.
     *    Without a real key, the image call will fail and the header
     *    ImageView will fall back to the placeholder drawable.
     */
    public static final String PIXABAY_API_KEY = "YOUR_API_KEY_HERE";

    private static final Retrofit NOMINATIM;
    private static final Retrofit OPEN_METEO;
    private static final Retrofit REST_COUNTRIES;
    private static final Retrofit PIXABAY;

    static {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

    public static PixabayApi pixabay() {
        return PIXABAY.create(PixabayApi.class);
    }

    private RetrofitClient() { /* no instances */ }
}
