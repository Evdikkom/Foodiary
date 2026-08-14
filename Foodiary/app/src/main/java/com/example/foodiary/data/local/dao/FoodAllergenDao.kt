package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.FoodAllergenEntity

@Dao
interface FoodAllergenDao {

    @Query("SELECT * FROM food_allergens WHERE foodId = :foodId")
    suspend fun getFoodAllergens(foodId: String): List<FoodAllergenEntity>

    @Query("SELECT * FROM food_allergens WHERE foodId IN (:foodIds)")
    suspend fun getFoodAllergensForFoods(foodIds: List<String>): List<FoodAllergenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FoodAllergenEntity>)

    @Query("DELETE FROM food_allergens WHERE foodId = :foodId")
    suspend fun deleteByFoodId(foodId: String)

    @Query("SELECT COUNT(*) FROM food_allergens")
    suspend fun countFoodAllergens(): Int
}
