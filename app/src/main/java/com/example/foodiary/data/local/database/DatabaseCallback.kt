package com.example.foodiary.data.local.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.MealType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * DatabaseCallback — наполнение базы данных тестовыми данными
 * при первом создании базы.
 */
class DatabaseCallback(
    private val foodDao: FoodDao,
    private val mealDao: MealDao
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        CoroutineScope(Dispatchers.IO).launch {
            seedFoods()
            seedMeals()
        }
    }

    private suspend fun seedFoods() {
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
    }

    private suspend fun seedMeals() {
        val calendar = Calendar.getInstance()

        val todayTimestamp = calendar.timeInMillis

        val meals = listOf(
            MealEntity(
                foodId = "chicken_breast",
                quantityInGrams = 200.0,
                mealType = MealType.LUNCH,
                timestamp = todayTimestamp,
                note = "Обед"
            ),
            MealEntity(
                foodId = "rice",
                quantityInGrams = 150.0,
                mealType = MealType.LUNCH,
                timestamp = todayTimestamp
            ),
            MealEntity(
                foodId = "apple",
                quantityInGrams = 120.0,
                mealType = MealType.SNACK,
                timestamp = todayTimestamp
            )
        )

        mealDao.insertAll(meals)
    }
}
