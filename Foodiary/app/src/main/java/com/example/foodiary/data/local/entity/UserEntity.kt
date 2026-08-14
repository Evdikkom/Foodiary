package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String = "current_user",
    val biologicalSex: BiologicalSex = BiologicalSex.FEMALE,
    val age: Int = 25,
    val weightKg: Double = 70.0,
    val heightCm: Int = 175,
    val bodyFatPercent: Double? = null,
    val goal: UserGoal = UserGoal.MAINTAIN_WEIGHT,
    val activityLevel: ActivityLevel = ActivityLevel.LOW_ACTIVE
)
