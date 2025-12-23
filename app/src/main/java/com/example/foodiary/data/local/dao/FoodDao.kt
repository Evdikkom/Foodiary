package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.foodiary.data.local.entity.FoodEntity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("""
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT 50
    """)
    fun searchFoods(query: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :foodId LIMIT 1")
    suspend fun getFoodById(foodId: String): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countFoods(): Int
}
