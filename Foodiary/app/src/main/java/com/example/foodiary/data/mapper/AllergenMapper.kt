package com.example.foodiary.data.mapper

import com.example.foodiary.data.local.entity.AllergenEntity
import com.example.foodiary.data.local.entity.FoodAllergenEntity
import com.example.foodiary.data.local.entity.UserRestrictionEntity
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.FoodAllergen
import com.example.foodiary.domain.model.UserRestriction

fun AllergenEntity.toDomain(): Allergen {
    return Allergen(
        id = id,
        code = code,
        displayName = displayName,
        description = description
    )
}

fun UserRestrictionEntity.toDomain(allergen: Allergen): UserRestriction {
    return UserRestriction(
        allergen = allergen,
        restrictionKind = restrictionKind
    )
}

fun UserRestriction.toEntity(userId: String): UserRestrictionEntity {
    return UserRestrictionEntity(
        id = "${userId}_${allergen.id}_${restrictionKind.name}",
        userId = userId,
        allergenId = allergen.id,
        restrictionKind = restrictionKind
    )
}

fun FoodAllergenEntity.toDomain(allergen: Allergen): FoodAllergen {
    return FoodAllergen(
        allergen = allergen,
        presenceType = presenceType,
        evidenceType = evidenceType,
        confidence = confidence
    )
}
