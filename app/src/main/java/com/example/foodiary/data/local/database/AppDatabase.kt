package com.example.foodiary.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Database(
    entities = [FoodEntity::class, MealEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var SEED_STARTED: Boolean = false

        fun getInstance(context: Context): AppDatabase {
            val appContext = context.applicationContext

            val db = INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "foodiary.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { created ->
                        INSTANCE = created
                    }
            }

            // Засев запускаем уже ПОСЛЕ того, как INSTANCE гарантированно установлен
            ensureSeed(db)

            return db
        }

        private fun ensureSeed(db: AppDatabase) {
            if (SEED_STARTED) return
            SEED_STARTED = true

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val foodDao = db.foodDao()
                    val mealDao = db.mealDao()

                    val foodsCount = foodDao.countFoods()

                    android.util.Log.d("DB_SEED", "foodsCount(before)=$foodsCount")

                    if (foodsCount == 0) {
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

                        val now = System.currentTimeMillis()
                        val meals = listOf(
                            MealEntity(
                                foodId = "chicken_breast",
                                quantityInGrams = 200.0,
                                mealType = com.example.foodiary.domain.model.MealType.LUNCH,
                                timestamp = now,
                                note = "Тестовый обед"
                            ),
                            MealEntity(
                                foodId = "rice",
                                quantityInGrams = 150.0,
                                mealType = com.example.foodiary.domain.model.MealType.LUNCH,
                                timestamp = now
                            ),
                            MealEntity(
                                foodId = "apple",
                                quantityInGrams = 120.0,
                                mealType = com.example.foodiary.domain.model.MealType.SNACK,
                                timestamp = now
                            )
                        )
                        mealDao.insertAll(meals)

                        android.util.Log.d("DB_SEED", "seed inserted OK")
                    } else {
                        android.util.Log.d("DB_SEED", "seed skipped (foods already exist)")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DB_SEED", "seed failed: ${e.message}", e)
                }
            }
        }
    }

}
