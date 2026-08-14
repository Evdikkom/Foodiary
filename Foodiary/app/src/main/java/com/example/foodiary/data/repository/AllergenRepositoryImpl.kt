package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.AllergenDao
import com.example.foodiary.data.local.dao.FoodAllergenDao
import com.example.foodiary.data.local.dao.UserRestrictionDao
import com.example.foodiary.data.local.entity.FoodAllergenEntity
import com.example.foodiary.data.local.seed.AllergenCatalog
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.mapper.toEntity
import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.data.model.UserRestrictionKind
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodAllergen
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.model.UserAllergenConflict
import com.example.foodiary.domain.model.UserRestriction
import com.example.foodiary.domain.repository.AllergenRepository

class AllergenRepositoryImpl(
    private val allergenDao: AllergenDao,
    private val foodAllergenDao: FoodAllergenDao,
    private val userRestrictionDao: UserRestrictionDao,
) : AllergenRepository {

    override suspend fun getAllergens(): List<Allergen> {
        ensureAllergenCatalog()
        return allergenDao.getAllergens().map { it.toDomain() }
    }

    override suspend fun getUserRestrictions(userId: String): List<UserRestriction> {
        ensureAllergenCatalog()
        val allergenMap = allergenDao.getAllergens()
            .associateBy { it.id }
            .mapValues { it.value.toDomain() }

        return userRestrictionDao.getRestrictionsForUser(userId)
            .mapNotNull { entity ->
                allergenMap[entity.allergenId]?.let { allergen ->
                    entity.toDomain(allergen)
                }
            }
            .sortedBy { it.allergen.displayName }
    }

    override suspend fun replaceUserRestrictions(userId: String, restrictions: List<UserRestriction>) {
        userRestrictionDao.deleteForUser(userId)
        if (restrictions.isNotEmpty()) {
            userRestrictionDao.insertAll(restrictions.map { it.toEntity(userId) })
        }
    }

    override suspend fun getFoodSafetyProfile(
        foodId: String,
        foodName: String,
        ingredientHints: List<String>
    ): FoodSafetyProfile {
        ensureAllergenCatalog()
        val allergenMap = allergenDao.getAllergens()
            .associateBy { it.id }
            .mapValues { it.value.toDomain() }
        val stored = mapFoodAllergens(foodAllergenDao.getFoodAllergens(foodId), allergenMap)
        val restrictions = getUserRestrictions()
        return buildProfile(
            stored = stored,
            restrictions = restrictions,
            foodName = foodName,
            ingredientHints = ingredientHints
        )
    }

    override suspend fun getFoodSafetyProfiles(foods: List<Food>): Map<String, FoodSafetyProfile> {
        if (foods.isEmpty()) return emptyMap()

        ensureAllergenCatalog()
        val allergenMap = allergenDao.getAllergens()
            .associateBy { it.id }
            .mapValues { it.value.toDomain() }
        val restrictions = getUserRestrictions()
        val storedByFood = foodAllergenDao.getFoodAllergensForFoods(foods.map { it.id })
            .groupBy { it.foodId }
            .mapValues { (_, items) -> mapFoodAllergens(items, allergenMap) }

        return foods.associate { food ->
            food.id to buildProfile(
                stored = storedByFood[food.id].orEmpty(),
                restrictions = restrictions,
                foodName = food.name
            )
        }
    }

    override suspend fun replaceManualFoodAllergens(
        foodId: String,
        allergens: Map<String, AllergenPresenceType>
    ) {
        ensureAllergenCatalog()
        val existing = foodAllergenDao.getFoodAllergens(foodId)
            .filterNot { it.evidenceType == AllergenEvidenceType.MANUAL }
        val manual = allergens.map { (allergenId, presenceType) ->
            FoodAllergenEntity(
                foodId = foodId,
                allergenId = allergenId,
                presenceType = presenceType,
                evidenceType = AllergenEvidenceType.MANUAL,
                confidence = 1.0
            )
        }

        foodAllergenDao.deleteByFoodId(foodId)
        val merged = existing + manual
        if (merged.isNotEmpty()) {
            foodAllergenDao.insertAll(merged)
        }
    }

    override suspend fun applyImportedAllergens(
        foodId: String,
        foodName: String,
        allergenTags: List<String>,
        traceTags: List<String>
    ) {
        ensureAllergenCatalog()
        val explicitIds = AllergenCatalog.normalizeOpenFoodFactsTags(allergenTags)
        val traceIds = AllergenCatalog.normalizeOpenFoodFactsTags(traceTags)

        val importedItems = buildList {
            explicitIds.forEach { allergenId ->
                add(
                    FoodAllergenEntity(
                        foodId = foodId,
                        allergenId = allergenId,
                        presenceType = AllergenPresenceType.CONTAINS,
                        evidenceType = AllergenEvidenceType.OPEN_FOOD_FACTS,
                        confidence = 1.0
                    )
                )
            }
            traceIds
                .filterNot { it in explicitIds }
                .forEach { allergenId ->
                    add(
                        FoodAllergenEntity(
                            foodId = foodId,
                            allergenId = allergenId,
                            presenceType = AllergenPresenceType.MAY_CONTAIN,
                            evidenceType = AllergenEvidenceType.OPEN_FOOD_FACTS,
                            confidence = 0.9
                        )
                    )
                }
        }

        foodAllergenDao.deleteByFoodId(foodId)
        if (importedItems.isNotEmpty()) {
            foodAllergenDao.insertAll(importedItems)
        } else {
            applyInferredFoodAllergens(foodId, listOf(foodName))
        }
    }

    override suspend fun deriveRecipeAllergens(recipeFoodId: String, ingredientFoods: List<Food>) {
        ensureAllergenCatalog()
        if (ingredientFoods.isEmpty()) {
            deleteFoodAllergens(recipeFoodId)
            return
        }

        val ingredientProfiles = getFoodSafetyProfiles(ingredientFoods)
        val merged = linkedMapOf<String, FoodAllergenEntity>()

        ingredientProfiles.values.forEach { profile ->
            (profile.confirmedAllergens + profile.inferredAllergens)
                .forEach { allergen ->
                    val existing = merged[allergen.allergen.id]
                    val mergedPresence = when {
                        existing == null -> allergen.presenceType
                        existing.presenceType == AllergenPresenceType.CONTAINS -> AllergenPresenceType.CONTAINS
                        allergen.presenceType == AllergenPresenceType.CONTAINS -> AllergenPresenceType.CONTAINS
                        else -> AllergenPresenceType.MAY_CONTAIN
                    }
                    val mergedConfidence = maxOf(existing?.confidence ?: 0.0, allergen.confidence)
                    merged[allergen.allergen.id] = FoodAllergenEntity(
                        foodId = recipeFoodId,
                        allergenId = allergen.allergen.id,
                        presenceType = mergedPresence,
                        evidenceType = AllergenEvidenceType.RECIPE_DERIVED,
                        confidence = mergedConfidence
                    )
                }
        }

        foodAllergenDao.deleteByFoodId(recipeFoodId)
        if (merged.isNotEmpty()) {
            foodAllergenDao.insertAll(merged.values.toList())
        }
    }

    override suspend fun applyInferredFoodAllergens(
        foodId: String,
        names: List<String>,
        ingredientHints: List<String>
    ) {
        ensureAllergenCatalog()
        val existing = foodAllergenDao.getFoodAllergens(foodId)
            .filterNot { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }

        val excludedAllergenIds = existing.map { it.allergenId }.toSet()
        val inferredHits = AllergenCatalog.inferFromNames(
            names = names + ingredientHints,
            excludedAllergenIds = excludedAllergenIds
        )

        val inferred = inferredHits.map { hit ->
            FoodAllergenEntity(
                foodId = foodId,
                allergenId = hit.allergenId,
                presenceType = AllergenPresenceType.MAY_CONTAIN,
                evidenceType = AllergenEvidenceType.NAME_MATCH_INFERRED,
                confidence = hit.confidence
            )
        }

        foodAllergenDao.deleteByFoodId(foodId)
        val merged = existing + inferred
        if (merged.isNotEmpty()) {
            foodAllergenDao.insertAll(merged)
        }
    }

    override suspend fun deleteFoodAllergens(foodId: String) {
        foodAllergenDao.deleteByFoodId(foodId)
    }

    private suspend fun ensureAllergenCatalog() {
        allergenDao.insertAll(AllergenCatalog.allergens)
    }

    private suspend fun buildProfile(
        stored: List<FoodAllergen>,
        restrictions: List<UserRestriction>,
        foodName: String,
        ingredientHints: List<String> = emptyList()
    ): FoodSafetyProfile {
        val confirmed = stored.filter { it.evidenceType != AllergenEvidenceType.NAME_MATCH_INFERRED }
            .sortedBy { it.allergen.displayName }
        val storedInferred = stored.filter { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }
        val inferred = if (storedInferred.isNotEmpty()) {
            storedInferred
        } else {
            inferRuntimeAllergens(foodName, ingredientHints, confirmed.map { it.allergen.id }.toSet())
        }.sortedBy { it.allergen.displayName }

        val strictConflicts = mutableListOf<UserAllergenConflict>()
        val warningConflicts = mutableListOf<UserAllergenConflict>()
        val restrictionById = restrictions.associateBy { it.allergen.id }

        confirmed.forEach { allergen ->
            val restriction = restrictionById[allergen.allergen.id] ?: return@forEach
            val conflict = UserAllergenConflict(
                allergen = allergen.allergen,
                restrictionKind = restriction.restrictionKind,
                presenceType = allergen.presenceType,
                evidenceType = allergen.evidenceType
            )
            if (restriction.restrictionKind == UserRestrictionKind.ALLERGY) {
                strictConflicts += conflict
            } else {
                warningConflicts += conflict
            }
        }

        inferred.forEach { allergen ->
            val restriction = restrictionById[allergen.allergen.id] ?: return@forEach
            warningConflicts += UserAllergenConflict(
                allergen = allergen.allergen,
                restrictionKind = restriction.restrictionKind,
                presenceType = allergen.presenceType,
                evidenceType = allergen.evidenceType
            )
        }

        return FoodSafetyProfile(
            confirmedAllergens = confirmed,
            inferredAllergens = inferred,
            highRiskConflicts = strictConflicts.distinctBy { it.allergen.id },
            warningConflicts = warningConflicts.distinctBy { it.allergen.id }
        )
    }

    private suspend fun inferRuntimeAllergens(
        foodName: String,
        ingredientHints: List<String>,
        excludedAllergenIds: Set<String>
    ): List<FoodAllergen> {
        val allergenMap = allergenDao.getAllergens()
            .associateBy { it.id }
            .mapValues { it.value.toDomain() }
        return AllergenCatalog.inferFromNames(
            names = listOf(foodName) + ingredientHints,
            excludedAllergenIds = excludedAllergenIds
        ).mapNotNull { hit ->
            allergenMap[hit.allergenId]?.let { allergen ->
                FoodAllergen(
                    allergen = allergen,
                    presenceType = AllergenPresenceType.MAY_CONTAIN,
                    evidenceType = AllergenEvidenceType.NAME_MATCH_INFERRED,
                    confidence = hit.confidence
                )
            }
        }
    }

    private fun mapFoodAllergens(
        entities: List<FoodAllergenEntity>,
        allergenMap: Map<String, Allergen>
    ): List<FoodAllergen> {
        return entities.mapNotNull { entity ->
            allergenMap[entity.allergenId]?.let { allergen ->
                entity.toDomain(allergen)
            }
        }
    }
}
