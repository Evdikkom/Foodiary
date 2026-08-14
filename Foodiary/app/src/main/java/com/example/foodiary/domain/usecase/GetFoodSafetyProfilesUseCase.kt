package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.repository.AllergenRepository

class GetFoodSafetyProfilesUseCase(
    private val allergenRepository: AllergenRepository
) {
    suspend operator fun invoke(foods: List<Food>): Map<String, FoodSafetyProfile> {
        return allergenRepository.getFoodSafetyProfiles(foods)
    }
}
