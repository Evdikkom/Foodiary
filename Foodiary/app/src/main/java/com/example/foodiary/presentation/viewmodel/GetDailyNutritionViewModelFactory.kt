package com.example.foodiary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.usecase.DeleteMealUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.domain.usecase.GetMealsForPeriodUseCase

class GetDailyNutritionViewModelFactory(
    private val getDailyNutritionUseCase: GetDailyNutritionUseCase,
    private val getMealsForPeriodUseCase: GetMealsForPeriodUseCase,
    private val deleteMealUseCase: DeleteMealUseCase,
    private val foodRepository: FoodRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GetDailyNutritionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GetDailyNutritionViewModel(
                getDailyNutritionUseCase = getDailyNutritionUseCase,
                getMealsForPeriodUseCase = getMealsForPeriodUseCase,
                deleteMealUseCase = deleteMealUseCase,
                foodRepository = foodRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
