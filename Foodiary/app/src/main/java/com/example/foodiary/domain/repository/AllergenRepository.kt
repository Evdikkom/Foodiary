package com.example.foodiary.domain.repository

import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.model.UserRestriction

interface AllergenRepository {
    suspend fun getAllergens(): List<Allergen>
    suspend fun getUserRestrictions(userId: String = "current_user"): List<UserRestriction>
    suspend fun replaceUserRestrictions(
        userId: String = "current_user",
        restrictions: List<UserRestriction>
    )

    suspend fun getFoodSafetyProfile(
        foodId: String,
        foodName: String,
        ingredientHints: List<String> = emptyList()
    ): FoodSafetyProfile

    suspend fun getFoodSafetyProfiles(foods: List<Food>): Map<String, FoodSafetyProfile>

    suspend fun replaceManualFoodAllergens(
        foodId: String,
        allergens: Map<String, AllergenPresenceType>
    )

    suspend fun applyImportedAllergens(
        foodId: String,
        foodName: String,
        allergenTags: List<String>,
        traceTags: List<String>
    )

    suspend fun deriveRecipeAllergens(
        recipeFoodId: String,
        ingredientFoods: List<Food>
    )

    suspend fun applyInferredFoodAllergens(
        foodId: String,
        names: List<String>,
        ingredientHints: List<String> = emptyList()
    )

    suspend fun deleteFoodAllergens(foodId: String)
}
