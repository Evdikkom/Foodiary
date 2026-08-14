package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.UserRestriction
import com.example.foodiary.domain.repository.AllergenRepository

class SaveUserRestrictionsUseCase(
    private val allergenRepository: AllergenRepository
) {
    suspend operator fun invoke(restrictions: List<UserRestriction>) {
        allergenRepository.replaceUserRestrictions(restrictions = restrictions)
    }
}
