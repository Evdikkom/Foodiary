package com.example.foodiary.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.data.local.entity.UserEntity
import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.local.dao.FoodDao

/**
 * AppDatabase — основная база данных приложения.
 *
 * entities — список таблиц (Entity)
 * version — версия схемы БД (используется для миграций)
 */
@Database(
    entities = [
        FoodEntity::class,
        MealEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "foodiary_db"
                )
                    .addCallback(
                        DatabaseCallback(
                            foodDao = INSTANCE!!.foodDao(),
                            mealDao = INSTANCE!!.mealDao()
                        )
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
