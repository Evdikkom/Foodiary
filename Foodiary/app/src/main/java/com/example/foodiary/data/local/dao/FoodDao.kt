package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.FoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("""
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
           OR name LIKE '%' || :altQuery || '%'
           OR name LIKE '%' || :thirdQuery || '%'
           OR id LIKE '%' || :query || '%'
           OR id LIKE '%' || :altQuery || '%'
           OR id LIKE '%' || :thirdQuery || '%'
        ORDER BY
            CASE WHEN id LIKE 'off_%' THEN 1 ELSE 0 END ASC,
            isCustom DESC,
            name ASC
        LIMIT 50
    """)
    fun searchFoods(
        query: String,
        altQuery: String,
        thirdQuery: String
    ): Flow<List<FoodEntity>>

    @Query("""
        SELECT foods.*
        FROM foods
        LEFT JOIN meals ON meals.foodId = foods.id
        GROUP BY foods.id
        HAVING COUNT(meals.id) > 0
        ORDER BY COUNT(meals.id) DESC, MAX(meals.timestamp) DESC, foods.name ASC
        LIMIT :limit
    """)
    fun getRecommendedFoods(limit: Int): Flow<List<FoodEntity>>

    @Query("""
        SELECT foods.*
        FROM foods
        LEFT JOIN meals ON meals.foodId = foods.id
        WHERE foods.isCustom = 1
        GROUP BY foods.id
        ORDER BY COUNT(meals.id) DESC, MAX(meals.timestamp) DESC, foods.name ASC
        LIMIT :limit
    """)
    suspend fun getCustomFoods(limit: Int): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :foodId LIMIT 1")
    suspend fun getFoodById(foodId: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE id IN (:foodIds) ORDER BY name ASC")
    suspend fun getFoodsByIds(foodIds: List<String>): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodEntity)

    @Query("DELETE FROM foods WHERE id = :foodId")
    suspend fun deleteFoodById(foodId: String)

    @Query("""
        SELECT * FROM foods
        ORDER BY
            CASE WHEN id LIKE 'off_%' THEN 1 ELSE 0 END ASC,
            isCustom DESC,
            name ASC
        LIMIT :limit
    """)
    suspend fun getFoodsForPicker(limit: Int): List<FoodEntity>

    @Query("""
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
           OR name LIKE '%' || :altQuery || '%'
           OR name LIKE '%' || :thirdQuery || '%'
        ORDER BY
            CASE WHEN id LIKE 'off_%' THEN 1 ELSE 0 END ASC,
            isCustom DESC,
            name ASC
        LIMIT :limit
    """)
    suspend fun searchFoodsForPicker(
        query: String,
        altQuery: String,
        thirdQuery: String,
        limit: Int
    ): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countFoods(): Int
}
