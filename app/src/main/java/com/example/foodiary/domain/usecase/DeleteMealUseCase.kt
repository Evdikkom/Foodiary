package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.repository.MealRepository

class DeleteMealUseCase(
    private val mealRepository: MealRepository
) {
    suspend operator fun invoke(mealId: Long) {
        mealRepository.deleteMeal(mealId)
    }
}
