package com.example.foodiary.data.local.dao

import androidx.room.*
import com.example.foodiary.data.local.entity.MealEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * MealDao — DAO для работы с приёмами пищи.
 */
@Dao
interface MealDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    @Query(
        """
        SELECT * FROM meals
        WHERE date = :date
        ORDER BY time ASC
        """
    )
    fun getMealsByDate(date: LocalDate): Flow<List<MealEntity>>

    @Query(
        """
        SELECT * FROM meals
        WHERE id = :mealId
        LIMIT 1
        """
    )
    suspend fun getMealById(mealId: Long): MealEntity?
}
