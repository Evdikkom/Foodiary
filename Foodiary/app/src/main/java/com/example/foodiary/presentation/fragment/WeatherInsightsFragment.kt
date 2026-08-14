package com.example.foodiary.presentation.fragment

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.WeatherRepositoryImpl
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.WeatherFoodRecommendation
import com.example.foodiary.domain.model.WeatherRecommendationAction
import com.example.foodiary.domain.model.WeatherSnapshot
import com.example.foodiary.domain.usecase.GetWeatherFoodRecommendationUseCase
import com.example.foodiary.presentation.location.DeviceLocationProvider
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

class WeatherInsightsFragment : Fragment(R.layout.fragment_weather_insights) {

    companion object {
        private const val ARG_SELECTED_DAY_START = "arg_selected_day_start"

        fun newInstance(selectedDayStart: Long): WeatherInsightsFragment {
            return WeatherInsightsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SELECTED_DAY_START, selectedDayStart)
                }
            }
        }
    }

    private lateinit var locationProvider: DeviceLocationProvider
    private lateinit var weatherRepository: WeatherRepositoryImpl
    private lateinit var recommendationUseCase: GetWeatherFoodRecommendationUseCase
    private var selectedDayStart: Long = 0L
    private var currentRecommendation: WeatherFoodRecommendation? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        view?.let(::loadWeather)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedDayStart = arguments?.getLong(ARG_SELECTED_DAY_START)
            ?: System.currentTimeMillis()

        val database = AppDatabase.getInstance(requireContext())
        val foodRepository = FoodRepositoryImpl(database.foodDao())
        val allergenRepository = AllergenRepositoryImpl(
            allergenDao = database.allergenDao(),
            foodAllergenDao = database.foodAllergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
        locationProvider = DeviceLocationProvider(requireContext())
        weatherRepository = WeatherRepositoryImpl()
        recommendationUseCase = GetWeatherFoodRecommendationUseCase(
            foodRepository = foodRepository,
            allergenRepository = allergenRepository
        )

        view.findViewById<ImageView>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }
        view.findViewById<Button>(R.id.buttonWeatherAction).setDebouncedClickListener {
            handleAction(view)
        }

        loadWeather(view)
    }

    private fun loadWeather(root: View) {
        val progress = root.findViewById<ProgressBar>(R.id.progressWeather)
        val action = root.findViewById<Button>(R.id.buttonWeatherAction)
        progress.visibility = View.VISIBLE
        action.visibility = View.GONE
        currentRecommendation = null

        if (!locationProvider.hasLocationPermission()) {
            progress.visibility = View.GONE
            bindCurrentWeather(root, null)
            bindRecommendation(root, WeatherFoodRecommendation.permissionRequired())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val location = locationProvider.getCurrentOrLastKnownLocation()
            if (location == null) {
                progress.visibility = View.GONE
                bindCurrentWeather(root, null)
                bindRecommendation(root, WeatherFoodRecommendation.locationUnavailable())
                return@launch
            }

            val snapshot = loadWeatherSnapshotWithFallback(
                latitude = location.latitude,
                longitude = location.longitude
            )

            progress.visibility = View.GONE
            if (snapshot == null) {
                bindCurrentWeather(root, null)
                bindRecommendation(root, WeatherFoodRecommendation.weatherUnavailable())
                return@launch
            }

            bindCurrentWeather(root, snapshot)
            val recommendation = runCatching {
                recommendationUseCase(snapshot)
            }.getOrNull()

            if (recommendation == null) {
                bindNeutralWeather(root)
            } else {
                bindRecommendation(root, recommendation)
            }
        }
    }

    private fun bindCurrentWeather(root: View, snapshot: WeatherSnapshot?) {
        val values = root.findViewById<TextView>(R.id.textWeatherValues)
        val details = root.findViewById<TextView>(R.id.textWeatherDetails)

        if (snapshot == null) {
            values.text = "Нет данных"
            details.text = "Разрешите геолокацию или повторите загрузку, чтобы Foodiary смог получить погоду рядом с вами."
            return
        }

        val temperature = snapshot.temperatureC?.roundToInt()?.let { "$it°C" } ?: "—"
        val feelsLike = snapshot.apparentTemperatureC?.roundToInt()?.let { "$it°C" } ?: "—"
        val humidity = snapshot.humidityPercent?.let { "$it%" } ?: "—"
        val wind = snapshot.windSpeedKmh?.roundToInt()?.let { "$it км/ч" } ?: "—"
        val rain = snapshot.precipitationMm?.let { "${formatOneDecimal(it)} мм" } ?: "—"
        val lowSunDays = snapshot.recentDays.count { day ->
            (day.sunshineDurationHours != null && day.sunshineDurationHours < 2.0) ||
                (day.uvIndexMax != null && day.uvIndexMax < 2.0) ||
                (day.precipitationMm ?: 0.0) >= 1.0
        }

        values.text = "Температура $temperature, ощущается как $feelsLike"
        details.text = "Влажность: $humidity\nВетер: $wind\nОсадки сейчас: $rain\nПасмурных или дождливых дней за неделю: $lowSunDays"
    }

    private suspend fun loadWeatherSnapshotWithFallback(
        latitude: Double,
        longitude: Double
    ): WeatherSnapshot? {
        return runCatching {
            weatherRepository.getWeather(latitude, longitude)
        }.getOrNull()
            ?: weatherRepository.getCachedWeather(latitude, longitude)
            ?: run {
                delay(650L)
                runCatching {
                    weatherRepository.getWeather(latitude, longitude)
                }.getOrNull()
                    ?: weatherRepository.getCachedWeather(latitude, longitude)
            }
    }

    private fun bindNeutralWeather(root: View) {
        val neutralRecommendation = WeatherFoodRecommendation(
            title = "Погодный вывод",
            headline = "Погода сейчас нейтральная",
            message = "Foodiary не видит сильной жары, холода или длительного недостатка солнца. Сегодня лучше ориентироваться на обычные рекомендации по КБЖУ и привычкам.",
            buttonText = "Обновить",
            action = WeatherRecommendationAction.RETRY
        )
        bindRecommendation(root, neutralRecommendation)
    }

    private fun bindRecommendation(root: View, recommendation: WeatherFoodRecommendation) {
        currentRecommendation = recommendation
        root.findViewById<TextView>(R.id.textWeatherConclusionTitle).text = recommendation.title
        root.findViewById<TextView>(R.id.textWeatherConclusionHeadline).text = recommendation.headline
        root.findViewById<TextView>(R.id.textWeatherConclusionMessage).text = recommendation.message
        root.findViewById<Button>(R.id.buttonWeatherAction).apply {
            text = recommendation.buttonText
            visibility = View.VISIBLE
        }
    }

    private fun handleAction(root: View) {
        val recommendation = currentRecommendation ?: return
        when (recommendation.action) {
            WeatherRecommendationAction.OPEN_FOOD -> {
                val food = recommendation.food ?: return
                replaceFragmentSafely(
                    ProductConfigFragment.newInstance(
                        mealType = resolveCurrentMealType(),
                        foodId = food.id,
                        targetDayStartTimestamp = selectedDayStart
                    )
                )
            }

            WeatherRecommendationAction.REQUEST_LOCATION -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            WeatherRecommendationAction.RETRY -> loadWeather(root)
            WeatherRecommendationAction.DISMISS -> popBackStackSafely()
        }
    }

    private fun resolveCurrentMealType(): MealType {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> MealType.BREAKFAST
            in 11..15 -> MealType.LUNCH
            in 16..17 -> MealType.SNACK
            else -> MealType.DINNER
        }
    }

    private fun formatOneDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }
}
