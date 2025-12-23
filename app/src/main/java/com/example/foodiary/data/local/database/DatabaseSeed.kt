package com.example.foodiary.data.local.database

import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.MealType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseSeed {

    fun seed(database: AppDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            val foodDao = database.foodDao()
            val mealDao = database.mealDao()

            // 1) Справочник продуктов
            val foods = listOf(
                FoodEntity(
                    id = "chicken_breast",
                    name = "Куриная грудка",
                    caloriesPer100g = 165.0,
                    proteinPer100g = 31.0,
                    fatPer100g = 3.6,
                    carbsPer100g = 0.0
                ),
                FoodEntity(
                    id = "rice",
                    name = "Рис",
                    caloriesPer100g = 130.0,
                    proteinPer100g = 2.7,
                    fatPer100g = 0.3,
                    carbsPer100g = 28.0
                ),
                FoodEntity(
                    id = "apple",
                    name = "Яблоко",
                    caloriesPer100g = 52.0,
                    proteinPer100g = 0.3,
                    fatPer100g = 0.2,
                    carbsPer100g = 14.0
                )
            )
            foodDao.insertAll(foods)

            // 2) Пара приёмов пищи “сегодня”
            val now = System.currentTimeMillis()

            val meals = listOf(
                MealEntity(
                    foodId = "chicken_breast",
                    quantityInGrams = 200.0,
                    mealType = MealType.LUNCH,
                    timestamp = now,
                    note = "Тестовый обед"
                ),
                MealEntity(
                    foodId = "rice",
                    quantityInGrams = 150.0,
                    mealType = MealType.LUNCH,
                    timestamp = now
                ),
                MealEntity(
                    foodId = "apple",
                    quantityInGrams = 120.0,
                    mealType = MealType.SNACK,
                    timestamp = now
                )
            )
            mealDao.insertAll(meals)
        }
    }
}
