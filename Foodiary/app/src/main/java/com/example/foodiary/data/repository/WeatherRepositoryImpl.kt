package com.example.foodiary.data.repository

import com.example.foodiary.data.remote.weather.OpenMeteoApi
import com.example.foodiary.data.remote.weather.OpenMeteoApiFactory
import com.example.foodiary.domain.model.WeatherDaySnapshot
import com.example.foodiary.domain.model.WeatherSnapshot

class WeatherRepositoryImpl(
    private val api: OpenMeteoApi = OpenMeteoApiFactory.create()
) {

    suspend fun getWeather(latitude: Double, longitude: Double): WeatherSnapshot {
        val response = api.getForecast(
            latitude = latitude,
            longitude = longitude
        )
        val current = response.current
        val daily = response.daily
        val dayCount = listOfNotNull(
            daily?.weatherCodes?.size,
            daily?.sunshineDurationSeconds?.size,
            daily?.precipitationSumMm?.size,
            daily?.uvIndexMax?.size
        ).maxOrNull() ?: 0

        return WeatherSnapshot(
            temperatureC = current?.temperatureC,
            apparentTemperatureC = current?.apparentTemperatureC,
            humidityPercent = current?.humidityPercent,
            precipitationMm = current?.precipitationMm,
            weatherCode = current?.weatherCode,
            windSpeedKmh = current?.windSpeedKmh,
            recentDays = (0 until dayCount).map { index ->
                WeatherDaySnapshot(
                    weatherCode = daily?.weatherCodes?.getOrNull(index),
                    sunshineDurationHours = daily
                        ?.sunshineDurationSeconds
                        ?.getOrNull(index)
                        ?.div(SECONDS_IN_HOUR),
                    precipitationMm = daily?.precipitationSumMm?.getOrNull(index),
                    uvIndexMax = daily?.uvIndexMax?.getOrNull(index)
                )
            }
        ).also { snapshot ->
            cachedSnapshot = CachedWeatherSnapshot(
                latitude = latitude,
                longitude = longitude,
                loadedAtMillis = System.currentTimeMillis(),
                snapshot = snapshot
            )
        }
    }

    fun getCachedWeather(
        latitude: Double,
        longitude: Double,
        maxAgeMillis: Long = CACHE_MAX_AGE_MS
    ): WeatherSnapshot? {
        val cached = cachedSnapshot ?: return null
        val isCloseEnough =
            kotlin.math.abs(cached.latitude - latitude) <= CACHE_COORDINATE_DELTA &&
                kotlin.math.abs(cached.longitude - longitude) <= CACHE_COORDINATE_DELTA
        val isFresh = System.currentTimeMillis() - cached.loadedAtMillis <= maxAgeMillis
        return if (isCloseEnough && isFresh) cached.snapshot else null
    }

    private companion object {
        const val SECONDS_IN_HOUR = 3600.0
        const val CACHE_MAX_AGE_MS = 30L * 60L * 1000L
        const val CACHE_COORDINATE_DELTA = 0.25

        @Volatile
        var cachedSnapshot: CachedWeatherSnapshot? = null
    }

    private data class CachedWeatherSnapshot(
        val latitude: Double,
        val longitude: Double,
        val loadedAtMillis: Long,
        val snapshot: WeatherSnapshot
    )
}
