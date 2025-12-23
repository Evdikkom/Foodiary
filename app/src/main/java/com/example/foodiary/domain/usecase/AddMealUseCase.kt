package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.repository.MealRepository

class AddMealUseCase(
    private val mealRepository: MealRepository
) {
    suspend operator fun invoke(meal: Meal): Long {
        return mealRepository.addMeal(meal)
    }
}