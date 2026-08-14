package com.example.foodiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allergens")
data class AllergenEntity(
    @PrimaryKey
    val id: String,
    val code: String,
    val displayName: String,
    val description: String,
    val sortOrder: Int
)
