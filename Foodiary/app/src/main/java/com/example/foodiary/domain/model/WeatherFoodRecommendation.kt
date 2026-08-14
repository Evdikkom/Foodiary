package com.example.foodiary.domain.model

enum class WeatherRecommendationAction {
    OPEN_FOOD,
    REQUEST_LOCATION,
    RETRY,
    DISMISS
}

enum class WeatherNutritionFocus {
    HEAT_HYDRATION,
    HEAT_ELECTROLYTES,
    LOW_SUNLIGHT_VITAMIN_D,
    COLD_WARM_ENERGY,
    RAINY_DAY_STABILITY,
    WIND_WARM_BALANCE
}

data class WeatherFoodRecommendation(
    val title: String,
    val headline: String,
    val message: String,
    val buttonText: String,
    val action: WeatherRecommendationAction,
    val focus: WeatherNutritionFocus? = null,
    val food: Food? = null
) {
    companion object {
        fun permissionRequired(): WeatherFoodRecommendation {
            return WeatherFoodRecommendation(
                title = "Погодные рекомендации",
                headline = "Включить погоду",
                message = "Разрешите местоположение, и Foodiary подберёт продукт под погоду за окном: жару, холод или долгую пасмурность.",
                buttonText = "Разрешить",
                action = WeatherRecommendationAction.REQUEST_LOCATION
            )
        }

        fun permissionDenied(): WeatherFoodRecommendation {
            return WeatherFoodRecommendation(
                title = "Погодные рекомендации",
                headline = "Геолокация выключена",
                message = "Без местоположения приложение не сможет понять погоду рядом с вами. Можно включить доступ позже в настройках Android.",
                buttonText = "Понятно",
                action = WeatherRecommendationAction.DISMISS
            )
        }

        fun locationUnavailable(): WeatherFoodRecommendation {
            return WeatherFoodRecommendation(
                title = "Погодные рекомендации",
                headline = "Не вижу место",
                message = "Не удалось получить координаты устройства. Проверьте, включена ли геолокация, и попробуйте ещё раз.",
                buttonText = "Повторить",
                action = WeatherRecommendationAction.RETRY
            )
        }

        fun weatherUnavailable(): WeatherFoodRecommendation {
            return WeatherFoodRecommendation(
                title = "Погодные рекомендации",
                headline = "Погода не загрузилась",
                message = "Сервис погоды сейчас не ответил. Рацион не меняю вслепую, можно повторить запрос позже.",
                buttonText = "Повторить",
                action = WeatherRecommendationAction.RETRY
            )
        }
    }
}
