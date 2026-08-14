package com.example.foodiary.testing

import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.User
import com.example.foodiary.domain.model.UserRestriction
import com.example.foodiary.domain.repository.AllergenRepository
import com.example.foodiary.domain.repository.FavoriteFoodsRepository
import com.example.foodiary.domain.repository.FoodRepository
import com.example.foodiary.domain.repository.MealRepository
import com.example.foodiary.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeFoodRepository(
    foods: List<Food> = emptyList()
) : FoodRepository {

    private val foodsById = linkedMapOf<String, Food>().apply {
        foods.forEach { put(it.id, it) }
    }

    fun replaceFoods(foods: List<Food>) {
        foodsById.clear()
        foods.forEach { foodsById[it.id] = it }
    }

    override suspend fun getFoodById(foodId: String): Food {
        return foodsById[foodId] ?: error("Food not found: $foodId")
    }

    override suspend fun getFoodsByIds(foodIds: List<String>): List<Food> {
        return foodIds.mapNotNull(foodsById::get)
    }

    override suspend fun getCustomFoods(limit: Int): List<Food> {
        return foodsById.values.filter { it.isCustom }.take(limit)
    }

    override suspend fun getFoodsForRecommendationPool(limit: Int): List<Food> {
        return foodsById.values.take(limit)
    }

    override fun searchFoods(query: String): Flow<List<Food>> {
        return flowOf(
            foodsById.values.filter {
                it.name.contains(query, ignoreCase = true)
            }
        )
    }

    override fun getRecommendedFoods(limit: Int): Flow<List<Food>> {
        return flowOf(foodsById.values.take(limit))
    }
}

class FakeMealRepository(
    meals: List<Meal> = emptyList(),
    private var dailyNutritionOverride: DailyNutrition? = null
) : MealRepository {

    private val storedMeals = meals.toMutableList()

    fun replaceMeals(meals: List<Meal>) {
        storedMeals.clear()
        storedMeals += meals
    }

    fun setDailyNutritionOverride(value: DailyNutrition?) {
        dailyNutritionOverride = value
    }

    override suspend fun getMealsForPeriod(startTimestamp: Long, endTimestamp: Long): List<Meal> {
        return storedMeals.filter { it.timestamp >= startTimestamp && it.timestamp < endTimestamp }
    }

    override suspend fun getMealById(mealId: Long): Meal? {
        return storedMeals.firstOrNull { it.id == mealId }
    }

    override suspend fun getDailyNutrition(startOfDay: Long, endOfDay: Long): DailyNutrition {
        return dailyNutritionOverride
            ?: DailyNutrition.fromMeals(getMealsForPeriod(startOfDay, endOfDay))
    }

    override suspend fun addMeal(meal: Meal): Long {
        val nextId = (storedMeals.maxOfOrNull { it.id } ?: 0L) + 1L
        storedMeals += meal.copy(id = nextId)
        return nextId
    }

    override suspend fun updateMeal(meal: Meal): Long {
        val index = storedMeals.indexOfFirst { it.id == meal.id }
        if (index >= 0) {
            storedMeals[index] = meal
        } else {
            storedMeals += meal
        }
        return meal.id
    }

    override suspend fun deleteMeal(mealId: Long) {
        storedMeals.removeAll { it.id == mealId }
    }
}

class FakeUserRepository(
    var currentUser: User? = null
) : UserRepository {

    override suspend fun getCurrentUser(): User? = currentUser

    override suspend fun saveCurrentUser(user: User) {
        currentUser = user
    }
}

class FakeFavoriteFoodsRepository(
    var storedFavoriteFoodIds: Set<String> = emptySet()
) : FavoriteFoodsRepository {

    override fun getFavoriteFoodIds(): Set<String> = storedFavoriteFoodIds
}

class FakeAllergenRepository : AllergenRepository {

    var allergens: List<Allergen> = emptyList()
    var restrictions: List<UserRestriction> = emptyList()
    var profilesByFoodId: Map<String, FoodSafetyProfile> = emptyMap()

    override suspend fun getAllergens(): List<Allergen> = allergens

    override suspend fun getUserRestrictions(userId: String): List<UserRestriction> = restrictions

    override suspend fun replaceUserRestrictions(userId: String, restrictions: List<UserRestriction>) {
        this.restrictions = restrictions
    }

    override suspend fun getFoodSafetyProfile(
        foodId: String,
        foodName: String,
        ingredientHints: List<String>
    ): FoodSafetyProfile {
        return profilesByFoodId[foodId] ?: FoodSafetyProfile()
    }

    override suspend fun getFoodSafetyProfiles(foods: List<Food>): Map<String, FoodSafetyProfile> {
        return foods.associate { food ->
            food.id to (profilesByFoodId[food.id] ?: FoodSafetyProfile())
        }
    }

    override suspend fun replaceManualFoodAllergens(
        foodId: String,
        allergens: Map<String, AllergenPresenceType>
    ) = Unit

    override suspend fun applyImportedAllergens(
        foodId: String,
        foodName: String,
        allergenTags: List<String>,
        traceTags: List<String>
    ) = Unit

    override suspend fun deriveRecipeAllergens(
        recipeFoodId: String,
        ingredientFoods: List<Food>
    ) = Unit

    override suspend fun applyInferredFoodAllergens(
        foodId: String,
        names: List<String>,
        ingredientHints: List<String>
    ) = Unit

    override suspend fun deleteFoodAllergens(foodId: String) = Unit
}
