package com.example.foodiary.data.local.entity

import androidx.room.Entity
import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.AllergenPresenceType

@Entity(
    tableName = "food_allergens",
    primaryKeys = ["foodId", "allergenId", "evidenceType"]
)
data class FoodAllergenEntity(
    val foodId: String,
    val allergenId: String,
    val presenceType: AllergenPresenceType,
    val evidenceType: AllergenEvidenceType,
    val confidence: Double = 1.0
)
