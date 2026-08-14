package com.example.foodiary.data.repository

import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale

class FoodRepositoryImpl(
    private val foodDao: FoodDao
) : FoodRepository {

    override suspend fun getFoodById(foodId: String): Food {
        return foodDao.getFoodById(foodId)
            ?.toDomain()
            ?: throw IllegalStateException(
                "Food with id=$foodId not found in database"
            )
    }

    override suspend fun getFoodsByIds(foodIds: List<String>): List<Food> {
        if (foodIds.isEmpty()) return emptyList()
        return foodDao.getFoodsByIds(foodIds).map { it.toDomain() }
    }

    override suspend fun getCustomFoods(limit: Int): List<Food> {
        return foodDao.getCustomFoods(limit).map { it.toDomain() }
    }

    override suspend fun getFoodsForRecommendationPool(limit: Int): List<Food> {
        return foodDao.getFoodsForPicker(limit).map { it.toDomain() }
    }

    override fun searchFoods(query: String): Flow<List<Food>> {
        val normalizedQuery = query.trim()

        if (normalizedQuery.isBlank()) {
            return flowOf(emptyList())
        }

        val variants = buildSearchVariants(normalizedQuery)
        return foodDao.searchFoods(
            query = variants.getOrElse(0) { normalizedQuery },
            altQuery = variants.getOrElse(1) { normalizedQuery },
            thirdQuery = variants.getOrElse(2) { normalizedQuery }
        )
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getRecommendedFoods(limit: Int): Flow<List<Food>> {
        return foodDao.getRecommendedFoods(limit)
            .map { list -> list.map { it.toDomain() } }
    }

    private fun buildSearchVariants(query: String): List<String> {
        val variants = linkedSetOf(query)
        val lower = query.lowercase(Locale("ru"))
        commonAliases[lower]?.let(variants::add)
        transliterateLatinToRussian(lower)
            .takeIf { it.isNotBlank() && it != lower }
            ?.let(variants::add)
        return variants.take(3).ifEmpty { listOf(query) }
    }

    private fun transliterateLatinToRussian(raw: String): String {
        var text = raw
        listOf(
            "shch" to "щ",
            "sch" to "щ",
            "yo" to "ё",
            "zh" to "ж",
            "ch" to "ч",
            "sh" to "ш",
            "yu" to "ю",
            "ya" to "я",
            "kh" to "х",
            "ts" to "ц"
        ).forEach { (latin, cyrillic) ->
            text = text.replace(latin, cyrillic)
        }
        return buildString {
            text.forEach { char ->
                append(latinToCyrillic[char] ?: char)
            }
        }
    }

    private companion object {
        val commonAliases = mapOf(
            "ris" to "рис",
            "rice" to "рис",
            "grechka" to "гречка",
            "chicken" to "курица",
            "kurica" to "курица",
            "tvorog" to "творог",
            "oatmeal" to "овсянка",
            "ovsyanka" to "овсянка",
            "banana" to "банан",
            "banan" to "банан"
        )

        val latinToCyrillic = mapOf(
            'a' to 'а',
            'b' to 'б',
            'v' to 'в',
            'g' to 'г',
            'd' to 'д',
            'e' to 'е',
            'z' to 'з',
            'i' to 'и',
            'j' to 'й',
            'k' to 'к',
            'l' to 'л',
            'm' to 'м',
            'n' to 'н',
            'o' to 'о',
            'p' to 'п',
            'r' to 'р',
            's' to 'с',
            't' to 'т',
            'u' to 'у',
            'f' to 'ф',
            'h' to 'х',
            'y' to 'ы'
        )
    }
}
