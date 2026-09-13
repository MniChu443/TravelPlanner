package com.example.travelplanner.data.remote;

import com.example.travelplanner.data.model.WikipediaResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface WikipediaApi {
    @GET("api/rest_v1/page/summary/{title}")
    Call<WikipediaResponse> getSummary(@Path("title") String title);
}
