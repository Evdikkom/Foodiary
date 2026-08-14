package com.example.foodiary.domain.model

import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.data.model.UserRestrictionKind

data class UserAllergenConflict(
    val allergen: Allergen,
    val restrictionKind: UserRestrictionKind,
    val presenceType: AllergenPresenceType,
    val evidenceType: AllergenEvidenceType,
)
