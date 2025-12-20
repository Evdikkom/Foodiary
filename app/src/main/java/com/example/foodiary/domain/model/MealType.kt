package com.example.foodiary.domain.model

/**
 * MealType — тип приёма пищи.
 * Используется для классификации питания пользователя
 * в течение суток и для аналитических расчётов.
 */
enum class MealType {
    BREAKFAST,  // Завтрак
    LUNCH,      // Обед
    DINNER,     // Ужин
    SNACK       // Перекус
}
