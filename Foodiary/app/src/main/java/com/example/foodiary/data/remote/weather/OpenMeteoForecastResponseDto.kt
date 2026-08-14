package com.example.foodiary.data.remote.weather

import com.google.gson.annotations.SerializedName

data class OpenMeteoForecastResponseDto(
    @SerializedName("current")
    val current: OpenMeteoCurrentWeatherDto?,
    @SerializedName("daily")
    val daily: OpenMeteoDailyWeatherDto?
)

data class OpenMeteoCurrentWeatherDto(
    @SerializedName("temperature_2m")
    val temperatureC: Double?,
    @SerializedName("apparent_temperature")
    val apparentTemperatureC: Double?,
    @SerializedName("relative_humidity_2m")
    val humidityPercent: Int?,
    @SerializedName("precipitation")
    val precipitationMm: Double?,
    @SerializedName("weather_code")
    val weatherCode: Int?,
    @SerializedName("wind_speed_10m")
    val windSpeedKmh: Double?
)

data class OpenMeteoDailyWeatherDto(
    @SerializedName("time")
    val time: List<String>?,
    @SerializedName("weather_code")
    val weatherCodes: List<Int?>?,
    @SerializedName("sunshine_duration")
    val sunshineDurationSeconds: List<Double?>?,
    @SerializedName("precipitation_sum")
    val precipitationSumMm: List<Double?>?,
    @SerializedName("uv_index_max")
    val uvIndexMax: List<Double?>?
)
