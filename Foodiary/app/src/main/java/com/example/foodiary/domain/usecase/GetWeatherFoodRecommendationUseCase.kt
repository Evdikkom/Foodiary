package com.example.foodiary.domain.usecase

import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.WeatherFoodRecommendation
import com.example.foodiary.domain.model.WeatherNutritionFocus
import com.example.foodiary.domain.model.WeatherRecommendationAction
import com.example.foodiary.domain.model.WeatherSnapshot
import com.example.foodiary.domain.repository.AllergenRepository
import com.example.foodiary.domain.repository.FoodRepository
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class GetWeatherFoodRecommendationUseCase(
    private val foodRepository: FoodRepository,
    private val allergenRepository: AllergenRepository
) {

    suspend operator fun invoke(snapshot: WeatherSnapshot): WeatherFoodRecommendation? {
        val focus = resolveFocus(snapshot) ?: return null
        val candidateFoods = foodRepository.getFoodsForRecommendationPool(CANDIDATE_LIMIT)
        if (candidateFoods.isEmpty()) return buildRecommendation(focus, snapshot, food = null)

        val safetyProfiles = allergenRepository.getFoodSafetyProfiles(candidateFoods)
        val safeFoods = candidateFoods.filterNot { food ->
            val profile = safetyProfiles[food.id] ?: return@filterNot false
            (profile.highRiskConflicts + profile.warningConflicts)
                .any { it.evidenceType != AllergenEvidenceType.NAME_MATCH_INFERRED }
        }

        val bestFood = safeFoods
            .map { food ->
                val inferredPenalty = safetyProfiles[food.id]?.let { profile ->
                    if (
                        (profile.highRiskConflicts + profile.warningConflicts)
                            .any { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }
                    ) {
                        INFERRED_ALLERGEN_PENALTY
                    } else {
                        0
                    }
                } ?: 0
                food to (scoreFood(food, focus) - inferredPenalty)
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Food, Int>> { it.second }
                    .thenBy { it.first.name.lowercase(Locale.getDefault()) }
            )
            .firstOrNull()
            ?.first

        return buildRecommendation(focus, snapshot, bestFood)
    }

    private fun resolveFocus(snapshot: WeatherSnapshot): WeatherNutritionFocus? {
        val airTemperature = snapshot.temperatureC
        val effectiveTemperature = snapshot.apparentTemperatureC ?: airTemperature
        val humidity = snapshot.humidityPercent
        val currentCode = snapshot.weatherCode
        val currentPrecipitation = snapshot.precipitationMm ?: 0.0
        val windSpeed = snapshot.windSpeedKmh ?: 0.0

        val recentLowUvDays = snapshot.recentDays.count { day ->
            val uvIndex = day.uvIndexMax
            uvIndex != null && uvIndex < LOW_UV_INDEX
        }
        val recentLowSunDays = snapshot.recentDays.count { day ->
            val sunshineHours = day.sunshineDurationHours
            sunshineHours != null && sunshineHours < LOW_SUN_HOURS_PER_DAY
        }
        val recentRainyDays = snapshot.recentDays.count { day ->
            (day.precipitationMm ?: 0.0) >= RAINY_DAY_MM ||
                isRainCode(day.weatherCode) ||
                isSnowCode(day.weatherCode)
        }
        val hasLongLowSunPeriod =
            recentLowUvDays >= LOW_SUN_DAYS_THRESHOLD ||
                recentLowSunDays >= LOW_SUN_DAYS_THRESHOLD ||
                recentRainyDays >= LOW_SUN_DAYS_THRESHOLD
        val isCurrentWet =
            currentPrecipitation >= CURRENT_RAIN_MM ||
                isRainCode(currentCode) ||
                isSnowCode(currentCode)

        return when {
            effectiveTemperature != null && effectiveTemperature >= STRONG_HEAT_FEELS_LIKE_C ->
                WeatherNutritionFocus.HEAT_ELECTROLYTES

            airTemperature != null && airTemperature >= STRONG_HEAT_AIR_C ->
                WeatherNutritionFocus.HEAT_ELECTROLYTES

            airTemperature != null && humidity != null &&
                airTemperature >= HUMID_HEAT_C && humidity >= HIGH_HUMIDITY_PERCENT ->
                WeatherNutritionFocus.HEAT_ELECTROLYTES

            effectiveTemperature != null && effectiveTemperature >= WARM_FEELS_LIKE_C ->
                WeatherNutritionFocus.HEAT_HYDRATION

            hasLongLowSunPeriod ->
                WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D

            effectiveTemperature != null && effectiveTemperature <= COLD_FEELS_LIKE_C ->
                WeatherNutritionFocus.COLD_WARM_ENERGY

            windSpeed >= WINDY_KMH && (effectiveTemperature == null || effectiveTemperature <= COOL_WIND_FEELS_LIKE_C) ->
                WeatherNutritionFocus.WIND_WARM_BALANCE

            isCurrentWet ->
                WeatherNutritionFocus.RAINY_DAY_STABILITY

            else -> null
        }
    }

    private fun scoreFood(food: Food, focus: WeatherNutritionFocus): Int {
        val name = food.name.lowercase(Locale.getDefault())
        val category = food.category.lowercase(Locale.getDefault())
        val rule = rulesByFocus.getValue(focus)
        val keywordMatches = rule.keywords.count { keyword -> name.contains(keyword) }
        val categoryMatches = rule.preferredCategories.count { preferred -> category.contains(preferred) }

        var score = 0
        score += preferredRankBonus(food.id, rule.preferredIds)
        score += (categoryMatches * CATEGORY_MATCH_SCORE).coerceAtMost(MAX_CATEGORY_SCORE)
        score += (keywordMatches * KEYWORD_MATCH_SCORE).coerceAtMost(MAX_KEYWORD_SCORE)
        score += nutritionBonus(food, focus)

        if (focus == WeatherNutritionFocus.HEAT_HYDRATION || focus == WeatherNutritionFocus.HEAT_ELECTROLYTES) {
            if (food.caloriesPer100g >= HEAT_HEAVY_CALORIES) score -= HEAT_HEAVY_FOOD_PENALTY
            if (food.fatPer100g >= HEAT_HEAVY_FAT_GRAMS) score -= HEAT_HIGH_FAT_PENALTY
        }

        return score.coerceIn(0, 100)
    }

    private fun preferredRankBonus(foodId: String, preferredIds: List<String>): Int {
        val index = preferredIds.indexOf(foodId)
        if (index < 0) return 0
        return (PREFERRED_BASE_SCORE - index * PREFERRED_STEP_PENALTY)
            .coerceAtLeast(PREFERRED_MIN_SCORE)
    }

    private fun nutritionBonus(food: Food, focus: WeatherNutritionFocus): Int {
        return when (focus) {
            WeatherNutritionFocus.HEAT_HYDRATION -> {
                val lightBonus = inverseNormalize(food.caloriesPer100g, 35.0, 160.0) * 22
                val lowFatBonus = inverseNormalize(food.fatPer100g, 0.0, 8.0) * 10
                val waterRichNameBonus = if (isWaterRichName(food.name)) 18 else 0
                (lightBonus + lowFatBonus + waterRichNameBonus).roundToInt()
            }

            WeatherNutritionFocus.HEAT_ELECTROLYTES -> {
                val moderateEnergyBonus = bellScore(food.caloriesPer100g, center = 110.0, radius = 140.0) * 14
                val carbBonus = normalize(food.carbsPer100g, 8.0, 28.0) * 10
                val potassiumNameBonus = if (isElectrolyteName(food.name)) 20 else 0
                val dairyProteinBonus = if (food.category == "dairy" && food.proteinPer100g >= 6.0) 10 else 0
                (moderateEnergyBonus + carbBonus + potassiumNameBonus + dairyProteinBonus).roundToInt()
            }

            WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D -> {
                val vitaminDNameBonus = if (isVitaminDName(food.name)) 32 else 0
                val proteinBonus = normalize(food.proteinPer100g, 8.0, 24.0) * 10
                val fatSolubleBonus = normalize(food.fatPer100g, 4.0, 14.0) * 8
                (vitaminDNameBonus + proteinBonus + fatSolubleBonus).roundToInt()
            }

            WeatherNutritionFocus.COLD_WARM_ENERGY,
            WeatherNutritionFocus.RAINY_DAY_STABILITY,
            WeatherNutritionFocus.WIND_WARM_BALANCE -> {
                val proteinBonus = normalize(food.proteinPer100g, 6.0, 24.0) * 12
                val carbsBonus = normalize(food.carbsPer100g, 12.0, 38.0) * 12
                val steadyEnergyBonus = bellScore(food.caloriesPer100g, center = 180.0, radius = 170.0) * 12
                val warmMealNameBonus = if (isWarmMealBaseName(food.name)) 16 else 0
                (proteinBonus + carbsBonus + steadyEnergyBonus + warmMealNameBonus).roundToInt()
            }
        }
    }

    private fun buildRecommendation(
        focus: WeatherNutritionFocus,
        snapshot: WeatherSnapshot,
        food: Food?
    ): WeatherFoodRecommendation {
        val weatherPrefix = weatherPrefix(snapshot)
        val foodName = food?.name

        val message = when (focus) {
            WeatherNutritionFocus.HEAT_HYDRATION ->
                weatherPrefix + if (foodName != null) {
                    "В тёплую погоду лучше выбирать лёгкие продукты с высокой долей воды. $foodName хорошо дополняет обычную воду и не перегружает рацион."
                } else {
                    "В тёплую погоду лучше выбирать лёгкие продукты с высокой долей воды: огурец, помидор, апельсин, ягоды или арбуз."
                }

            WeatherNutritionFocus.HEAT_ELECTROLYTES ->
                weatherPrefix + if (foodName != null) {
                    "Когда жарко и влажно, вместе с водой важны электролиты: калий и другие минералы. $foodName подходит как мягкая поддержка после потоотделения."
                } else {
                    "Когда жарко и влажно, вместе с водой полезны продукты с электролитами: банан, картофель, йогурт, овощи."
                }

            WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D ->
                if (foodName != null) {
                    "Последние дни выглядят пасмурными или дождливыми. $foodName относится к продуктам, которые помогают поддержать витамин D в рационе."
                } else {
                    "Последние дни выглядят пасмурными или дождливыми. Обратите внимание на лосось, тунец, яйца, сыр или обогащённые продукты."
                }

            WeatherNutritionFocus.COLD_WARM_ENERGY ->
                weatherPrefix + if (foodName != null) {
                    "В холод легче держать режим с тёплой сытной едой. $foodName поможет добавить энергии и не пропустить нормальный приём пищи."
                } else {
                    "В холод легче держать режим с тёплой сытной едой: каши, крупы, картофель, рыба, птица или яйца."
                }

            WeatherNutritionFocus.RAINY_DAY_STABILITY ->
                if (foodName != null) {
                    "Дождливая погода часто сбивает режим и тянет к случайным перекусам. $foodName поможет собрать более стабильный приём пищи."
                } else {
                    "Дождливая погода часто сбивает режим. Выбирайте тёплые крупы, белковые продукты и овощи вместо случайных перекусов."
                }

            WeatherNutritionFocus.WIND_WARM_BALANCE ->
                weatherPrefix + if (foodName != null) {
                    "В ветреную прохладную погоду лучше заходят тёплые и устойчивые блюда. $foodName может быть хорошей основой."
                } else {
                    "В ветреную прохладную погоду хорошо заходят тёплые блюда: крупы, картофель, рыба, птица или яйца."
                }
        }

        return WeatherFoodRecommendation(
            title = "Погодная рекомендация",
            headline = foodName ?: focus.fallbackHeadline(),
            message = message,
            buttonText = if (food != null) "Добавить продукт" else "Понятно",
            action = if (food != null) {
                WeatherRecommendationAction.OPEN_FOOD
            } else {
                WeatherRecommendationAction.DISMISS
            },
            focus = focus,
            food = food
        )
    }

    private fun weatherPrefix(snapshot: WeatherSnapshot): String {
        val airTemperature = snapshot.temperatureC?.roundToInt()
        val apparentTemperature = snapshot.apparentTemperatureC?.roundToInt()
        return when {
            airTemperature != null &&
                apparentTemperature != null &&
                abs(apparentTemperature - airTemperature) >= 2 ->
                "Сейчас $airTemperature°C, ощущается как $apparentTemperature°C. "

            airTemperature != null ->
                "Сейчас около $airTemperature°C. "

            else -> ""
        }
    }

    private fun WeatherNutritionFocus.fallbackHeadline(): String {
        return when (this) {
            WeatherNutritionFocus.HEAT_HYDRATION -> "Лёгкие продукты и вода"
            WeatherNutritionFocus.HEAT_ELECTROLYTES -> "Вода и электролиты"
            WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D -> "Поддержать витамин D"
            WeatherNutritionFocus.COLD_WARM_ENERGY -> "Тёплая сытная еда"
            WeatherNutritionFocus.RAINY_DAY_STABILITY -> "Стабильный приём пищи"
            WeatherNutritionFocus.WIND_WARM_BALANCE -> "Тёплая основа дня"
        }
    }

    private fun isRainCode(code: Int?): Boolean {
        return code != null && (code in 51..67 || code in 80..82 || code == 95 || code in 96..99)
    }

    private fun isSnowCode(code: Int?): Boolean {
        return code != null && (code in 71..77 || code in 85..86)
    }

    private fun isWaterRichName(name: String): Boolean {
        val normalized = name.lowercase(Locale.getDefault())
        return waterRichKeywords.any(normalized::contains)
    }

    private fun isElectrolyteName(name: String): Boolean {
        val normalized = name.lowercase(Locale.getDefault())
        return electrolyteKeywords.any(normalized::contains)
    }

    private fun isVitaminDName(name: String): Boolean {
        val normalized = name.lowercase(Locale.getDefault())
        return vitaminDKeywords.any(normalized::contains)
    }

    private fun isWarmMealBaseName(name: String): Boolean {
        val normalized = name.lowercase(Locale.getDefault())
        return warmMealKeywords.any(normalized::contains)
    }

    private fun normalize(
        value: Double,
        min: Double,
        max: Double
    ): Double {
        if (max <= min) return 0.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun inverseNormalize(
        value: Double,
        min: Double,
        max: Double
    ): Double {
        return 1.0 - normalize(value, min, max)
    }

    private fun bellScore(
        value: Double,
        center: Double,
        radius: Double
    ): Double {
        if (radius <= 0.0) return 0.0
        return (1.0 - abs(value - center) / radius).coerceIn(0.0, 1.0)
    }

    private data class FocusRule(
        val preferredIds: List<String>,
        val preferredCategories: Set<String>,
        val keywords: Set<String>
    )

    private companion object {
        const val CANDIDATE_LIMIT = 160
        const val INFERRED_ALLERGEN_PENALTY = 18

        const val WARM_FEELS_LIKE_C = 26.0
        const val HUMID_HEAT_C = 27.0
        const val STRONG_HEAT_AIR_C = 30.0
        const val STRONG_HEAT_FEELS_LIKE_C = 31.0
        const val HIGH_HUMIDITY_PERCENT = 70
        const val COLD_FEELS_LIKE_C = 7.0
        const val COOL_WIND_FEELS_LIKE_C = 16.0
        const val WINDY_KMH = 35.0
        const val CURRENT_RAIN_MM = 0.2
        const val RAINY_DAY_MM = 1.0
        const val LOW_SUN_HOURS_PER_DAY = 2.0
        const val LOW_UV_INDEX = 2.0
        const val LOW_SUN_DAYS_THRESHOLD = 4

        const val PREFERRED_BASE_SCORE = 72
        const val PREFERRED_STEP_PENALTY = 5
        const val PREFERRED_MIN_SCORE = 38
        const val CATEGORY_MATCH_SCORE = 18
        const val MAX_CATEGORY_SCORE = 28
        const val KEYWORD_MATCH_SCORE = 18
        const val MAX_KEYWORD_SCORE = 54
        const val HEAT_HEAVY_CALORIES = 350.0
        const val HEAT_HEAVY_FOOD_PENALTY = 24
        const val HEAT_HEAVY_FAT_GRAMS = 24.0
        const val HEAT_HIGH_FAT_PENALTY = 18

        val waterRichKeywords = setOf(
            "огур", "cucumber", "помид", "томат", "tomato", "апельс", "orange",
            "мандарин", "mandarin", "грейпфрут", "grapefruit", "яблок", "apple",
            "груш", "pear", "ягод", "berries", "арбуз", "watermelon", "дын", "melon",
            "киви", "kiwi", "клубник", "strawberry", "голубик", "blueberry",
            "малин", "raspberry", "персик", "peach", "ананас", "pineapple",
            "салат", "lettuce", "кабач", "zucchini"
        )

        val electrolyteKeywords = setOf(
            "банан", "banana", "картоф", "potato", "йогурт", "yogurt", "yoghurt",
            "кефир", "kefir", "творог", "cottage", "апельс", "orange", "авокад", "avocado",
            "шпинат", "spinach", "фасол", "beans", "чечев", "lentil", "минд", "almond",
            "нут", "chickpea", "эдамаме", "edamame", "тыквен", "pumpkin seed",
            "томат", "tomato", "помид"
        )

        val vitaminDKeywords = setOf(
            "лосос", "salmon", "тунец", "tuna", "скумбр", "mackerel", "сардин", "sardine",
            "сельд", "herring", "форел", "trout", "треск", "cod", "яй", "egg",
            "желт", "yolk", "сыр", "cheese", "молок", "milk", "йогурт", "yogurt",
            "скир", "skyr", "обогащ", "fortified", "гриб", "mushroom", "витамин d"
        )

        val warmMealKeywords = setOf(
            "овсян", "oat", "греч", "buckwheat", "рис", "rice", "картоф", "potato",
            "батат", "sweet potato", "киноа", "quinoa", "булгур", "bulgur",
            "чечев", "lentil", "нут", "chickpea", "фасол", "beans",
            "паста", "pasta", "макарон", "хлеб", "bread", "куриц", "chicken",
            "индей", "turkey", "рыб", "fish", "лосос", "salmon", "яй", "egg",
            "суп", "soup", "рагу", "stew", "каша", "porridge"
        )

        val rulesByFocus = mapOf(
            WeatherNutritionFocus.HEAT_HYDRATION to FocusRule(
                preferredIds = listOf(
                    "watermelon",
                    "cucumber",
                    "tomato",
                    "melon",
                    "strawberries",
                    "orange",
                    "grapefruit",
                    "berries_mix",
                    "pear",
                    "apple",
                    "zucchini",
                    "lettuce"
                ),
                preferredCategories = setOf("fruit", "vegetable"),
                keywords = waterRichKeywords
            ),
            WeatherNutritionFocus.HEAT_ELECTROLYTES to FocusRule(
                preferredIds = listOf(
                    "banana",
                    "potato",
                    "avocado",
                    "spinach",
                    "beans_red",
                    "chickpeas",
                    "lentils",
                    "greek_yogurt",
                    "kefir_2_5",
                    "orange",
                    "tomato",
                    "almonds",
                    "pumpkin_seeds"
                ),
                preferredCategories = setOf("fruit", "vegetable", "dairy", "grain", "nuts", "protein"),
                keywords = electrolyteKeywords
            ),
            WeatherNutritionFocus.LOW_SUNLIGHT_VITAMIN_D to FocusRule(
                preferredIds = listOf(
                    "salmon",
                    "sardines",
                    "mackerel",
                    "herring",
                    "trout",
                    "tuna",
                    "egg",
                    "cheese",
                    "milk_2_5",
                    "greek_yogurt",
                    "mushrooms"
                ),
                preferredCategories = setOf("protein", "dairy"),
                keywords = vitaminDKeywords
            ),
            WeatherNutritionFocus.COLD_WARM_ENERGY to FocusRule(
                preferredIds = listOf(
                    "oat_porridge",
                    "oatmeal",
                    "buckwheat",
                    "rice",
                    "brown_rice",
                    "potato",
                    "sweet_potato",
                    "lentils",
                    "chickpeas",
                    "wholegrain_bread",
                    "chicken_breast",
                    "turkey_fillet",
                    "salmon",
                    "egg"
                ),
                preferredCategories = setOf("grain", "protein"),
                keywords = warmMealKeywords
            ),
            WeatherNutritionFocus.RAINY_DAY_STABILITY to FocusRule(
                preferredIds = listOf(
                    "oat_porridge",
                    "buckwheat",
                    "rice",
                    "quinoa",
                    "bulgur",
                    "potato",
                    "chicken_breast",
                    "turkey_fillet",
                    "lentils",
                    "egg",
                    "broccoli",
                    "mushrooms"
                ),
                preferredCategories = setOf("grain", "protein", "vegetable"),
                keywords = warmMealKeywords
            ),
            WeatherNutritionFocus.WIND_WARM_BALANCE to FocusRule(
                preferredIds = listOf(
                    "buckwheat",
                    "oat_porridge",
                    "oatmeal",
                    "potato",
                    "sweet_potato",
                    "turkey_fillet",
                    "chicken_breast",
                    "salmon",
                    "egg",
                    "wholegrain_bread",
                    "lentils"
                ),
                preferredCategories = setOf("grain", "protein"),
                keywords = warmMealKeywords
            )
        )
    }
}
