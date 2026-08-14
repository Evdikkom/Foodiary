package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.WeatherDaySnapshot
import com.example.foodiary.domain.model.WeatherNutritionFocus
import com.example.foodiary.domain.model.WeatherRecommendationAction
import com.example.foodiary.domain.model.WeatherSnapshot
import com.example.foodiary.testing.FakeAllergenRepository
import com.example.foodiary.testing.FakeFoodRepository
import com.example.foodiary.testing.food
import com.example.foodiary.testing.manualConflict
import com.example.foodiary.testing.safetyProfileWithWarning
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetWeatherFoodRecommendationUseCaseTest {

    @Test
    fun `hot humid weather recommends electrolyte oriented food`() = runBlocking {
        val useCase = buildUseCase(
            foods = listOf(
                food(id = "cucumber", name = "Огурец", caloriesPer100g = 15.0, category = "vegetable"),
                food(id = "banana", name = "Банан", caloriesPer100g = 89.0, carbsPer100g = 22.8, category = "fruit"),
                food(id = "rice", name = "Рис", caloriesPer100g = 130.0, carbsPer100g = 28.0, category = "grain")
            )
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 31.0,
                humidityPercent = 74,
                precipitationMm = 0.0,
                weatherCode = 1,
                windSpeedKmh = 8.0
            )
        )

        assertEquals(WeatherNutritionFocus.HEAT_ELECTROLYTES, result?.focus)
        assertEquals("banana", result?.food?.id)
        assertEquals(WeatherRecommendationAction.OPEN_FOOD, result?.action)
        assertTrue(result?.message.orEmpty().contains("электролит", ignoreCase = true))
    }

    @Test
    fun `apparent temperature can trigger electrolyte recommendation even when air temperature is lower`() = runBlocking {
        val useCase = buildUseCase(
            foods = listOf(
                food(id = "banana", name = "Банан", caloriesPer100g = 89.0, carbsPer100g = 22.8, category = "fruit"),
                food(id = "wholegrain_bread", name = "Хлеб цельнозерновой", caloriesPer100g = 247.0, carbsPer100g = 41.0, category = "grain")
            )
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 24.0,
                apparentTemperatureC = 32.0,
                humidityPercent = 65,
                precipitationMm = 0.0,
                weatherCode = 1,
                windSpeedKmh = 6.0
            )
        )

        assertEquals(WeatherNutritionFocus.HEAT_ELECTROLYTES, result?.focus)
        assertEquals("banana", result?.food?.id)
    }

    @Test
    fun `many low sun days recommend vitamin D source`() = runBlocking {
        val useCase = buildUseCase(
            foods = listOf(
                food(id = "rice", name = "Рис", category = "grain"),
                food(id = "salmon", name = "Лосось", proteinPer100g = 20.0, fatPer100g = 13.0, category = "protein"),
                food(id = "apple", name = "Яблоко", category = "fruit")
            )
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 14.0,
                humidityPercent = 80,
                precipitationMm = 0.0,
                weatherCode = 3,
                windSpeedKmh = 10.0,
                recentDays = List(5) {
                    WeatherDaySnapshot(
                        weatherCode = 61,
                        sunshineDurationHours = 1.2,
                        precipitationMm = 2.0
                    )
                }
            )
        )

        assertEquals(WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D, result?.focus)
        assertEquals("salmon", result?.food?.id)
        assertTrue(result?.message.orEmpty().contains("витамин D"))
    }

    @Test
    fun `many low uv days recommend vitamin D source even without rain data`() = runBlocking {
        val useCase = buildUseCase(
            foods = listOf(
                food(id = "salmon", name = "Лосось", proteinPer100g = 20.0, fatPer100g = 13.0, category = "protein"),
                food(id = "oatmeal", name = "Овсянка", carbsPer100g = 61.8, category = "grain")
            )
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 12.0,
                humidityPercent = 72,
                precipitationMm = 0.0,
                weatherCode = 3,
                windSpeedKmh = 11.0,
                recentDays = List(4) {
                    WeatherDaySnapshot(
                        weatherCode = 3,
                        sunshineDurationHours = null,
                        precipitationMm = 0.0,
                        uvIndexMax = 1.1
                    )
                }
            )
        )

        assertEquals(WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D, result?.focus)
        assertEquals("salmon", result?.food?.id)
    }

    @Test
    fun `single rainy day recommends stable meal instead of vitamin D`() = runBlocking {
        val useCase = buildUseCase(
            foods = listOf(
                food(id = "rice", name = "Рис", caloriesPer100g = 130.0, carbsPer100g = 28.0, category = "grain"),
                food(id = "orange", name = "Апельсин", caloriesPer100g = 47.0, carbsPer100g = 11.8, category = "fruit")
            )
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 15.0,
                humidityPercent = 88,
                precipitationMm = 0.4,
                weatherCode = 61,
                windSpeedKmh = 12.0,
                recentDays = listOf(
                    WeatherDaySnapshot(
                        weatherCode = 61,
                        sunshineDurationHours = 3.5,
                        precipitationMm = 2.0,
                        uvIndexMax = 3.0
                    )
                )
            )
        )

        assertEquals(WeatherNutritionFocus.RAINY_DAY_STABILITY, result?.focus)
        assertEquals("rice", result?.food?.id)
    }

    @Test
    fun `normal weather stays silent`() = runBlocking {
        val useCase = buildUseCase(
            foods = listOf(food(id = "apple", name = "Яблоко", category = "fruit"))
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 20.0,
                humidityPercent = 50,
                precipitationMm = 0.0,
                weatherCode = 1,
                windSpeedKmh = 8.0
            )
        )

        assertNull(result)
    }

    @Test
    fun `confirmed allergen conflict removes weather candidate`() = runBlocking {
        val allergenRepository = FakeAllergenRepository().apply {
            profilesByFoodId = mapOf(
                "salmon" to safetyProfileWithWarning(manualConflict("fish"))
            )
        }
        val useCase = buildUseCase(
            foods = listOf(
                food(id = "salmon", name = "Лосось", proteinPer100g = 20.0, fatPer100g = 13.0, category = "protein"),
                food(id = "egg", name = "Яйцо куриное", proteinPer100g = 12.7, fatPer100g = 11.5, category = "protein")
            ),
            allergenRepository = allergenRepository
        )

        val result = useCase(
            WeatherSnapshot(
                temperatureC = 12.0,
                humidityPercent = 82,
                precipitationMm = 0.0,
                weatherCode = 3,
                windSpeedKmh = 9.0,
                recentDays = List(4) {
                    WeatherDaySnapshot(
                        weatherCode = 61,
                        sunshineDurationHours = 0.8,
                        precipitationMm = 2.5
                    )
                }
            )
        )

        assertEquals(WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D, result?.focus)
        assertEquals("egg", result?.food?.id)
    }

    private fun buildUseCase(
        foods: List<com.example.foodiary.domain.model.Food>,
        allergenRepository: FakeAllergenRepository = FakeAllergenRepository()
    ): GetWeatherFoodRecommendationUseCase {
        return GetWeatherFoodRecommendationUseCase(
            foodRepository = FakeFoodRepository(foods),
            allergenRepository = allergenRepository
        )
    }
}
