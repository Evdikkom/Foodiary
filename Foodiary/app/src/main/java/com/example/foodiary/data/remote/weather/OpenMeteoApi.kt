package com.example.foodiary.data.remote.weather

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("past_days") pastDays: Int = 7,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoForecastResponseDto

    companion object {
        const val CURRENT_FIELDS = "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m"
        const val DAILY_FIELDS = "weather_code,sunshine_duration,precipitation_sum,uv_index_max"
    }
}
