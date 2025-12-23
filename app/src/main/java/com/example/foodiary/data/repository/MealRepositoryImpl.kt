package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.repository.MealRepository
import com.example.foodiary.data.mapper.toEntity
/**
 * MealRepositoryImpl — реализация репозитория приёмов пищи.
 *
 * Отвечает за:
 * - получение приёмов пищи из БД
 * - преобразование Entity → Domain
 * - базовую агрегацию (количество приёмов, распределение по типам)
 *
 * НЕ отвечает за:
 * - расчёт калорий и БЖУ (это зона UseCase)
 */
class MealRepositoryImpl(
    private val mealDao: MealDao,
    private val foodRepository: FoodRepository
) : MealRepository {

    /**
     * Возвращает список приёмов пищи за период времени.
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
     * Возвращает агрегированные суточные показатели питания
     * без расчёта калорий и БЖУ.
     *
     * Финальный расчёт выполняется в GetDailyNutritionUseCase.
     */
    override suspend fun getDailyNutrition(
        startOfDay: Long,
        endOfDay: Long
    ): DailyNutrition {

        val meals: List<Meal> =
            mealDao.getMealsForPeriod(startOfDay, endOfDay)
                .map { it.toDomain() }

        val mealsCount = meals.size

        val mealsByType: Map<MealType, Int> =
            meals.groupingBy { it.mealType }.eachCount()

        return DailyNutrition(
            totalCalories = 0.0,
            totalProtein = 0.0,
            totalFat = 0.0,
            totalCarbs = 0.0,
            mealsCount = mealsCount,
            mealsByType = mealsByType
        )
    }

    override suspend fun addMeal(meal: Meal): Long {
        return mealDao.insert(meal.toEntity())
    }
}
