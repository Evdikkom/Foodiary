package com.example.foodiary.presentation.util

import com.example.foodiary.domain.model.MealType

fun MealType.displayName(): String = when (this) {
    MealType.BREAKFAST -> "Завтрак"
    MealType.LUNCH -> "Обед"
    MealType.DINNER -> "Ужин"
    MealType.SNACK -> "Перекус"
    MealType.AFTERNOON_SNACK -> "Полдник"
    MealType.LATE_DINNER -> "Поздний ужин"
}

fun MealType.genitiveName(): String = when (this) {
    MealType.BREAKFAST -> "завтрака"
    MealType.LUNCH -> "обеда"
    MealType.DINNER -> "ужина"
    MealType.SNACK -> "перекуса"
    MealType.AFTERNOON_SNACK -> "полдника"
    MealType.LATE_DINNER -> "позднего ужина"
}

fun MealType.recommendationBucket(): MealType = when (this) {
    MealType.AFTERNOON_SNACK -> MealType.SNACK
    MealType.LATE_DINNER -> MealType.DINNER
    else -> this
}

fun MealType.defaultReminderHour(): Int = when (this) {
    MealType.BREAKFAST -> 8
    MealType.LUNCH -> 13
    MealType.DINNER -> 19
    MealType.SNACK -> 16
    MealType.AFTERNOON_SNACK -> 15
    MealType.LATE_DINNER -> 21
}

fun MealType.defaultReminderMinute(): Int = when (this) {
    MealType.BREAKFAST -> 0
    MealType.LUNCH -> 0
    MealType.DINNER -> 0
    MealType.SNACK -> 30
    MealType.AFTERNOON_SNACK -> 30
    MealType.LATE_DINNER -> 0
}

fun MealType.defaultDiaryTimestampHour(): Int = when (this) {
    MealType.BREAKFAST -> 8
    MealType.LUNCH -> 13
    MealType.DINNER -> 19
    MealType.SNACK -> 16
    MealType.AFTERNOON_SNACK -> 15
    MealType.LATE_DINNER -> 21
}

fun MealType.defaultDiaryTimestampMinute(): Int = when (this) {
    MealType.BREAKFAST -> 30
    MealType.LUNCH -> 0
    MealType.DINNER -> 0
    MealType.SNACK -> 30
    MealType.AFTERNOON_SNACK -> 30
    MealType.LATE_DINNER -> 0
}

fun primaryMealTypes(): List<MealType> = listOf(
    MealType.BREAKFAST,
    MealType.LUNCH,
    MealType.DINNER
)

fun configurableMealTypes(): List<MealType> = listOf(
    MealType.BREAKFAST,
    MealType.LUNCH,
    MealType.DINNER,
    MealType.SNACK,
    MealType.AFTERNOON_SNACK,
    MealType.LATE_DINNER
)
