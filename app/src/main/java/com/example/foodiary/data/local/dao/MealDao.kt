package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.MealEntity

@Dao
interface MealDao {

    @Query("""
        SELECT * FROM meals
        WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp
        ORDER BY timestamp DESC
    """)
    suspend fun getMealsForPeriod(startTimestamp: Long, endTimestamp: Long): List<MealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meals: List<MealEntity>)

    @Insert
    suspend fun insert(meal: MealEntity): Long

    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteById(mealId: Long)

}