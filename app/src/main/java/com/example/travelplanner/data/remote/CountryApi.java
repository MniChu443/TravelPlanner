package com.example.travelplanner.data.remote;

import com.example.travelplanner.data.model.CountryResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * REST Countries API.
 * Docs: https://restcountries.com/
 */
public interface CountryApi {

    @GET("v3.1/alpha/{code}")
    Call<List<CountryResponse>> getCountryByCode(
            @Path("code") String countryCode
    );
}
