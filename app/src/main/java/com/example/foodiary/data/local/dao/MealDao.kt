package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.MealEntity

@Dao
interface MealDao {

    /**
     * Вставка списка приёмов пищи.
     * Используется для seeding и batch-операций.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meals: List<MealEntity>)

    /**
     * Получение приёмов пищи за период времени.
     */
    @Query("""
        SELECT * FROM meals
        WHERE timestamp BETWEEN :start AND :end
    """)
    suspend fun getMealsForPeriod(
        start: Long,
        end: Long
    ): List<MealEntity>
}
