package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.foodiary.data.local.entity.FoodEntity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Query("SELECT * FROM foods WHERE id = :foodId LIMIT 1")
    suspend fun getFoodById(foodId: String): FoodEntity?

    @Query("SELECT * FROM foods")
    suspend fun getAllFoods(): List<FoodEntity>
}
