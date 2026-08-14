package com.example.foodiary.domain.usecase

import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.HistoryMealTemplate
import com.example.foodiary.domain.model.HistoryMealTemplateItem
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.repository.MealRepository
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

class GetHistoryMealTemplatesUseCase(
    private val mealRepository: MealRepository,
    private val foodRepository: FoodRepository,
) {

    suspend operator fun invoke(
        mealType: MealType,
        limit: Int = DEFAULT_LIMIT,
    ): List<HistoryMealTemplate> {
        val now = System.currentTimeMillis()
        val historyStart = now - HISTORY_WINDOW_MS
        val meals = mealRepository.getMealsForPeriod(historyStart, now + 1)
            .filter { it.mealType == mealType }

        if (meals.isEmpty()) return emptyList()

        val sessions = meals
            .groupBy { startOfDay(it.timestamp) }
            .values
            .mapNotNull { dayMeals -> buildSession(mealType, dayMeals) }

        if (sessions.isEmpty()) return emptyList()

        val foodIds = sessions
            .flatMap { session -> session.items.map { it.foodId } }
            .distinct()
        val foodsById = foodRepository.getFoodsByIds(foodIds).associateBy { it.id }

        val exactTemplates = sessions
            .groupBy { it.signature }
            .values
            .mapNotNull { groupedSessions -> buildTemplate(groupedSessions, foodsById) }

        val exactSignatures = exactTemplates
            .map { template -> buildTemplateSignature(template.items.map { it.foodId }) }
            .toSet()

        val softTemplates = buildSoftMatchedTemplates(sessions, foodsById)
            .filterNot { template ->
                buildTemplateSignature(template.items.map { it.foodId }) in exactSignatures
            }

        return (exactTemplates + softTemplates)
            .filter { it.occurrencesCount >= MIN_OCCURRENCES }
            .sortedWith(
                compareByDescending<HistoryMealTemplate> { it.occurrencesCount }
                    .thenByDescending { it.items.size }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.totalWeightInGrams }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
            .take(limit)
    }

    private fun buildSoftMatchedTemplates(
        sessions: List<Session>,
        foodsById: Map<String, Food>,
    ): List<HistoryMealTemplate> {
        if (sessions.size < MIN_OCCURRENCES) return emptyList()

        val candidateSignatures = linkedSetOf<String>()

        for (leftIndex in 0 until sessions.lastIndex) {
            for (rightIndex in leftIndex + 1 until sessions.size) {
                val left = sessions[leftIndex]
                val right = sessions[rightIndex]
                val commonFoodIds = left.foodIds.intersect(right.foodIds)

                if (!isSoftMatchCandidate(commonFoodIds, left, right)) continue

                candidateSignatures += buildTemplateSignature(commonFoodIds)
            }
        }

        return candidateSignatures.mapNotNull { signature ->
            val coreFoodIds = signature
                .split("|")
                .filter { it.isNotBlank() }
                .toSet()

            val matchingSessions = sessions.filter { session ->
                sessionMatchesCore(session, coreFoodIds)
            }

            if (matchingSessions.size < MIN_OCCURRENCES) return@mapNotNull null

            buildTemplate(
                sessions = matchingSessions,
                foodsById = foodsById,
                includedFoodIds = coreFoodIds,
            )
        }
    }

    private fun isSoftMatchCandidate(
        commonFoodIds: Set<String>,
        left: Session,
        right: Session,
    ): Boolean {
        if (commonFoodIds.size < MIN_SHARED_ITEMS_FOR_SOFT_MATCH) return false

        val smallerSessionSize = min(left.items.size, right.items.size).coerceAtLeast(1)
        val overlapRatio = commonFoodIds.size.toDouble() / smallerSessionSize.toDouble()

        return overlapRatio >= MIN_OVERLAP_RATIO
    }

    private fun sessionMatchesCore(
        session: Session,
        coreFoodIds: Set<String>,
    ): Boolean {
        if (!session.foodIds.containsAll(coreFoodIds)) return false

        val coverageRatio =
            coreFoodIds.size.toDouble() / session.items.size.coerceAtLeast(1).toDouble()

        return coverageRatio >= MIN_SESSION_COVERAGE_RATIO
    }

    private fun buildSession(
        mealType: MealType,
        meals: List<Meal>,
    ): Session? {
        val aggregatedItems = meals
            .groupBy { it.foodId }
            .map { (foodId, groupedMeals) ->
                SessionItem(
                    foodId = foodId,
                    quantityInGrams = groupedMeals.sumOf { it.quantityInGrams },
                )
            }
            .sortedBy { it.foodId }

        if (aggregatedItems.isEmpty()) return null

        return Session(
            mealType = mealType,
            signature = buildTemplateSignature(aggregatedItems.map { it.foodId }),
            items = aggregatedItems,
            foodIds = aggregatedItems.map { it.foodId }.toSet(),
            lastUsedAt = meals.maxOf { it.timestamp },
        )
    }

    private fun buildTemplate(
        sessions: List<Session>,
        foodsById: Map<String, Food>,
        includedFoodIds: Set<String>? = null,
    ): HistoryMealTemplate? {
        val groupedItems = linkedMapOf<String, MutableList<Double>>()

        sessions.forEach { session ->
            session.items
                .filter { includedFoodIds == null || it.foodId in includedFoodIds }
                .forEach { item ->
                    groupedItems.getOrPut(item.foodId) { mutableListOf() } += item.quantityInGrams
                }
        }

        val items = groupedItems.mapNotNull { (foodId, quantities) ->
            val food = foodsById[foodId] ?: return@mapNotNull null
            HistoryMealTemplateItem(
                foodId = foodId,
                foodName = food.name,
                imageUrl = food.imageUrl,
                quantityInGrams = quantities.average(),
            )
        }.sortedByDescending { it.quantityInGrams }

        if (items.isEmpty()) return null

        val title = when (items.size) {
            1 -> items.first().foodName
            2 -> "${items[0].foodName} + ${items[1].foodName}"
            else -> "${items[0].foodName} + ${items[1].foodName} + ещё ${items.size - 2}"
        }

        val totalWeight = items.sumOf { it.quantityInGrams }
        val totalCalories = items.sumOf { item ->
            val food = foodsById[item.foodId] ?: return@sumOf 0.0
            food.caloriesPer100g * item.quantityInGrams / 100.0
        }
        val lastUsedAt = sessions.maxOf { it.lastUsedAt }
        val id = "${sessions.first().mealType.name}:${buildTemplateSignature(items.map { it.foodId })}"

        return HistoryMealTemplate(
            id = id,
            mealType = sessions.first().mealType,
            title = title,
            items = items,
            occurrencesCount = sessions.size,
            totalWeightInGrams = totalWeight,
            totalCalories = totalCalories,
            lastUsedAt = lastUsedAt,
        )
    }

    private fun buildTemplateSignature(foodIds: Iterable<String>): String {
        return foodIds
            .toSet()
            .sorted()
            .joinToString("|")
    }

    private fun startOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private data class Session(
        val mealType: MealType,
        val signature: String,
        val items: List<SessionItem>,
        val foodIds: Set<String>,
        val lastUsedAt: Long,
    )

    private data class SessionItem(
        val foodId: String,
        val quantityInGrams: Double,
    )

    private companion object {
        const val HISTORY_WINDOW_MS = 90L * 24L * 60L * 60L * 1000L
        const val DEFAULT_LIMIT = 6
        const val MIN_OCCURRENCES = 2
        const val MIN_SHARED_ITEMS_FOR_SOFT_MATCH = 2
        const val MIN_OVERLAP_RATIO = 0.6
        const val MIN_SESSION_COVERAGE_RATIO = 0.5
    }
}
