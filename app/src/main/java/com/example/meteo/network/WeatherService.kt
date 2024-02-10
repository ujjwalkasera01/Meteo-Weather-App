package com.example.meteo.network

import com.example.meteo.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// TODO (STEP 4: Create a WeatherService interface)
// START
/**
 * An Interface which defines the HTTP operations Functions.
 */
interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?
    ): Call<WeatherResponse>
}
// END