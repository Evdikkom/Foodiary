package com.example.foodiary.testing

import com.example.foodiary.data.model.ActivityLevel
import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.data.model.BiologicalSex
import com.example.foodiary.data.model.UserGoal
import com.example.foodiary.data.model.UserRestrictionKind
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.model.UserAllergenConflict

fun food(
    id: String,
    name: String = id,
    caloriesPer100g: Double = 100.0,
    proteinPer100g: Double = 10.0,
    fatPer100g: Double = 5.0,
    carbsPer100g: Double = 10.0,
    isCustom: Boolean = false,
    category: String = "other"
): Food {
    return Food(
        id = id,
        name = name,
        imageUrl = null,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g,
        isCustom = isCustom,
        category = category
    )
}

fun meal(
    id: Long = 0L,
    foodId: String,
    quantityInGrams: Double,
    mealType: MealType,
    timestamp: Long,
    note: String = ""
): Meal {
    return Meal(
        id = id,
        foodId = foodId,
        quantityInGrams = quantityInGrams,
        mealType = mealType,
        timestamp = timestamp,
        note = note
    )
}

fun user(
    goal: UserGoal = UserGoal.MAINTAIN_WEIGHT,
    activityLevel: ActivityLevel = ActivityLevel.LOW_ACTIVE,
    biologicalSex: BiologicalSex = BiologicalSex.MALE,
    age: Int = 30,
    weightKg: Double = 80.0,
    heightCm: Int = 180,
    bodyFatPercent: Double? = null
): User {
    return User(
        biologicalSex = biologicalSex,
        age = age,
        weightKg = weightKg,
        heightCm = heightCm,
        bodyFatPercent = bodyFatPercent,
        goal = goal,
        activityLevel = activityLevel
    )
}

fun inferredConflict(allergenId: String = "gluten"): UserAllergenConflict {
    return UserAllergenConflict(
        allergen = Allergen(
            id = allergenId,
            code = allergenId.uppercase(),
            displayName = allergenId,
            description = allergenId
        ),
        restrictionKind = UserRestrictionKind.ALLERGY,
        presenceType = AllergenPresenceType.CONTAINS,
        evidenceType = AllergenEvidenceType.NAME_MATCH_INFERRED
    )
}

fun manualConflict(allergenId: String = "milk"): UserAllergenConflict {
    return UserAllergenConflict(
        allergen = Allergen(
            id = allergenId,
            code = allergenId.uppercase(),
            displayName = allergenId,
            description = allergenId
        ),
        restrictionKind = UserRestrictionKind.ALLERGY,
        presenceType = AllergenPresenceType.CONTAINS,
        evidenceType = AllergenEvidenceType.MANUAL
    )
}

fun safetyProfileWithWarning(conflict: UserAllergenConflict): FoodSafetyProfile {
    return FoodSafetyProfile(
        warningConflicts = listOf(conflict)
    )
}
