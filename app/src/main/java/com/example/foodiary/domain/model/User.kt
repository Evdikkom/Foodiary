package com.example.foodiary.domain.model

import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.UserGoal

/**
 * User — доменная модель пользователя информационной системы Foodiary.
 * Класс описывает персональные характеристики пользователя,
 * необходимые для анализа питания и формирования рекомендаций.
 */
data class User(
    val name: String,
    val age: Int,
    val weight: Double,
    val height: Int,
    val goal: UserGoal,
    val activityLevel: ActivityLevel,
    val dailyCalorieGoal: Int,
    val proteinGoal: Int,
    val fatGoal: Int,
    val carbsGoal: Int
)
