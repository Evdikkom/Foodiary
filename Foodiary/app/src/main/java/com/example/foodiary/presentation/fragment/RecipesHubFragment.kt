package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RecipesHubFragment : Fragment(R.layout.fragment_recipes_hub) {

    private lateinit var foodRepository: FoodRepositoryImpl

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        foodRepository = FoodRepositoryImpl(AppDatabase.getInstance(requireContext()).foodDao())

        view.findViewById<View>(R.id.buttonCreateRecipe).setDebouncedClickListener {
            replaceFragmentSafely(
                CreateRecipeFragment.newInstance(MealType.BREAKFAST),
                motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y
            )
        }
        view.findViewById<View>(R.id.buttonCreateCustomFood).setDebouncedClickListener {
            replaceFragmentSafely(
                CreateCustomFoodFragment.newInstance(MealType.BREAKFAST),
                motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y
            )
        }

        loadEntries(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadEntries(it) }
    }

    private fun loadEntries(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = foodRepository.getCustomFoods(limit = 200)
            val recipes = items.filter { it.category == "custom_recipe" }
            val foods = items.filter { it.category != "custom_recipe" }
            renderRows(
                container = root.findViewById(R.id.layoutRecipeRows),
                emptyText = root.findViewById(R.id.textRecipesEmpty),
                items = recipes
            )
            renderRows(
                container = root.findViewById(R.id.layoutCustomFoodRows),
                emptyText = root.findViewById(R.id.textCustomFoodsEmpty),
                items = foods
            )
        }
    }

    private fun renderRows(container: LinearLayout, emptyText: TextView, items: List<Food>) {
        container.removeAllViews()
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { food ->
            val row = layoutInflater.inflate(R.layout.item_recipe_hub_food, container, false)
            row.findViewById<TextView>(R.id.textFoodName).text = food.name
            row.findViewById<TextView>(R.id.textFoodMeta).text =
                "${food.caloriesPer100g.roundToInt()} ккал, Б ${format(food.proteinPer100g)} г, Ж ${format(food.fatPer100g)} г, У ${format(food.carbsPer100g)} г"
            row.setDebouncedClickListener {
                val fragment = if (food.category == "custom_recipe") {
                    CreateRecipeFragment.newEditInstance(MealType.BREAKFAST, food.id)
                } else {
                    CreateCustomFoodFragment.newEditInstance(MealType.BREAKFAST, food.id)
                }
                replaceFragmentSafely(fragment, motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y)
            }
            container.addView(row)
        }
    }

    private fun format(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
