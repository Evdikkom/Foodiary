package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.UserRestrictionEntity

@Dao
interface UserRestrictionDao {

    @Query("SELECT * FROM user_restrictions WHERE userId = :userId")
    suspend fun getRestrictionsForUser(userId: String): List<UserRestrictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<UserRestrictionEntity>)

    @Query("DELETE FROM user_restrictions WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}
