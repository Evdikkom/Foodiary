package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.UserEntity

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id = 'current_user' LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)
}
