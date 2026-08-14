package com.example.foodiary.data.mapper

import com.example.foodiary.data.local.entity.UserEntity
import com.example.foodiary.domain.model.User

fun UserEntity.toDomain(): User {
    return User(
        id = id,
        biologicalSex = biologicalSex,
        age = age,
        weightKg = weightKg,
        heightCm = heightCm,
        bodyFatPercent = bodyFatPercent,
        goal = goal,
        activityLevel = activityLevel
    )
}

fun User.toEntity(): UserEntity {
    return UserEntity(
        id = id,
        biologicalSex = biologicalSex,
        age = age,
        weightKg = weightKg,
        heightCm = heightCm,
        bodyFatPercent = bodyFatPercent,
        goal = goal,
        activityLevel = activityLevel
    )
}
