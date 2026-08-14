package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.repository.AllergenRepository

class GetFoodSafetyProfileUseCase(
    private val allergenRepository: AllergenRepository
) {
    suspend operator fun invoke(food: Food): FoodSafetyProfile {
        return allergenRepository.getFoodSafetyProfile(
            foodId = food.id,
            foodName = food.name
        )
    }
}
