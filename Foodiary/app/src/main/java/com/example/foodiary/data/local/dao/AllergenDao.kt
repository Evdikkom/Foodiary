package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.AllergenEntity

@Dao
interface AllergenDao {

    @Query("SELECT * FROM allergens ORDER BY sortOrder ASC, displayName ASC")
    suspend fun getAllergens(): List<AllergenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AllergenEntity>)

    @Query("SELECT COUNT(*) FROM allergens")
    suspend fun countAllergens(): Int
}
