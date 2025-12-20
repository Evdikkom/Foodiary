package com.example.foodiary.data.model

/**
 * UserGoal — целевая установка пользователя в системе Foodiary.
 * Используется для определения логики аналитических рекомендаций
 * и расчёта суточных норм питания.
 */
enum class UserGoal {
    WEIGHT_LOSS,        // Снижение веса
    MAINTAIN_WEIGHT,    // Поддержание текущего веса
    WEIGHT_GAIN         // Набор массы
}
