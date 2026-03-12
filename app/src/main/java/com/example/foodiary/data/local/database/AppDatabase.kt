package com.example.foodiary.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.domain.model.MealType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [FoodEntity::class, MealEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
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

            ensureSeed(db)

            return db
        }

        private fun ensureSeed(db: AppDatabase) {
            if (SEED_STARTED) return
            SEED_STARTED = true

            CoroutineScope(Dispatchers.IO).launch {
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
                                imageUrl = "drawable://chicken_breast",
                                caloriesPer100g = 165.0,
                                proteinPer100g = 31.0,
                                fatPer100g = 3.6,
                                carbsPer100g = 0.0
                            ),
                            FoodEntity(
                                id = "rice",
                                name = "Рис",
                                imageUrl = "drawable://rice",
                                caloriesPer100g = 130.0,
                                proteinPer100g = 2.7,
                                fatPer100g = 0.3,
                                carbsPer100g = 28.0
                            ),
                            FoodEntity(
                                id = "apple",
                                name = "Яблоко",
                                imageUrl = "drawable://apple",
                                caloriesPer100g = 52.0,
                                proteinPer100g = 0.3,
                                fatPer100g = 0.2,
                                carbsPer100g = 14.0
                            ),
                            FoodEntity(
                                id = "banana",
                                name = "Банан",
                                imageUrl = "drawable://banana",
                                caloriesPer100g = 89.0,
                                proteinPer100g = 1.1,
                                fatPer100g = 0.3,
                                carbsPer100g = 22.8
                            )
                        )
                        foodDao.insertAll(foods)

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