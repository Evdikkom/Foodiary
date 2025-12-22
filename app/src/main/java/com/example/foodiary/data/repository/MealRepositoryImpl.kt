package com.example.foodiary.data.local.repository

import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.MealRepository

/**
 * MealRepositoryImpl — реализация репозитория приёмов пищи.
 * Связывает слой данных (Room) с доменной моделью.
 */
class MealRepositoryImpl(
    private val mealDao: MealDao
) : MealRepository {

    /**
     * Возвращает список приёмов пищи за период.
     */
    override suspend fun getMealsForPeriod(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<Meal> {
        return mealDao
            .getMealsForPeriod(startTimestamp, endTimestamp)
            .map { it.toDomain() }
    }

    /**
     * Возвращает агрегированные суточные показатели питания.
     *
     * ВАЖНО:
     * - агрегация происходит здесь, а не в DAO
     * - DAO отвечает только за доступ к данным
     */
    override suspend fun getDailyNutrition(
        startOfDay: Long,
        endOfDay: Long
    ): DailyNutrition {

        val meals = mealDao
            .getMealsForPeriod(startOfDay, endOfDay)
            .map { it.toDomain() }

        val mealsCount = meals.size

        val mealsByType: Map<MealType, Int> =
            meals.groupingBy { it.mealType }.eachCount()

        return DailyNutrition(
            totalCalories = 0.0, // будет рассчитываться в UseCase
            totalProtein = 0.0,
            totalFat = 0.0,
            totalCarbs = 0.0,
            mealsCount = mealsCount,
            mealsByType = mealsByType
        )
    }
}
