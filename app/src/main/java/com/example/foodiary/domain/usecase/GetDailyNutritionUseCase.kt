package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.repository.MealRepository

/**
 * GetDailyNutritionUseCase — бизнес-сценарий получения
 * суточных показателей питания пользователя.
 */
class GetDailyNutritionUseCase(
    private val mealRepository: MealRepository
) {

    /**
     * Выполняет расчёт суточных показателей питания
     * за указанный временной период.
     */
    suspend operator fun invoke(
        startOfDay: Long,
        endOfDay: Long
    ): DailyNutrition {
        return mealRepository.getDailyNutrition(startOfDay, endOfDay)
    }
}
