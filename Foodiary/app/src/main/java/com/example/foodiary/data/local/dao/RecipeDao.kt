package com.example.foodiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodiary.data.local.entity.RecipeEntity
import com.example.foodiary.data.local.entity.RecipeIngredientEntity

@Dao
interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Query("SELECT * FROM recipes WHERE foodId = :foodId LIMIT 1")
    suspend fun getRecipeByFoodId(foodId: String): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position ASC")
    suspend fun getIngredientsForRecipe(recipeId: String): List<RecipeIngredientEntity>

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: String)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipeById(recipeId: String)

    @Query("DELETE FROM recipes WHERE foodId = :foodId")
    suspend fun deleteRecipeByFoodId(foodId: String)
}
