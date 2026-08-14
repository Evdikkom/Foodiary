package com.example.foodiary.presentation.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.RecipeEntity
import com.example.foodiary.data.local.entity.RecipeIngredientEntity
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.dialog.IngredientPickerDialogFragment
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import java.util.Locale

class CreateRecipeFragment : Fragment(R.layout.fragment_create_recipe) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_EDIT_FOOD_ID = "arg_edit_food_id"

        fun newInstance(mealType: MealType): CreateRecipeFragment {
            return CreateRecipeFragment().apply {
                arguments = Bundle().apply { putString(ARG_MEAL_TYPE, mealType.name) }
            }
        }

        fun newEditInstance(mealType: MealType, foodId: String): CreateRecipeFragment {
            return CreateRecipeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putString(ARG_EDIT_FOOD_ID, foodId)
                }
            }
        }
    }

    private enum class SummaryMode { TOTAL, PER_100, PORTION }
    private data class RecipeIngredient(val food: Food, var grams: Double)
    private data class Totals(val calories: Double, val protein: Double, val fat: Double, val carbs: Double)

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }
    private val editFoodId: String? by lazy { arguments?.getString(ARG_EDIT_FOOD_ID) }
    private lateinit var allergenRepository: AllergenRepositoryImpl

    private val ingredients = mutableListOf<RecipeIngredient>()
    private var summaryMode = SummaryMode.TOTAL
    private var recipeRecordId: String? = null
    private var selectedImageUri: String? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectedImageUri = uri.toString()
        view?.findViewById<ImageView>(R.id.imageRecipeCover)?.apply {
            imageTintList = null
            load(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = AppDatabase.getInstance(requireContext())
        allergenRepository = AllergenRepositoryImpl(
            allergenDao = database.allergenDao(),
            foodAllergenDao = database.foodAllergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )

        view.findViewById<TextView>(R.id.textScreenTitle).text =
            if (editFoodId == null) "Создать рецепт блюда" else "Редактировать рецепт блюда"
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmExitIfNeeded(view)
                }
            }
        )

        view.findViewById<ImageView>(R.id.buttonClose).setDebouncedClickListener {
            confirmExitIfNeeded(view)
        }
        view.findViewById<TextView>(R.id.textMealCaption).text =
            "Блюдо сохранится как рецепт и будет доступно для дневника, добавления в приём пищи и рекомендаций"
        view.findViewById<TextView>(R.id.textHelpIngredients).setOnClickListener {
            Toast.makeText(requireContext(), "Добавляйте ингредиенты по одному. Если вес готового блюда оставить пустым, Foodiary возьмёт сумму ингредиентов.", Toast.LENGTH_LONG).show()
        }

        val openIngredientPicker = {
            IngredientPickerDialogFragment.newInstance().show(parentFragmentManager, "ingredient_picker")
        }
        view.findViewById<Button>(R.id.buttonAddIngredient).setDebouncedClickListener { openIngredientPicker() }
        view.findViewById<Button>(R.id.buttonAddIngredientInline).setDebouncedClickListener { openIngredientPicker() }
        view.findViewById<View>(R.id.cardRecipeImagePicker).setOnClickListener { imagePicker.launch(arrayOf("image/*")) }

        view.findViewById<TextView>(R.id.chipTotal).setOnClickListener { summaryMode = SummaryMode.TOTAL; renderSummaryMode(view) }
        view.findViewById<TextView>(R.id.chipPer100).setOnClickListener { summaryMode = SummaryMode.PER_100; renderSummaryMode(view) }
        view.findViewById<TextView>(R.id.chipPortion).setOnClickListener { summaryMode = SummaryMode.PORTION; renderSummaryMode(view) }

        view.findViewById<TextView>(R.id.chipServing100).setOnClickListener { setServingWeight(view, 100.0) }
        view.findViewById<TextView>(R.id.chipServing150).setOnClickListener { setServingWeight(view, 150.0) }
        view.findViewById<TextView>(R.id.chipServing250).setOnClickListener { setServingWeight(view, 250.0) }

        view.findViewById<EditText>(R.id.editServingWeight).setText("100")
        view.findViewById<EditText>(R.id.editRecipeName).doAfterTextChanged { validate(view) }
        view.findViewById<EditText>(R.id.editServingWeight).doAfterTextChanged {
            updateSelectedServingChip(view)
            renderSummary(view)
            validate(view)
        }
        view.findViewById<EditText>(R.id.editCookedWeight).doAfterTextChanged {
            renderSummary(view)
            validate(view)
        }
        view.findViewById<EditText>(R.id.editDescription).doAfterTextChanged {
            view.findViewById<TextView>(R.id.textDescriptionCounter).text = "${it?.length ?: 0}/1000"
        }
        view.findViewById<Button>(R.id.buttonSaveRecipe).setDebouncedClickListener { saveRecipe(view) }

        parentFragmentManager.setFragmentResultListener(IngredientPickerDialogFragment.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val foodId = bundle.getString(IngredientPickerDialogFragment.RESULT_FOOD_ID).orEmpty()
            if (foodId.isNotBlank()) addIngredient(foodId, view)
        }

        renderSummaryMode(view)
        renderIngredients(view)
        refreshDerivedAllergenSummary(view)
        if (editFoodId != null) preloadRecipe(view) else validate(view)
    }

    private fun preloadRecipe(root: View) {
        val foodId = editFoodId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val food = db.foodDao().getFoodById(foodId) ?: return@launch
            val recipe = db.recipeDao().getRecipeByFoodId(foodId) ?: return@launch
            recipeRecordId = recipe.id
            root.findViewById<EditText>(R.id.editRecipeName).setText(food.name)
            root.findViewById<EditText>(R.id.editServingWeight).setText(format(recipe.servingWeightInGrams))
            root.findViewById<EditText>(R.id.editCookedWeight).setText(format(recipe.totalWeightInGrams))
            root.findViewById<EditText>(R.id.editDescription).setText(recipe.description)
            selectedImageUri = recipe.imageUrl?.takeUnless { it.startsWith("drawable://") }
            if (selectedImageUri != null) {
                root.findViewById<ImageView>(R.id.imageRecipeCover).apply {
                    imageTintList = null
                    load(selectedImageUri)
                }
            } else {
                bindLocalDrawable(root.findViewById(R.id.imageRecipeCover), recipe.imageUrl)
            }

            ingredients.clear()
            db.recipeDao().getIngredientsForRecipe(recipe.id).forEach { item ->
                val ingredientFood = db.foodDao().getFoodById(item.foodId)?.toDomain() ?: return@forEach
                ingredients += RecipeIngredient(ingredientFood, item.grams)
            }
            renderIngredients(root)
            updateSelectedServingChip(root)
            renderSummary(root)
            refreshDerivedAllergenSummary(root)
            validate(root)
        }
    }

    private fun confirmExitIfNeeded(root: View) {
        if (!hasUnsavedChanges(root)) {
            popBackStackSafely()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Выйти без сохранения?")
            .setMessage("Введённые данные рецепта не будут сохранены.")
            .setPositiveButton("Выйти") { _, _ -> popBackStackSafely() }
            .setNegativeButton("Остаться", null)
            .show()
    }

    private fun hasUnsavedChanges(root: View): Boolean {
        if (editFoodId != null) return true
        val name = root.findViewById<EditText>(R.id.editRecipeName).text?.toString().orEmpty().trim()
        val description = root.findViewById<EditText>(R.id.editDescription).text?.toString().orEmpty().trim()
        val serving = root.findViewById<EditText>(R.id.editServingWeight).text?.toString().orEmpty().trim()
        val cooked = root.findViewById<EditText>(R.id.editCookedWeight).text?.toString().orEmpty().trim()
        return name.isNotBlank() ||
            description.isNotBlank() ||
            selectedImageUri != null ||
            ingredients.isNotEmpty() ||
            serving != "100" ||
            cooked.isNotBlank()
    }

    private fun addIngredient(foodId: String, root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val entity = AppDatabase.getInstance(requireContext()).foodDao().getFoodById(foodId)
            val food = entity?.toDomain() ?: return@launch
            val existing = ingredients.firstOrNull { it.food.id == food.id }
            if (existing != null) {
                existing.grams += 100.0
            } else {
                ingredients += RecipeIngredient(food, 100.0)
            }
            renderIngredients(root)
            renderSummary(root)
            refreshDerivedAllergenSummary(root)
            validate(root)
        }
    }

    private fun renderIngredients(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.layoutIngredientsContainer)
        val empty = root.findViewById<View>(R.id.cardEmptyIngredients)
        val subtitle = root.findViewById<TextView>(R.id.textIngredientsCaption)
        container.removeAllViews()
        subtitle.text = if (ingredients.isEmpty()) {
            "Добавьте продукты, из которых состоит блюдо"
        } else {
            "Ингредиентов: ${ingredients.size}, можно менять вес и удалять лишнее"
        }

        if (ingredients.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        ingredients.forEachIndexed { index, ingredient ->
            val item = layoutInflater.inflate(R.layout.item_recipe_ingredient, container, false)
            item.findViewById<TextView>(R.id.textIngredientName).text = ingredient.food.name
            item.findViewById<TextView>(R.id.textIngredientMeta).text = buildString {
                append("${format(ingredient.food.caloriesPer100g)} ккал / 100 г")
                if (ingredient.food.isCustom) {
                    append(", ")
                    append(if (ingredient.food.category == "custom_recipe") "Блюдо" else "Мой")
                }
            }
            bindLocalDrawable(item.findViewById(R.id.imageIngredient), ingredient.food.imageUrl)
            val editWeight = item.findViewById<EditText>(R.id.editIngredientWeight)
            editWeight.setText(format(ingredient.grams))
            editWeight.doAfterTextChanged {
                val value = it?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                ingredients.getOrNull(index)?.grams = value.coerceAtLeast(0.0)
                syncCookedWeightIfNeeded(root)
                renderSummary(root)
                validate(root)
            }
            item.findViewById<View>(R.id.buttonRemoveIngredient).setOnClickListener {
                ingredients.removeAt(index)
                syncCookedWeightIfNeeded(root, force = true)
                renderIngredients(root)
                renderSummary(root)
                refreshDerivedAllergenSummary(root)
                validate(root)
            }
            container.addView(item)
        }
    }

    private fun renderSummaryMode(root: View) {
        mapOf(
            R.id.chipTotal to (summaryMode == SummaryMode.TOTAL),
            R.id.chipPer100 to (summaryMode == SummaryMode.PER_100),
            R.id.chipPortion to (summaryMode == SummaryMode.PORTION)
        ).forEach { (id, selected) ->
            root.findViewById<TextView>(id).setBackgroundResource(
                if (selected) R.drawable.bg_product_config_portion_chip_selected else R.drawable.bg_product_config_portion_chip
            )
        }
        renderSummary(root)
    }

    private fun renderSummary(root: View) {
        val totals = calculateTotals()
        val ingredientWeight = ingredients.sumOf { it.grams }
        val cookedWeight = parse(root.findViewById(R.id.editCookedWeight)).takeIf { it > 0 } ?: ingredientWeight
        val portionWeight = parse(root.findViewById(R.id.editServingWeight)).takeIf { it > 0 } ?: 100.0

        root.findViewById<TextView>(R.id.textIngredientWeightValue).text = "${format(ingredientWeight)} г"
        root.findViewById<TextView>(R.id.textHeaderLabel).text = when (summaryMode) {
            SummaryMode.TOTAL -> "Всего"
            SummaryMode.PER_100 -> "На 100 г"
            SummaryMode.PORTION -> "Порция ${format(portionWeight)} г"
        }

        val factor = when (summaryMode) {
            SummaryMode.TOTAL -> 1.0
            SummaryMode.PER_100 -> if (cookedWeight > 0) 100.0 / cookedWeight else 0.0
            SummaryMode.PORTION -> if (cookedWeight > 0) portionWeight / cookedWeight else 0.0
        }

        root.findViewById<TextView>(R.id.textSummaryCalories).text = "${format(totals.calories * factor)} ккал"
        root.findViewById<TextView>(R.id.textSummaryProtein).text = "${format(totals.protein * factor)} г"
        root.findViewById<TextView>(R.id.textSummaryFat).text = "${format(totals.fat * factor)} г"
        root.findViewById<TextView>(R.id.textSummaryCarbs).text = "${format(totals.carbs * factor)} г"
    }

    private fun calculateTotals(): Totals {
        var calories = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0

        ingredients.forEach { ingredient ->
            val factor = ingredient.grams / 100.0
            calories += ingredient.food.caloriesPer100g * factor
            protein += ingredient.food.proteinPer100g * factor
            fat += ingredient.food.fatPer100g * factor
            carbs += ingredient.food.carbsPer100g * factor
        }

        return Totals(calories, protein, fat, carbs)
    }

    private fun validate(root: View): Boolean {
        val isValid = root.findViewById<EditText>(R.id.editRecipeName).text?.toString().orEmpty().trim().isNotBlank() &&
            ingredients.isNotEmpty() &&
            parse(root.findViewById(R.id.editServingWeight)) > 0.0 &&
            parse(root.findViewById(R.id.editCookedWeight)) > 0.0

        root.findViewById<Button>(R.id.buttonSaveRecipe).isEnabled = isValid
        root.findViewById<TextView>(R.id.textError).visibility = View.GONE
        return isValid
    }

    private fun saveRecipe(root: View) {
        if (!validate(root)) {
            root.findViewById<TextView>(R.id.textError).apply {
                visibility = View.VISIBLE
                text = "Укажите название, ингредиенты, вес готового блюда и вес порции"
            }
            return
        }

        val db = AppDatabase.getInstance(requireContext())
        val progress = root.findViewById<ProgressBar>(R.id.progressSaveRecipe)
        val button = root.findViewById<Button>(R.id.buttonSaveRecipe)
        val foodId = editFoodId ?: "custom_recipe_food_${System.currentTimeMillis()}"
        val recipeId = recipeRecordId ?: "custom_recipe_${System.currentTimeMillis()}"
        val name = root.findViewById<EditText>(R.id.editRecipeName).text?.toString().orEmpty().trim()
        val description = root.findViewById<EditText>(R.id.editDescription).text?.toString().orEmpty().trim()
        val cookedWeight = parse(root.findViewById(R.id.editCookedWeight))
        val servingWeight = parse(root.findViewById(R.id.editServingWeight))
        val totals = calculateTotals()
        val per100Factor = if (cookedWeight > 0) 100.0 / cookedWeight else 0.0

        val food = FoodEntity(
            id = foodId,
            name = name,
            imageUrl = selectedImageUri ?: "drawable://ic_custom_dish_placeholder",
            caloriesPer100g = totals.calories * per100Factor,
            proteinPer100g = totals.protein * per100Factor,
            fatPer100g = totals.fat * per100Factor,
            carbsPer100g = totals.carbs * per100Factor,
            category = "custom_recipe",
            isCustom = true
        )

        val recipe = RecipeEntity(
            id = recipeId,
            foodId = foodId,
            name = name,
            imageUrl = food.imageUrl,
            description = description,
            totalWeightInGrams = cookedWeight,
            servingWeightInGrams = servingWeight
        )

        val recipeItems = ingredients.mapIndexed { index, ingredient ->
            RecipeIngredientEntity(
                recipeId = recipeId,
                foodId = ingredient.food.id,
                grams = ingredient.grams,
                position = index
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            button.isEnabled = false
            try {
                db.foodDao().insertFood(food)
                db.recipeDao().deleteIngredientsForRecipe(recipeId)
                db.recipeDao().insertRecipe(recipe)
                db.recipeDao().insertIngredients(recipeItems)
                allergenRepository.deriveRecipeAllergens(
                    recipeFoodId = food.id,
                    ingredientFoods = ingredients.map { it.food }
                )
                Toast.makeText(
                    requireContext(),
                    if (editFoodId == null) "Блюдо сохранено" else "Блюдо обновлено",
                    Toast.LENGTH_SHORT
                ).show()
                popBackStackSafely()
            } catch (e: Exception) {
                root.findViewById<TextView>(R.id.textError).apply {
                    visibility = View.VISIBLE
                    text = e.message ?: "Не удалось сохранить блюдо"
                }
            } finally {
                if (view != null) {
                    progress.visibility = View.GONE
                    button.isEnabled = true
                }
            }
        }
    }

    private fun refreshDerivedAllergenSummary(root: View) {
        if (ingredients.isEmpty()) {
            root.findViewById<View>(R.id.cardRecipeAllergenSummary).visibility = View.GONE
            root.findViewById<TextView>(R.id.textRecipeAllergenSummary).text = ""
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val profiles = allergenRepository.getFoodSafetyProfiles(ingredients.map { it.food })
            val confirmed = linkedSetOf<String>()
            val inferred = linkedSetOf<String>()

            profiles.values.forEach { profile ->
                profile.confirmedAllergens.forEach { confirmed += it.allergen.displayName }
                profile.inferredAllergens.forEach { inferred += it.allergen.displayName }
            }

            val possibleOnly = inferred.filterNot { it in confirmed.toSet() }
            val summary = buildList {
                if (confirmed.isNotEmpty()) {
                    add("Подтвержденные: ${confirmed.joinToString(", ")}.")
                }
                if (possibleOnly.isNotEmpty()) {
                    add("Возможные по составу или названию ингредиентов: ${possibleOnly.joinToString(", ")}.")
                }
            }.joinToString(" ")

            if (summary.isBlank()) {
                root.findViewById<View>(R.id.cardRecipeAllergenSummary).visibility = View.GONE
                root.findViewById<TextView>(R.id.textRecipeAllergenSummary).text = ""
            } else {
                root.findViewById<View>(R.id.cardRecipeAllergenSummary).visibility = View.VISIBLE
                root.findViewById<TextView>(R.id.textRecipeAllergenSummary).text =
                    "$summary При сохранении эти данные будут унаследованы итоговым блюдом."
            }
        }
    }

    private fun syncCookedWeightIfNeeded(root: View, force: Boolean = false) {
        val input = root.findViewById<EditText>(R.id.editCookedWeight)
        val current = parse(input)
        val sum = ingredients.sumOf { it.grams }
        if (force || current <= 0.0 || current == 100.0 || current == sum - 100.0 || current == sum + 100.0) {
            input.setText(format(sum))
            input.setSelection(input.text?.length ?: 0)
        }
    }

    private fun setServingWeight(root: View, value: Double) {
        val input = root.findViewById<EditText>(R.id.editServingWeight)
        input.setText(format(value))
        input.setSelection(input.text?.length ?: 0)
    }

    private fun updateSelectedServingChip(root: View) {
        val portion = parse(root.findViewById(R.id.editServingWeight))
        listOf(
            R.id.chipServing100 to 100.0,
            R.id.chipServing150 to 150.0,
            R.id.chipServing250 to 250.0
        ).forEach { (id, value) ->
            root.findViewById<TextView>(id).setBackgroundResource(
                if (portion == value) R.drawable.bg_product_config_portion_chip_selected else R.drawable.bg_product_config_portion_chip
            )
        }
    }

    private fun bindLocalDrawable(imageView: ImageView, imageRef: String?) {
        val ref = imageRef?.trim().orEmpty()
        if (ref.startsWith("drawable://")) {
            val resId = resources.getIdentifier(ref.removePrefix("drawable://"), "drawable", requireContext().packageName)
            if (resId != 0) {
                imageView.imageTintList = null
                imageView.setImageResource(resId)
                return
            }
        }
        if (ref.isNotBlank()) {
            imageView.imageTintList = null
            imageView.load(ref)
        } else {
            imageView.setImageResource(R.drawable.ic_custom_dish_placeholder)
            imageView.imageTintList = android.content.res.ColorStateList.valueOf(0xFF8B63D5.toInt())
        }
    }

    private fun parse(editText: EditText): Double {
        return editText.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    }

    private fun format(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
}
