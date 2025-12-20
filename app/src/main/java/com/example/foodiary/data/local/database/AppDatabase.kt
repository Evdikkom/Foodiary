package com.example.foodiary.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.data.local.entity.UserEntity

/**
 * AppDatabase — основная база данных приложения.
 *
 * entities — список таблиц (Entity)
 * version — версия схемы БД (используется для миграций)
 */
@Database(
    entities = [
        UserEntity::class,
        MealEntity::class,
        FoodEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // DAO будут добавлены на следующем шаге
}
