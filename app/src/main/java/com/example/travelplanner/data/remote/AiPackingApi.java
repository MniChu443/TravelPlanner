package com.example.travelplanner.data.remote;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Interface for Groq AI API (OpenAI compatible).
 * Docs: https://console.groq.com/docs/openai
 */
public interface AiPackingApi {

    @POST("chat/completions")
    Call<ResponseBody> getPackingList(
            @Header("Authorization") String bearerToken,
            @Body RequestBody body
    );
}
