package com.example.foodiary.domain.model

data class FoodSafetyProfile(
    val confirmedAllergens: List<FoodAllergen> = emptyList(),
    val inferredAllergens: List<FoodAllergen> = emptyList(),
    val highRiskConflicts: List<UserAllergenConflict> = emptyList(),
    val warningConflicts: List<UserAllergenConflict> = emptyList(),
) {
    val hasAnyKnownAllergens: Boolean
        get() = confirmedAllergens.isNotEmpty() || inferredAllergens.isNotEmpty()

    val hasHighRisk: Boolean
        get() = highRiskConflicts.isNotEmpty()
}
