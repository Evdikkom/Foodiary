package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.foodiary.data.model.UserRestrictionKind

@Entity(tableName = "user_restrictions")
data class UserRestrictionEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val allergenId: String,
    val restrictionKind: UserRestrictionKind
)
