package com.example.foodiary.domain.repository

import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Meal

/**
 * MealRepository — контракт доступа к данным о питании пользователя.
 * Определяет, какие данные необходимы бизнес-логике,
 * не раскрывая деталей их получения.
 */
interface MealRepository {

    /**
     * Возвращает список приёмов пищи пользователя
     * за указанный период времени.
     */
    suspend fun getMealsForPeriod(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<Meal>

    /**
     * Возвращает агрегированные суточные показатели питания
     * за указанный период.
     */
    suspend fun getDailyNutrition(
        startOfDay: Long,
        endOfDay: Long
    ): DailyNutrition

    suspend fun addMeal(meal: Meal): Long

    suspend fun deleteMeal(mealId: Long)

}
