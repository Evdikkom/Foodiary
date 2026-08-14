package com.example.foodiary.domain.model

import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal

data class User(
    val id: String = "current_user",
    val biologicalSex: BiologicalSex,
    val age: Int,
    val weightKg: Double,
    val heightCm: Int,
    val bodyFatPercent: Double? = null,
    val goal: UserGoal,
    val activityLevel: ActivityLevel,
    val restrictions: List<UserRestriction> = emptyList(),
)
