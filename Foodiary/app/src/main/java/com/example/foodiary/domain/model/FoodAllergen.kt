package com.example.foodiary.domain.model

import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.AllergenPresenceType

data class FoodAllergen(
    val allergen: Allergen,
    val presenceType: AllergenPresenceType,
    val evidenceType: AllergenEvidenceType,
    val confidence: Double,
)
