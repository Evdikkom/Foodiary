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
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.model.AllergenPresenceType
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.domain.model.Allergen
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import java.util.Locale

class CreateCustomFoodFragment : Fragment(R.layout.fragment_create_custom_food) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_EDIT_FOOD_ID = "arg_edit_food_id"

        fun newInstance(mealType: MealType): CreateCustomFoodFragment {
            return CreateCustomFoodFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                }
            }
        }

        fun newEditInstance(mealType: MealType, foodId: String): CreateCustomFoodFragment {
            return CreateCustomFoodFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putString(ARG_EDIT_FOOD_ID, foodId)
                }
            }
        }
    }

    private enum class MeasureUnit(val shortLabel: String) {
        GRAM("г"),
        MILLILITER("мл")
    }

    private data class AllergenRowViews(
        val containsChip: TextView,
        val mayContainChip: TextView,
    )

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    private val editFoodId: String? by lazy { arguments?.getString(ARG_EDIT_FOOD_ID) }
    private var selectedUnit: MeasureUnit = MeasureUnit.GRAM
    private var selectedImageUri: String? = null
    private lateinit var allergenRepository: AllergenRepositoryImpl
    private var availableAllergens: List<Allergen> = emptyList()
    private val allergenSelections = linkedMapOf<String, AllergenPresenceType?>()
    private val allergenRows = linkedMapOf<String, AllergenRowViews>()
    private var allergenSectionExpanded = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        selectedImageUri = uri.toString()
        view?.findViewById<ImageView>(R.id.imageFoodCover)?.apply {
            imageTintList = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_CROP
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

        val baseWeight = view.findViewById<EditText>(R.id.editBaseWeight)
        val name = view.findViewById<EditText>(R.id.editFoodName)
        val calories = view.findViewById<EditText>(R.id.editCalories)
        val protein = view.findViewById<EditText>(R.id.editProtein)
        val fat = view.findViewById<EditText>(R.id.editFat)
        val carbs = view.findViewById<EditText>(R.id.editCarbs)
        val save = view.findViewById<Button>(R.id.buttonContinue)

        view.findViewById<TextView>(R.id.textScreenTitle).text =
            if (editFoodId == null) "Создать свой продукт" else "Редактировать продукт"
        view.findViewById<TextView>(R.id.textMealCaption).text =
            "Сохранится в «Мои продукты» и будет доступен на всех приёмах пищи"
        view.findViewById<TextView>(R.id.textHelp).setOnClickListener {
            Toast.makeText(requireContext(), "Укажите значения для выбранной базы: например, на 100 г или на 250 мл.", Toast.LENGTH_LONG).show()
        }

        baseWeight.setText("100")
        updateUnitUi(view)

        listOf(name, baseWeight, calories, protein, fat, carbs).forEach { input ->
            input.doAfterTextChanged { validate(view) }
        }

        view.findViewById<TextView>(R.id.chipGram).setOnClickListener {
            selectedUnit = MeasureUnit.GRAM
            updateUnitUi(view)
            validate(view)
        }

        view.findViewById<TextView>(R.id.chipMl).setOnClickListener {
            selectedUnit = MeasureUnit.MILLILITER
            updateUnitUi(view)
            validate(view)
        }

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

        view.findViewById<View>(R.id.layoutAllergenEditorHeader).setDebouncedClickListener {
            allergenSectionExpanded = !allergenSectionExpanded
            renderAllergenSectionState(view)
        }

        view.findViewById<View>(R.id.cardImagePicker).setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }

        save.setDebouncedClickListener { saveFood(view) }

        viewLifecycleOwner.lifecycleScope.launch {
            loadAllergenRows(view)
            if (editFoodId != null) {
                preloadFood(view)
            } else {
                validate(view)
            }
        }
    }

    private suspend fun loadAllergenRows(root: View) {
        availableAllergens = allergenRepository.getAllergens()
        val container = root.findViewById<LinearLayout>(R.id.layoutAllergenEditorRows)
        container.removeAllViews()
        allergenRows.clear()

        availableAllergens.forEach { allergen ->
            val row = layoutInflater.inflate(
                R.layout.item_food_allergen_editor,
                container,
                false
            )
            row.findViewById<TextView>(R.id.textAllergenName).text = allergen.displayName
            val containsChip = row.findViewById<TextView>(R.id.chipContains)
            val mayContainChip = row.findViewById<TextView>(R.id.chipMayContain)
            containsChip.setOnClickListener {
                toggleAllergen(allergen.id, AllergenPresenceType.CONTAINS)
            }
            mayContainChip.setOnClickListener {
                toggleAllergen(allergen.id, AllergenPresenceType.MAY_CONTAIN)
            }
            allergenRows[allergen.id] = AllergenRowViews(
                containsChip = containsChip,
                mayContainChip = mayContainChip
            )
            container.addView(row)
        }
        applyAllergenSelectionUi()
        refreshAllergenHint(root)
        renderAllergenSectionState(root)
    }

    private fun toggleAllergen(allergenId: String, presenceType: AllergenPresenceType) {
        val current = allergenSelections[allergenId]
        allergenSelections[allergenId] = if (current == presenceType) null else presenceType
        applyAllergenSelectionUi()
        refreshAllergenHint(requireView())
    }

    private fun applyAllergenSelectionUi() {
        allergenRows.forEach { (allergenId, row) ->
            val selected = allergenSelections[allergenId]
            row.containsChip.setBackgroundResource(
                if (selected == AllergenPresenceType.CONTAINS) {
                    R.drawable.bg_product_config_portion_chip_selected
                } else {
                    R.drawable.bg_product_config_portion_chip
                }
            )
            row.mayContainChip.setBackgroundResource(
                if (selected == AllergenPresenceType.MAY_CONTAIN) {
                    R.drawable.bg_product_config_portion_chip_selected
                } else {
                    R.drawable.bg_product_config_portion_chip
                }
            )
        }
    }

    private fun refreshAllergenHint(root: View) {
        val containsCount = allergenSelections.values.count { it == AllergenPresenceType.CONTAINS }
        val mayContainCount = allergenSelections.values.count { it == AllergenPresenceType.MAY_CONTAIN }
        root.findViewById<TextView>(R.id.textAllergenSummaryCompact).text = when {
            containsCount == 0 && mayContainCount == 0 ->
                "Опционально: можно пропустить"
            containsCount > 0 && mayContainCount > 0 ->
                "Выбрано: содержит — $containsCount, может содержать — $mayContainCount"
            containsCount > 0 ->
                "Выбрано: содержит — $containsCount"
            else ->
                "Выбрано: может содержать — $mayContainCount"
        }
        root.findViewById<TextView>(R.id.textAllergenEditorHint).text = when {
            containsCount == 0 && mayContainCount == 0 ->
                "Если ничего не отмечено, Foodiary попробует осторожно предположить возможные аллергены по названию продукта."
            else ->
                "Сейчас отмечено: содержит — $containsCount, может содержать — $mayContainCount. Эти данные будут использоваться в предупреждениях и фильтрации."
        }
    }

    private fun renderAllergenSectionState(root: View) {
        root.findViewById<View>(R.id.layoutAllergenEditorContent).isVisible = allergenSectionExpanded
        root.findViewById<TextView>(R.id.textAllergenToggle).text = if (allergenSectionExpanded) {
            "Свернуть"
        } else {
            "Развернуть"
        }
    }

    private suspend fun preloadFood(root: View) {
        val foodId = editFoodId ?: return
        val db = AppDatabase.getInstance(requireContext())
        val food = db.foodDao().getFoodById(foodId) ?: return
        root.findViewById<EditText>(R.id.editFoodName).setText(food.name)
        root.findViewById<EditText>(R.id.editBaseWeight).setText("100")
        root.findViewById<EditText>(R.id.editCalories).setText(format(food.caloriesPer100g))
        root.findViewById<EditText>(R.id.editProtein).setText(format(food.proteinPer100g))
        root.findViewById<EditText>(R.id.editFat).setText(format(food.fatPer100g))
        root.findViewById<EditText>(R.id.editCarbs).setText(format(food.carbsPer100g))
        selectedUnit = if (food.category.contains("_ml")) MeasureUnit.MILLILITER else MeasureUnit.GRAM
        updateUnitUi(root)
        selectedImageUri = food.imageUrl?.takeUnless { it.startsWith("drawable://") }
        selectedImageUri?.let {
            root.findViewById<ImageView>(R.id.imageFoodCover).apply {
                imageTintList = null
                setPadding(0, 0, 0, 0)
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(it)
            }
        }

        allergenSelections.clear()
        val profile = allergenRepository.getFoodSafetyProfile(food.id, food.name)
        profile.confirmedAllergens.forEach { allergen ->
            allergenSelections[allergen.allergen.id] = allergen.presenceType
        }
        allergenSectionExpanded = false
        applyAllergenSelectionUi()
        refreshAllergenHint(root)
        renderAllergenSectionState(root)
        validate(root)
    }

    private fun updateUnitUi(root: View) {
        root.findViewById<TextView>(R.id.chipGram).setBackgroundResource(
            if (selectedUnit == MeasureUnit.GRAM) R.drawable.bg_product_config_portion_chip_selected
            else R.drawable.bg_product_config_portion_chip
        )
        root.findViewById<TextView>(R.id.chipMl).setBackgroundResource(
            if (selectedUnit == MeasureUnit.MILLILITER) R.drawable.bg_product_config_portion_chip_selected
            else R.drawable.bg_product_config_portion_chip
        )
        root.findViewById<TextView>(R.id.textBaseWeightLabel).text =
            "Вес и ед. измерения (${selectedUnit.shortLabel})*"
    }

    private fun validate(root: View): Boolean {
        val name = root.findViewById<EditText>(R.id.editFoodName).text?.toString().orEmpty().trim()
        val baseWeight = parse(root.findViewById(R.id.editBaseWeight))
        val calories = parse(root.findViewById(R.id.editCalories))
        val protein = parse(root.findViewById(R.id.editProtein))
        val fat = parse(root.findViewById(R.id.editFat))
        val carbs = parse(root.findViewById(R.id.editCarbs))
        val isValid = name.isNotBlank() && baseWeight > 0.0 && calories >= 0.0 && protein >= 0.0 && fat >= 0.0 && carbs >= 0.0
        root.findViewById<Button>(R.id.buttonContinue).isEnabled = isValid
        root.findViewById<TextView>(R.id.textError).visibility = View.GONE
        return isValid
    }

    private fun saveFood(root: View) {
        if (!validate(root)) {
            root.findViewById<TextView>(R.id.textError).apply {
                visibility = View.VISIBLE
                text = "Заполните название и корректные КБЖУ"
            }
            return
        }

        val progress = root.findViewById<ProgressBar>(R.id.progressSave)
        val button = root.findViewById<Button>(R.id.buttonContinue)
        val baseWeight = parse(root.findViewById(R.id.editBaseWeight))
        val rawCalories = parse(root.findViewById(R.id.editCalories))
        val rawProtein = parse(root.findViewById(R.id.editProtein))
        val rawFat = parse(root.findViewById(R.id.editFat))
        val rawCarbs = parse(root.findViewById(R.id.editCarbs))
        val factor = 100.0 / baseWeight
        val foodId = editFoodId ?: "custom_food_${System.currentTimeMillis()}"

        val food = FoodEntity(
            id = foodId,
            name = root.findViewById<EditText>(R.id.editFoodName).text?.toString().orEmpty().trim(),
            imageUrl = selectedImageUri ?: "drawable://ic_custom_food_photo_placeholder",
            caloriesPer100g = rawCalories * factor,
            proteinPer100g = rawProtein * factor,
            fatPer100g = rawFat * factor,
            carbsPer100g = rawCarbs * factor,
            category = if (selectedUnit == MeasureUnit.GRAM) "custom_product_g" else "custom_product_ml",
            isCustom = true
        )

        val selectedAllergens = allergenSelections.mapNotNull { (allergenId, presenceType) ->
            presenceType?.let { allergenId to it }
        }.toMap()

        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            button.isEnabled = false
            try {
                AppDatabase.getInstance(requireContext()).foodDao().insertFood(food)
                if (selectedAllergens.isNotEmpty()) {
                    allergenRepository.replaceManualFoodAllergens(food.id, selectedAllergens)
                } else {
                    allergenRepository.applyInferredFoodAllergens(food.id, listOf(food.name))
                }
                Toast.makeText(
                    requireContext(),
                    if (editFoodId == null) "Свой продукт сохранён" else "Продукт обновлён",
                    Toast.LENGTH_SHORT
                ).show()
                popBackStackSafely()
            } catch (e: Exception) {
                root.findViewById<TextView>(R.id.textError).apply {
                    visibility = View.VISIBLE
                    text = e.message ?: "Не удалось сохранить продукт"
                }
            } finally {
                if (view != null) {
                    progress.visibility = View.GONE
                    button.isEnabled = true
                }
            }
        }
    }

    private fun parse(editText: EditText): Double {
        return editText.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: -1.0
    }

    private fun confirmExitIfNeeded(root: View) {
        if (!hasUnsavedChanges(root)) {
            popBackStackSafely()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Выйти без сохранения?")
            .setMessage("Введённые данные продукта не будут сохранены.")
            .setPositiveButton("Выйти") { _, _ -> popBackStackSafely() }
            .setNegativeButton("Остаться", null)
            .show()
    }

    private fun hasUnsavedChanges(root: View): Boolean {
        if (editFoodId != null) return true
        val name = root.findViewById<EditText>(R.id.editFoodName).text?.toString().orEmpty().trim()
        val baseWeight = root.findViewById<EditText>(R.id.editBaseWeight).text?.toString().orEmpty().trim()
        val calories = root.findViewById<EditText>(R.id.editCalories).text?.toString().orEmpty().trim()
        val protein = root.findViewById<EditText>(R.id.editProtein).text?.toString().orEmpty().trim()
        val fat = root.findViewById<EditText>(R.id.editFat).text?.toString().orEmpty().trim()
        val carbs = root.findViewById<EditText>(R.id.editCarbs).text?.toString().orEmpty().trim()
        return name.isNotBlank() ||
            baseWeight != "100" ||
            calories.isNotBlank() ||
            protein.isNotBlank() ||
            fat.isNotBlank() ||
            carbs.isNotBlank() ||
            selectedImageUri != null ||
            allergenSelections.values.any { it != null }
    }

    private fun format(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
}
