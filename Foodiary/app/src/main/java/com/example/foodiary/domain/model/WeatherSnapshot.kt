package com.example.foodiary.domain.model

data class WeatherSnapshot(
    val temperatureC: Double?,
    val apparentTemperatureC: Double? = null,
    val humidityPercent: Int?,
    val precipitationMm: Double?,
    val weatherCode: Int?,
    val windSpeedKmh: Double?,
    val recentDays: List<WeatherDaySnapshot> = emptyList()
)

data class WeatherDaySnapshot(
    val weatherCode: Int?,
    val sunshineDurationHours: Double?,
    val precipitationMm: Double?,
    val uvIndexMax: Double? = null
)
