package com.example.foodiary.presentation.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.mapper.toDomain
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Locale

class RecipeDetailsBottomSheet : DialogFragment() {

    companion object {
        private const val ARG_FOOD_ID = "arg_food_id"

        fun newInstance(foodId: String): RecipeDetailsBottomSheet = RecipeDetailsBottomSheet().apply {
            arguments = bundleOf(ARG_FOOD_ID to foodId)
        }
    }

    private val foodId: String by lazy { arguments?.getString(ARG_FOOD_ID).orEmpty() }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.setBackgroundBlurRadius(22)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_recipe_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRecipe(view)
    }

    private fun loadRecipe(root: View) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val recipe = db.recipeDao().getRecipeByFoodId(foodId) ?: return@launch
            val ingredients = db.recipeDao().getIngredientsForRecipe(recipe.id)

            root.findViewById<TextView>(R.id.textRecipeTitle).text = recipe.name
            root.findViewById<TextView>(R.id.textTotalWeight).text = formatWeight(recipe.totalWeightInGrams)
            root.findViewById<TextView>(R.id.textServingWeight).text = formatWeight(recipe.servingWeightInGrams)
            root.findViewById<TextView>(R.id.textIngredientsSummary).text = when (ingredients.size) {
                0 -> "Ингредиенты не найдены"
                1 -> "1 ингредиент"
                in 2..4 -> "${ingredients.size} ингредиента"
                else -> "${ingredients.size} ингредиентов"
            }

            bindImage(root.findViewById(R.id.imageRecipe), recipe.imageUrl)

            val description = recipe.description.trim()
            root.findViewById<TextView>(R.id.textDescriptionTitle).visibility = if (description.isBlank()) View.GONE else View.VISIBLE
            root.findViewById<TextView>(R.id.textDescription).apply {
                visibility = if (description.isBlank()) View.GONE else View.VISIBLE
                text = description
            }

            val container = root.findViewById<LinearLayout>(R.id.layoutIngredients)
            container.removeAllViews()
            ingredients.forEach { item ->
                val food = db.foodDao().getFoodById(item.foodId)?.toDomain() ?: return@forEach
                val card = layoutInflater.inflate(R.layout.item_recipe_ingredient, container, false)
                card.findViewById<TextView>(R.id.textIngredientName).text = food.name
                card.findViewById<TextView>(R.id.textIngredientMeta).text = "${formatWeight(item.grams)}, ${format(food.caloriesPer100g * item.grams / 100.0)} ккал"
                card.findViewById<View>(R.id.editIngredientWeight).visibility = View.GONE
                card.findViewById<View>(R.id.buttonRemoveIngredient).visibility = View.GONE
                bindImage(card.findViewById(R.id.imageIngredient), food.imageUrl)
                container.addView(card)
            }
        }
    }

    private fun bindImage(imageView: ImageView, ref: String?) {
        val normalized = ref?.trim().orEmpty()
        if (normalized.startsWith("drawable://")) {
            val resId = resources.getIdentifier(normalized.removePrefix("drawable://"), "drawable", requireContext().packageName)
            if (resId != 0) imageView.setImageResource(resId) else imageView.setImageResource(R.drawable.ic_custom_dish_placeholder)
        } else if (normalized.isBlank()) {
            imageView.setImageResource(R.drawable.ic_custom_dish_placeholder)
        } else {
            imageView.load(normalized)
        }
    }

    private fun formatWeight(value: Double): String = "${format(value)} г"

    private fun format(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
}
