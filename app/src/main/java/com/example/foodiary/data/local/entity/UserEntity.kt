package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.UserGoal

/**
 * UserEntity — сущность базы данных,
 * представляющая профиль пользователя Foodiary.
 */
@Entity(tableName = "users")
data class UserEntity(

    @PrimaryKey
    val id: String = "current_user",

    val name: String = "",

    val age: Int = 25,

    val weight: Double = 70.0,

    val height: Int = 175,

    // Цель пользователя (похудение, поддержание, набор)
    val goal: UserGoal = UserGoal.MAINTAIN_WEIGHT,

    // Уровень физической активности
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,

    // Рассчитанная суточная норма калорий
    val dailyCalorieGoal: Int = 2000,

    // Целевые значения БЖУ
    val proteinGoal: Int = 100,
    val fatGoal: Int = 70,
    val carbsGoal: Int = 250
)
