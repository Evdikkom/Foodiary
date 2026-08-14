package com.example.foodiary.data.local.seed

import com.example.foodiary.data.model.AllergenPresenceType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedCatalogTest {

    @Test
    fun `seed food catalog is broad and has stable unique ids`() {
        val foods = SeedFoodCatalog.foods

        assertTrue(foods.size >= 120)
        assertEquals(foods.size, foods.map { it.id }.distinct().size)
        assertTrue(foods.all { it.id.isNotBlank() })
        assertTrue(foods.all { it.name.isNotBlank() })
        assertTrue(foods.all { !it.imageUrl.isNullOrBlank() })
    }

    @Test
    fun `seed foods cover core recommendation categories`() {
        val categories = SeedFoodCatalog.foods.groupingBy { it.category }.eachCount()

        assertTrue(categories.getValue("protein") >= 20)
        assertTrue(categories.getValue("grain") >= 15)
        assertTrue(categories.getValue("fruit") >= 20)
        assertTrue(categories.getValue("vegetable") >= 15)
        assertTrue(categories.getValue("dairy") >= 10)
        assertTrue(categories.getValue("nuts") >= 10)
    }

    @Test
    fun `every seed food has its own lightweight local image`() {
        val foods = SeedFoodCatalog.foods
        val imageDirectory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("app/src/main/res/drawable-nodpi")
        ).first { it.exists() }

        assertTrue(foods.all { it.imageUrl == "drawable://seed_${it.id}" })

        val missingImages = foods
            .map { it.id }
            .filterNot { id -> File(imageDirectory, "seed_$id.jpg").exists() }

        val oversizedImages = foods
            .map { it.id to File(imageDirectory, "seed_${it.id}.jpg") }
            .filter { (_, file) -> file.length() > 80 * 1024 }
            .map { (id, file) -> "$id=${file.length() / 1024}KB" }

        assertTrue("Missing seed image assets: $missingImages", missingImages.isEmpty())
        assertTrue("Oversized seed image assets: $oversizedImages", oversizedImages.isEmpty())
    }

    @Test
    fun `seed food nutrition values stay in plausible per 100 gram ranges`() {
        assertTrue(
            SeedFoodCatalog.foods.all { food ->
                food.caloriesPer100g in 0.0..900.0 &&
                    food.proteinPer100g in 0.0..100.0 &&
                    food.fatPer100g in 0.0..100.0 &&
                    food.carbsPer100g in 0.0..100.0
            }
        )
    }

    @Test
    fun `audited seed foods keep source-backed per 100 gram values`() {
        val foods = SeedFoodCatalog.foods.associateBy { it.id }

        assertNutrition(foods, "buckwheat", 92.0, 3.4, 0.6, 19.9)
        assertNutrition(foods, "pasta", 158.0, 5.8, 0.9, 30.9)
        assertNutrition(foods, "whole_wheat_pasta", 149.0, 6.0, 1.7, 30.1)
        assertNutrition(foods, "mozzarella", 299.0, 22.2, 22.1, 2.4)
        assertNutrition(foods, "chips", 532.0, 6.4, 34.0, 53.8)
    }

    @Test
    fun `seed allergen links reference existing foods and allergens`() {
        val foodIds = SeedFoodCatalog.foodIds
        val allergenIds = AllergenCatalog.allergens.map { it.id }.toSet()

        assertTrue(AllergenCatalog.seedFoodAllergens.keys.all { it in foodIds })
        assertTrue(
            AllergenCatalog.seedFoodAllergens.values
                .flatten()
                .all { (allergenId, presenceType) ->
                    allergenId in allergenIds &&
                        presenceType in setOf(
                            AllergenPresenceType.CONTAINS,
                            AllergenPresenceType.MAY_CONTAIN
                        )
                }
        )
    }

    @Test
    fun `name inference catches practical user restrictions`() {
        val hits = AllergenCatalog.inferFromNames(
            listOf("Шоколадное печенье с апельсином и арахисом")
        ).map { it.allergenId }.toSet()

        assertTrue(AllergenCatalog.SWEETS in hits)
        assertTrue(AllergenCatalog.COCOA in hits)
        assertTrue(AllergenCatalog.CITRUS in hits)
        assertTrue(AllergenCatalog.PEANUTS in hits)
        assertFalse(AllergenCatalog.FISH in hits)
    }

    private fun assertNutrition(
        foods: Map<String, com.example.foodiary.data.local.entity.FoodEntity>,
        id: String,
        calories: Double,
        protein: Double,
        fat: Double,
        carbs: Double
    ) {
        val food = foods.getValue(id)

        assertEquals(calories, food.caloriesPer100g, 0.01)
        assertEquals(protein, food.proteinPer100g, 0.01)
        assertEquals(fat, food.fatPer100g, 0.01)
        assertEquals(carbs, food.carbsPer100g, 0.01)
    }
}
