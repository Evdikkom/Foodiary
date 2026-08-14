package com.example.foodiary.domain.model

import com.example.foodiary.data.model.UserRestrictionKind

data class UserRestriction(
    val allergen: Allergen,
    val restrictionKind: UserRestrictionKind,
)
