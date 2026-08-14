package com.example.foodiary.presentation.util

import android.content.Context
import com.example.foodiary.data.local.preferences.NutritionTargetsPreferences
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.usecase.CalculateNutritionTargetsUseCase

class EffectiveNutritionTargetsResolver(context: Context) {

    private val preferences = NutritionTargetsPreferences(context)
    private val calculateNutritionTargetsUseCase = CalculateNutritionTargetsUseCase()

    fun calculateAutoTargets(user: User): NutritionTargets {
        return calculateNutritionTargetsUseCase(user)
    }

    fun resolve(user: User): NutritionTargets {
        return preferences.apply(calculateAutoTargets(user))
    }
}
