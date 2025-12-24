package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.repository.MealRepository

class GetMealsForPeriodUseCase(
    private val mealRepository: MealRepository
) {
    suspend operator fun invoke(startTimestamp: Long, endTimestamp: Long): List<Meal> {
        return mealRepository.getMealsForPeriod(startTimestamp, endTimestamp)
    }
}
