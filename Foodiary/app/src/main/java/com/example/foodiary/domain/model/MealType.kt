package com.example.foodiary.domain.model

/**
 * Тип приёма пищи внутри Foodiary.
 *
 * Основные типы используются на главном экране дневника по умолчанию,
 * а дополнительные могут включаться пользователем в настройках структуры дня.
 */
enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    AFTERNOON_SNACK,
    LATE_DINNER
}
