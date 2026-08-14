package com.example.foodiary.data.model

/**
 * Категории физической активности для расчета суточной потребности в энергии.
 *
 * Эти уровни согласованы с официальными категориями physical activity level
 * из Dietary Reference Intakes for Energy (2023).
 */
enum class ActivityLevel {
    INACTIVE,
    LOW_ACTIVE,
    ACTIVE,
    VERY_ACTIVE
}
