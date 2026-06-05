package com.example.travelplanner.data.remote;

import com.example.travelplanner.data.model.PixabayResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Pixabay image search API.
 * Docs: https://pixabay.com/api/docs/
 */
public interface PixabayApi {

    @GET("api/")
    Call<PixabayResponse> searchImage(
            @Query("key") String apiKey,
            @Query("q") String query,
            @Query("image_type") String imageType   // "photo"
    );
}
