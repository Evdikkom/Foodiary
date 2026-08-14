package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.repository.MealRepository

/**
 * AddMealUseCase (UseCase — сценарий бизнес-логики)
 * Сохраняет приём пищи пользователя.
 */
class AddMealUseCase(
    private val mealRepository: MealRepository
) {
    suspend operator fun invoke(meal: Meal) {
        mealRepository.addMeal(meal)
    }
}
