package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase

class GetDailyNutritionViewModelFactory(
    private val getDailyNutritionUseCase: GetDailyNutritionUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GetDailyNutritionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GetDailyNutritionViewModel(getDailyNutritionUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

