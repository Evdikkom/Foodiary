package com.example.foodiary.presentation.fragment

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.dispose
import coil.imageLoader
import coil.request.ImageRequest
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.model.AllergenEvidenceType
import com.example.foodiary.data.local.preferences.FavoriteFoodsStorage
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.domain.model.DailyNutrition
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodImpactTone
import com.example.foodiary.domain.model.FoodPortionMemory
import com.example.foodiary.domain.model.FoodSafetyProfile
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.model.NutritionTargets
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.domain.usecase.GetFoodImpactPreviewUseCase
import com.example.foodiary.domain.usecase.GetFoodPortionMemoryUseCase
import com.example.foodiary.presentation.dialog.RecipeDetailsBottomSheet
import com.example.foodiary.presentation.util.EffectiveNutritionTargetsResolver
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.prepareFoodiaryTransition
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class ProductConfigFragment : Fragment(R.layout.fragment_product_config) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_FOOD_ID = "arg_food_id"
        private const val ARG_INITIAL_QUANTITY = "arg_initial_quantity"
        private const val ARG_SOURCE_PORTION_WEIGHT = "arg_source_portion_weight"
        private const val ARG_EDIT_MEAL_ID = "arg_edit_meal_id"
        private const val ARG_TARGET_DAY_START = "arg_target_day_start"

        private const val DEFAULT_QUANTITY = "100"
        private const val PORTION_STEP = 10.0

        fun newInstance(
            mealType: MealType,
            foodId: String,
            initialQuantityInGrams: Double? = null,
            sourcePortionWeightInGrams: Double? = null,
            targetDayStartTimestamp: Long? = null
        ): ProductConfigFragment {
            return ProductConfigFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putString(ARG_FOOD_ID, foodId)
                    initialQuantityInGrams?.let { putDouble(ARG_INITIAL_QUANTITY, it) }
                    sourcePortionWeightInGrams?.let { putDouble(ARG_SOURCE_PORTION_WEIGHT, it) }
                    targetDayStartTimestamp?.let { putLong(ARG_TARGET_DAY_START, it) }
                }
            }
        }

        fun newEditMealInstance(
            mealType: MealType,
            foodId: String,
            mealId: Long,
            initialQuantityInGrams: Double? = null
        ): ProductConfigFragment {
            return ProductConfigFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putString(ARG_FOOD_ID, foodId)
                    putLong(ARG_EDIT_MEAL_ID, mealId)
                    initialQuantityInGrams?.let { putDouble(ARG_INITIAL_QUANTITY, it) }
                }
            }
        }
    }

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    private val foodId: String by lazy {
        arguments?.getString(ARG_FOOD_ID).orEmpty()
    }

    private val initialQuantityInGrams: Double? by lazy {
        if (arguments?.containsKey(ARG_INITIAL_QUANTITY) == true) {
            arguments?.getDouble(ARG_INITIAL_QUANTITY)
        } else {
            null
        }
    }

    private val sourcePortionWeightInGrams: Double? by lazy {
        if (arguments?.containsKey(ARG_SOURCE_PORTION_WEIGHT) == true) {
            arguments?.getDouble(ARG_SOURCE_PORTION_WEIGHT)
        } else {
            null
        }
    }

    private val editMealId: Long? by lazy {
        if (arguments?.containsKey(ARG_EDIT_MEAL_ID) == true) {
            arguments?.getLong(ARG_EDIT_MEAL_ID)?.takeIf { it > 0L }
        } else {
            null
        }
    }

    private val targetDayStartTimestamp: Long by lazy {
        if (arguments?.containsKey(ARG_TARGET_DAY_START) == true) {
            arguments?.getLong(ARG_TARGET_DAY_START) ?: normalizeDayStart(System.currentTimeMillis())
        } else {
            normalizeDayStart(System.currentTimeMillis())
        }
    }

    private val foodRepository by lazy {
        FoodRepositoryImpl(foodDao = AppDatabase.getInstance(requireContext()).foodDao())
    }

    private val mealRepository by lazy {
        MealRepositoryImpl(
            mealDao = AppDatabase.getInstance(requireContext()).mealDao(),
            foodRepository = foodRepository
        )
    }

    private val addMealUseCase by lazy {
        AddMealUseCase(mealRepository)
    }

    private val getDailyNutritionUseCase by lazy {
        GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )
    }

    private val getFoodImpactPreviewUseCase by lazy {
        GetFoodImpactPreviewUseCase()
    }

    private val getFoodPortionMemoryUseCase by lazy {
        GetFoodPortionMemoryUseCase(mealRepository)
    }

    private val favoriteFoodsStorage by lazy {
        FavoriteFoodsStorage(requireContext())
    }

    private val allergenRepository by lazy {
        val database = AppDatabase.getInstance(requireContext())
        AllergenRepositoryImpl(
            allergenDao = database.allergenDao(),
            foodAllergenDao = database.foodAllergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
    }

    private val userRepository by lazy {
        val database = AppDatabase.getInstance(requireContext())
        UserRepositoryImpl(
            userDao = database.userDao(),
            allergenDao = database.allergenDao(),
            userRestrictionDao = database.userRestrictionDao()
        )
    }

    private val nutritionTargetsResolver by lazy {
        EffectiveNutritionTargetsResolver(requireContext())
    }

    private var loadedFood: Food? = null
    private var loadedMealForEdit: Meal? = null
    private var loadedSafetyProfile: FoodSafetyProfile? = null
    private var loadedPortionMemory: FoodPortionMemory? = null
    private var loadedDailyNutrition: DailyNutrition? = null
    private var loadedNutritionTargets: NutritionTargets? = null
    private var selectedMeasureUnit: MeasureUnit = MeasureUnit.GRAM
    private var isFavorite: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStaticUi(view)
        setupActions(view)
        loadFood(view)
    }

    private fun setupStaticUi(root: View) {
        root.findViewById<TextView>(R.id.textScreenTitle).text =
            if (editMealId == null) {
                "Добавить в ${mealTypeLabel(mealType).lowercase(Locale("ru"))}"
            } else {
                "Редактировать ${mealTypeLabel(mealType).lowercase(Locale("ru"))}"
            }

        val startQuantity = initialQuantityInGrams ?: DEFAULT_QUANTITY.toDouble()
        root.findViewById<EditText>(R.id.editQuantity).setText(formatPlainNumber(startQuantity))
        root.findViewById<Button>(R.id.buttonSave).text =
            if (editMealId == null) "Сохранить" else "Сохранить изменения"
        updateMeasureUi(root)
        updateFavoriteUi(root, false)
        updateProductOriginBadge(root, null)
        updateSourcePortionHint(root, null)
        renderPortionMemory(root, null)
        renderSafetyProfile(root, null)
        renderImpactPreview(root)
    }

    private fun setupActions(root: View) {
        root.findViewById<View>(R.id.textBack).setDebouncedClickListener {
            popBackStackSafely()
        }

        root.findViewById<ImageView>(R.id.buttonFavorite).setOnClickListener {
            toggleFavorite(root)
        }

        root.findViewById<TextView>(R.id.buttonEditCustom).setOnClickListener {
            openCustomEditor()
        }
        root.findViewById<TextView>(R.id.buttonRecipeDetails).setOnClickListener {
            loadedFood?.takeIf { it.category == "custom_recipe" }?.let { food ->
                RecipeDetailsBottomSheet.newInstance(food.id).show(parentFragmentManager, "recipe_details")
            }
        }

        root.findViewById<TextView>(R.id.chipPortionMemoryFavorite).setOnClickListener {
            loadedPortionMemory?.favoriteQuantityInGrams?.let { quantity ->
                setQuantity(root, quantity)
            }
        }

        root.findViewById<TextView>(R.id.chipPortionMemoryLast).setOnClickListener {
            loadedPortionMemory?.lastQuantityInGrams?.let { quantity ->
                setQuantity(root, quantity)
            }
        }

        root.findViewById<EditText>(R.id.editQuantity).doAfterTextChanged {
            loadedFood?.let { food ->
                updatePortionSummary(
                    root = root,
                    food = food,
                    quantity = parseQuantity(root.findViewById(R.id.editQuantity))
                )
                updateSourcePortionHint(root, food)
            }
        }

        setupPortionControls(root)
        setupMeasureControls(root)

        root.findViewById<Button>(R.id.buttonSave).setDebouncedClickListener {
            saveMeal(root)
        }
    }

    private fun setupMeasureControls(root: View) {
        root.findViewById<TextView>(R.id.chipMeasureGram).setOnClickListener {
            selectedMeasureUnit = MeasureUnit.GRAM
            updateMeasureUi(root)
        }

        root.findViewById<TextView>(R.id.chipMeasureMl).setOnClickListener {
            selectedMeasureUnit = MeasureUnit.MILLILITER
            updateMeasureUi(root)
        }
    }

    private fun updateMeasureUi(root: View) {
        val gramChip = root.findViewById<TextView>(R.id.chipMeasureGram)
        val mlChip = root.findViewById<TextView>(R.id.chipMeasureMl)
        val quantityInput = root.findViewById<EditText>(R.id.editQuantity)
        val detailBadge = root.findViewById<TextView>(R.id.textFoodBadge)

        val isGram = selectedMeasureUnit == MeasureUnit.GRAM

        gramChip.setBackgroundResource(
            if (isGram) R.drawable.bg_product_config_portion_chip_selected
            else R.drawable.bg_product_config_portion_chip
        )
        mlChip.setBackgroundResource(
            if (isGram) R.drawable.bg_product_config_portion_chip
            else R.drawable.bg_product_config_portion_chip_selected
        )

        quantityInput.hint = "Количество (${selectedMeasureUnit.shortLabel})"
        detailBadge.text = "На 100 ${selectedMeasureUnit.shortLabel}"
        renderPortionMemory(root, loadedPortionMemory)

        loadedFood?.let { food ->
            bindFood(root, food)
            updatePortionSummary(
                root = root,
                food = food,
                quantity = parseQuantity(quantityInput)
            )
        }
    }

    private fun setupPortionControls(root: View) {
        root.findViewById<View>(R.id.buttonMinus).setOnClickListener {
            adjustQuantity(root, -PORTION_STEP)
        }

        root.findViewById<View>(R.id.buttonPlus).setOnClickListener {
            adjustQuantity(root, PORTION_STEP)
        }

        mapOf(
            R.id.chipPortion50 to 50.0,
            R.id.chipPortion100 to 100.0,
            R.id.chipPortion150 to 150.0,
            R.id.chipPortion200 to 200.0
        ).forEach { (viewId, amount) ->
            root.findViewById<TextView>(viewId).setOnClickListener {
                setQuantity(root, amount)
            }
        }
    }

    private fun adjustQuantity(root: View, delta: Double) {
        val current = parseQuantity(root.findViewById(R.id.editQuantity))
        val updated = (current + delta).coerceAtLeast(0.0)
        setQuantity(root, updated)
    }

    private fun setQuantity(root: View, amount: Double) {
        val quantityInput = root.findViewById<EditText>(R.id.editQuantity)
        val formatted = formatPlainNumber(amount)
        quantityInput.setText(formatted)
        quantityInput.setSelection(formatted.length)
    }

    private fun loadFood(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val mealForEdit = editMealId?.let { mealRepository.getMealById(it) }
                loadedMealForEdit = mealForEdit
                if (editMealId != null && mealForEdit == null) {
                    root.findViewById<TextView>(R.id.textError).apply {
                        visibility = View.VISIBLE
                        text = "Запись приёма пищи не найдена"
                    }
                    return@launch
                }
                mealForEdit?.let { meal ->
                    root.findViewById<EditText>(R.id.editQuantity)
                        .setText(formatPlainNumber(meal.quantityInGrams))
                    root.findViewById<EditText>(R.id.editNote).setText(meal.note)
                }

                val food = foodRepository.getFoodById(foodId)
                loadedFood = food
                isFavorite = favoriteFoodsStorage.isFavorite(food.id)
                loadedPortionMemory = getFoodPortionMemoryUseCase(food.id, mealType)
                loadedDailyNutrition = getDailyNutritionUseCase(
                    startOfDay = targetDayStartTimestamp,
                    endOfDay = targetDayEndTimestamp()
                )
                loadedNutritionTargets = userRepository.getCurrentUser()
                    ?.let(nutritionTargetsResolver::resolve)
                loadedSafetyProfile = allergenRepository.getFoodSafetyProfile(food.id, food.name)

                maybeApplyRememberedPortion(root, loadedPortionMemory)

                bindFood(root, food)
                updateFavoriteUi(root, isFavorite)
                renderPortionMemory(root, loadedPortionMemory)
                renderSafetyProfile(root, loadedSafetyProfile)
                updatePortionSummary(
                    root = root,
                    food = food,
                    quantity = parseQuantity(root.findViewById(R.id.editQuantity))
                )
            } catch (e: Exception) {
                root.findViewById<TextView>(R.id.textError).apply {
                    visibility = View.VISIBLE
                    text = e.message ?: "Не удалось загрузить продукт"
                }
            }
        }
    }

    private fun toggleFavorite(root: View) {
        val food = loadedFood ?: return

        isFavorite = favoriteFoodsStorage.toggleFavorite(food.id)
        updateFavoriteUi(root, isFavorite)

        Toast.makeText(
            requireContext(),
            if (isFavorite) "Добавлено в избранное" else "Убрано из избранного",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun maybeApplyRememberedPortion(
        root: View,
        memory: FoodPortionMemory?
    ) {
        val preferredQuantity = memory?.preferredQuantityInGrams ?: return
        if (editMealId != null) return
        if (initialQuantityInGrams != null) return
        if (sourcePortionWeightInGrams != null) return

        setQuantity(root, preferredQuantity)
    }

    private fun renderPortionMemory(root: View, memory: FoodPortionMemory?) {
        val container = root.findViewById<View>(R.id.layoutPortionMemory)
        val caption = root.findViewById<TextView>(R.id.textPortionMemoryCaption)
        val favoriteChip = root.findViewById<TextView>(R.id.chipPortionMemoryFavorite)
        val lastChip = root.findViewById<TextView>(R.id.chipPortionMemoryLast)

        val favoriteQuantity = memory?.favoriteQuantityInGrams
        val lastQuantity = memory?.lastQuantityInGrams
        val hasFavorite = favoriteQuantity != null
        val hasLast = lastQuantity != null &&
            (favoriteQuantity == null || abs(favoriteQuantity - lastQuantity) >= 0.05)

        if (memory == null || memory.basedOnMealsCount == 0 || (!hasFavorite && !hasLast)) {
            container.visibility = View.GONE
            caption.text = ""
            favoriteChip.visibility = View.GONE
            lastChip.visibility = View.GONE
            return
        }

        val unitLabel = selectedMeasureUnit.shortLabel
        container.visibility = View.VISIBLE
        caption.text = if (memory.isMealTypeSpecific) {
            "Подсказки по вашим прошлым порциям для этого приёма пищи"
        } else {
            "Подсказки по вашим прошлым порциям этого продукта"
        }

        if (hasFavorite) {
            favoriteChip.visibility = View.VISIBLE
            favoriteChip.text = "Обычно ${formatPlainNumber(favoriteQuantity!!)} $unitLabel"
        } else {
            favoriteChip.visibility = View.GONE
        }

        if (hasLast) {
            lastChip.visibility = View.VISIBLE
            lastChip.text = "Последний раз ${formatPlainNumber(lastQuantity!!)} $unitLabel"
        } else {
            lastChip.visibility = View.GONE
        }
    }

    private fun bindFood(root: View, food: Food) {
        root.findViewById<TextView>(R.id.textFoodName).text = food.name
        updateProductOriginBadge(root, food)
        updateSourcePortionHint(root, food)

        root.findViewById<TextView>(R.id.textDetailCalories).text = formatKcal(food.caloriesPer100g)
        root.findViewById<TextView>(R.id.textDetailProtein).text = formatMacro(food.proteinPer100g)
        root.findViewById<TextView>(R.id.textDetailFat).text = formatMacro(food.fatPer100g)
        root.findViewById<TextView>(R.id.textDetailCarbs).text = formatMacro(food.carbsPer100g)

        bindImage(root.findViewById(R.id.imageFood), food.imageUrl)
    }

    private fun updateFavoriteUi(root: View, isFavorite: Boolean) {
        val favoriteButton = root.findViewById<ImageView>(R.id.buttonFavorite)

        favoriteButton.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite_outline
        )
        favoriteButton.imageTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
        favoriteButton.alpha = if (isFavorite) 1f else 0.96f
        favoriteButton.contentDescription =
            if (isFavorite) "Убрать из избранного" else "Добавить в избранное"
    }

    private fun bindImage(imageView: ImageView, imageRef: String?) {
        val normalized = imageRef?.trim().orEmpty()
        val placeholder = ColorDrawable(0xFFF4D4FB.toInt())

        imageView.dispose()
        imageView.tag = normalized

        if (normalized.isBlank()) {
            imageView.setImageDrawable(placeholder)
            return
        }

        if (normalized.startsWith("drawable://")) {
            val drawableName = normalized.removePrefix("drawable://")
            val resId = resources.getIdentifier(
                drawableName,
                "drawable",
                requireContext().packageName
            )

            if (resId != 0) {
                imageView.setImageResource(resId)
            } else {
                imageView.setImageDrawable(placeholder)
            }
            return
        }

        val request = ImageRequest.Builder(requireContext())
            .data(normalized)
            .target(
                onStart = { drawable ->
                    if (imageView.tag == normalized) {
                        imageView.setImageDrawable(drawable ?: placeholder)
                    }
                },
                onSuccess = { drawable ->
                    if (imageView.tag == normalized) {
                        imageView.setImageDrawable(drawable)
                    }
                },
                onError = { drawable ->
                    if (imageView.tag == normalized) {
                        imageView.setImageDrawable(drawable ?: placeholder)
                    }
                }
            )
            .crossfade(true)
            .build()

        requireContext().imageLoader.enqueue(request)
    }

    private fun updateProductOriginBadge(root: View, food: Food?) {
        val badge = root.findViewById<TextView>(R.id.textProductOriginBadge)
        val edit = root.findViewById<TextView>(R.id.buttonEditCustom)
        val details = root.findViewById<TextView>(R.id.buttonRecipeDetails)
        val actionsRow = root.findViewById<View>(R.id.layoutCustomActions)
        if (food?.isCustom == true) {
            badge.visibility = View.VISIBLE
            badge.text = if (food.category == "custom_recipe") "Блюдо" else "Мой"
            edit.visibility = View.VISIBLE
            details.visibility = if (food.category == "custom_recipe") View.VISIBLE else View.GONE
            actionsRow.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
            badge.text = ""
            edit.visibility = View.GONE
            details.visibility = View.GONE
            actionsRow.visibility = View.GONE
        }
    }

    private fun updateSourcePortionHint(root: View, food: Food?) {
        val hintView = root.findViewById<TextView>(R.id.textSourcePortionHint)
        val sourceWeight = sourcePortionWeightInGrams

        if (food?.category != "photo_ai" || sourceWeight == null) {
            hintView.visibility = View.GONE
            hintView.text = ""
            return
        }

        val currentQuantity = parseQuantity(root.findViewById<EditText>(R.id.editQuantity))
            .takeIf { it > 0.0 }
            ?: initialQuantityInGrams
            ?: sourceWeight
        hintView.visibility = View.VISIBLE
        hintView.text = if (kotlin.math.abs(currentQuantity - sourceWeight) < 0.05) {
            "LogMeal оценил всю порцию примерно в ${formatPlainNumber(sourceWeight)} г. Это значение уже подставлено выше, но его можно изменить перед сохранением."
        } else {
            "LogMeal оценил всю порцию примерно в ${formatPlainNumber(sourceWeight)} г. Сейчас в поле выше подставлено ${formatPlainNumber(currentQuantity)} г, и вы можете скорректировать это количество перед сохранением."
        }
    }

    private fun renderSafetyProfile(root: View, profile: FoodSafetyProfile?) {
        val card = root.findViewById<View>(R.id.cardAllergenSafety)
        val title = root.findViewById<TextView>(R.id.textAllergenSafetyTitle)
        val summary = root.findViewById<TextView>(R.id.textAllergenSafetySummary)
        val details = root.findViewById<TextView>(R.id.textAllergenSafetyDetails)

        val hasUserConflict = profile?.highRiskConflicts?.isNotEmpty() == true ||
            profile?.warningConflicts?.isNotEmpty() == true

        if (profile == null || !hasUserConflict) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        card.setBackgroundResource(
            if (profile.hasHighRisk) R.drawable.bg_allergen_danger_card
            else R.drawable.bg_allergen_warning_card
        )

        val explicitNames = profile.confirmedAllergens.map { it.allergen.displayName }.distinct()
        val inferredNames = profile.inferredAllergens.map { it.allergen.displayName }
            .filterNot { it in explicitNames.toSet() }
            .distinct()
        val explicitHighRiskConflicts = profile.highRiskConflicts
            .filterNot { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }
        val explicitWarningConflicts = profile.warningConflicts
            .filterNot { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }
        val inferredWarningConflicts = profile.warningConflicts
            .filter { it.evidenceType == AllergenEvidenceType.NAME_MATCH_INFERRED }
        val highRiskNames = explicitHighRiskConflicts.map { it.allergen.displayName }.distinct()
        val warningNames = explicitWarningConflicts.map { it.allergen.displayName }.distinct()
        val inferredWarningNames = inferredWarningConflicts.map { it.allergen.displayName }
            .distinct()

        summary.setTypeface(summary.typeface, Typeface.BOLD)

        title.text = when {
            explicitHighRiskConflicts.isNotEmpty() -> "Есть конфликт с аллергией"
            explicitWarningConflicts.isNotEmpty() -> "Есть несовместимость с ограничениями"
            inferredWarningConflicts.isNotEmpty() -> "Стоит перепроверить состав"
            else -> "Аллергены продукта"
        }

        summary.text = when {
            explicitHighRiskConflicts.isNotEmpty() ->
                "Аллергия: ${highRiskNames.joinToString(", ")}."
            explicitWarningConflicts.isNotEmpty() ->
                "Непереносимость: ${warningNames.joinToString(", ")}."
            inferredWarningConflicts.isNotEmpty() ->
                "Возможное совпадение с ограничениями: ${inferredWarningNames.joinToString(", ")}."
            explicitNames.isNotEmpty() ->
                "Подтвержденные аллергены: ${explicitNames.joinToString(", ")}."
            inferredNames.isNotEmpty() ->
                "Возможные аллергены по типу или названию продукта: ${inferredNames.joinToString(", ")}."
            else -> "Для продукта пока нет аллергенных данных."
        }

        details.text = buildList {
            if (explicitHighRiskConflicts.isNotEmpty()) {
                add("В профиле пользователя это отмечено как строгая аллергия. Перед добавлением лучше ещё раз проверить состав.")
            } else if (explicitWarningConflicts.isNotEmpty()) {
                add("В профиле пользователя это отмечено как непереносимость. Продукт можно добавить, но лучше делать это осознанно.")
            } else if (inferredWarningConflicts.isNotEmpty()) {
                add("Это только осторожная подсказка по типу или названию продукта, а не подтверждённый факт состава.")
            }
            if (explicitNames.isNotEmpty() && explicitHighRiskConflicts.isEmpty() && explicitWarningConflicts.isEmpty()) {
                add("Подтвержденные: ${explicitNames.joinToString(", ")}.")
            }
            if (inferredNames.isNotEmpty() && explicitHighRiskConflicts.isEmpty() && explicitWarningConflicts.isEmpty()) {
                add("Возможные по названию: ${inferredNames.joinToString(", ")}.")
            }
            if (explicitHighRiskConflicts.isNotEmpty()) {
                add("Перед добавлением Foodiary попросит ещё раз подтвердить сохранение.")
            }
        }.joinToString(" ")
    }

    private fun updatePortionSummary(
        root: View,
        food: Food,
        quantity: Double
    ) {
        val safeQuantity = quantity.coerceAtLeast(0.0)
        val factor = safeQuantity / 100.0

        val calories = food.caloriesPer100g * factor
        val protein = food.proteinPer100g * factor
        val fat = food.fatPer100g * factor
        val carbs = food.carbsPer100g * factor

        root.findViewById<TextView>(R.id.textHeaderCalories).text = formatKcal(calories)
        root.findViewById<TextView>(R.id.textProtein).text = formatMacro(protein)
        root.findViewById<TextView>(R.id.textFat).text = formatMacro(fat)
        root.findViewById<TextView>(R.id.textCarbs).text = formatMacro(carbs)

        updateSelectedPortionChip(root, safeQuantity)
        renderImpactPreview(root)
    }

    private fun renderImpactPreview(root: View) {
        val card = root.findViewById<View>(R.id.cardImpactPreview)
        val food = loadedFood
        val currentNutrition = loadedDailyNutrition
        val targets = loadedNutritionTargets

        if (food == null || currentNutrition == null || targets == null) {
            card.visibility = View.GONE
            return
        }

        val quantity = parseQuantity(root.findViewById(R.id.editQuantity))
        val preview = getFoodImpactPreviewUseCase(
            food = food,
            quantityInGrams = quantity,
            currentNutrition = currentNutrition,
            targets = targets,
            safetyProfile = loadedSafetyProfile,
            replacedFood = if (editMealId != null) food else null,
            replacedQuantityInGrams = loadedMealForEdit?.quantityInGrams ?: 0.0
        )

        card.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.textImpactScore).text = formatScore(preview.score)
        root.findViewById<TextView>(R.id.textImpactTitle).text = preview.title
        root.findViewById<TextView>(R.id.textImpactSummary).text = preview.summary
        root.findViewById<TextView>(R.id.textImpactPortionAdvice).text = preview.portionAdvice

        val macroContainer = root.findViewById<LinearLayout>(R.id.layoutImpactMacros)
        macroContainer.removeAllViews()
        preview.macroStatuses.forEach { status ->
            macroContainer.addView(
                buildImpactRow(
                    label = status.label,
                    value = "${formatPlainNumber(status.after)} / ${formatPlainNumber(status.target)} ${status.unit}",
                    description = status.message,
                    tone = status.tone
                )
            )
        }

        val detailsContainer = root.findViewById<LinearLayout>(R.id.layoutImpactDetails)
        detailsContainer.removeAllViews()
        preview.details.forEach { detail ->
            detailsContainer.addView(
                buildImpactRow(
                    label = detail.label,
                    value = detail.value,
                    description = detail.description,
                    tone = detail.tone
                )
            )
        }
    }

    private fun buildImpactRow(
        label: String,
        value: String,
        description: String,
        tone: FoodImpactTone
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(requireContext()).apply {
                text = label
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#2F2433"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        header.addView(
            TextView(requireContext()).apply {
                text = value
                textSize = 13f
                setTextColor(impactToneColor(tone))
                typeface = Typeface.DEFAULT_BOLD
            }
        )
        row.addView(header)
        row.addView(
            TextView(requireContext()).apply {
                text = description
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#6B5B73"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2.dp()
                }
            }
        )
        return row
    }

    private fun impactToneColor(tone: FoodImpactTone): Int {
        val color = when (tone) {
            FoodImpactTone.POSITIVE -> "#2C8E61"
            FoodImpactTone.NEUTRAL -> "#6B5B73"
            FoodImpactTone.WARNING -> "#C07900"
            FoodImpactTone.DANGER -> "#A33A3A"
        }
        return android.graphics.Color.parseColor(color)
    }

    private fun updateSelectedPortionChip(root: View, quantity: Double) {
        val selectedId = when {
            quantity == 50.0 -> R.id.chipPortion50
            quantity == 100.0 -> R.id.chipPortion100
            quantity == 150.0 -> R.id.chipPortion150
            quantity == 200.0 -> R.id.chipPortion200
            else -> View.NO_ID
        }

        listOf(
            R.id.chipPortion50,
            R.id.chipPortion100,
            R.id.chipPortion150,
            R.id.chipPortion200
        ).forEach { viewId ->
            root.findViewById<TextView>(viewId).setBackgroundResource(
                if (viewId == selectedId) R.drawable.bg_product_config_portion_chip_selected
                else R.drawable.bg_product_config_portion_chip
            )
        }
    }

    private fun saveMeal(root: View) {
        val profile = loadedSafetyProfile
        if (profile?.hasHighRisk == true) {
            AlertDialog.Builder(requireContext())
                .setTitle("Подтвердите добавление")
                .setMessage(
                    "По профилю пользователя продукт конфликтует со строгими аллергическими ограничениями. Добавить его в приём пищи всё равно?"
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить") { _, _ ->
                    performSaveMeal(root)
                }
                .show()
            return
        }

        performSaveMeal(root)
    }

    private fun performSaveMeal(root: View) {
        val errorView = root.findViewById<TextView>(R.id.textError)
        val progress = root.findViewById<ProgressBar>(R.id.progressSaving)
        val saveButton = root.findViewById<Button>(R.id.buttonSave)
        val quantityInput = root.findViewById<EditText>(R.id.editQuantity)
        val noteInput = root.findViewById<EditText>(R.id.editNote)

        errorView.visibility = View.GONE

        val quantity = parseQuantity(quantityInput)
        if (quantity <= 0.0) {
            errorView.visibility = View.VISIBLE
            errorView.text = "Количество должно быть больше 0"
            return
        }

        val food = loadedFood
        if (food == null) {
            errorView.visibility = View.VISIBLE
            errorView.text = "Продукт ещё не загружен"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            saveButton.isEnabled = false

            try {
                val originalNote = noteInput.text?.toString().orEmpty().trim()
                val noteWithUnit = buildSavedNote(originalNote)

                val existingMealId = editMealId
                if (existingMealId == null) {
                    addMealUseCase(
                        Meal(
                            foodId = food.id,
                            quantityInGrams = quantity,
                            mealType = mealType,
                            timestamp = resolveMealTimestampForSave(),
                            note = noteWithUnit
                        )
                    )
                    parentFragmentManager.setFragmentResult(
                        DailyNutritionFragment.REQUEST_MEALS_CHANGED,
                        Bundle.EMPTY
                    )
                    Toast.makeText(requireContext(), "Продукт добавлен", Toast.LENGTH_SHORT).show()
                    navigateToMealScreenAfterAdd()
                } else {
                    mealRepository.updateMeal(
                        Meal(
                            id = existingMealId,
                            foodId = food.id,
                            quantityInGrams = quantity,
                            mealType = mealType,
                            timestamp = loadedMealForEdit?.timestamp ?: System.currentTimeMillis(),
                            note = noteWithUnit
                        )
                    )
                    parentFragmentManager.setFragmentResult(
                        DailyNutritionFragment.REQUEST_MEALS_CHANGED,
                        Bundle.EMPTY
                    )
                    Toast.makeText(requireContext(), "Запись обновлена", Toast.LENGTH_SHORT).show()
                    popBackStackSafely()
                }
            } catch (e: Exception) {
                errorView.visibility = View.VISIBLE
                errorView.text = e.message ?: "Не удалось сохранить продукт"
            } finally {
                if (view != null) {
                    progress.visibility = View.GONE
                    saveButton.isEnabled = true
                }
            }
        }
    }

    private fun resolveMealTimestampForSave(): Long {
        if (isTodayTargetDay()) return System.currentTimeMillis()

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = targetDayStartTimestamp
        }
        when (mealType) {
            MealType.AFTERNOON_SNACK -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 15)
                calendar.set(java.util.Calendar.MINUTE, 30)
            }
            MealType.LATE_DINNER -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 21)
                calendar.set(java.util.Calendar.MINUTE, 0)
            }
            MealType.BREAKFAST -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 8)
                calendar.set(java.util.Calendar.MINUTE, 30)
            }
            MealType.LUNCH -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 13)
                calendar.set(java.util.Calendar.MINUTE, 0)
            }
            MealType.DINNER -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 19)
                calendar.set(java.util.Calendar.MINUTE, 0)
            }
            MealType.SNACK -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 16)
                calendar.set(java.util.Calendar.MINUTE, 30)
            }
        }
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun targetDayEndTimestamp(): Long {
        return java.util.Calendar.getInstance().apply {
            timeInMillis = targetDayStartTimestamp
            add(java.util.Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }

    private fun navigateToMealScreenAfterAdd() {
        val fragmentManager = activity?.supportFragmentManager ?: return
        if (fragmentManager.isStateSaved) return

        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }

        val current = fragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is MealDetailsFragment) return

        val mealDetailsFragment = MealDetailsFragment.newInstance(mealType, targetDayStartTimestamp)
        prepareFoodiaryTransition(current, mealDetailsFragment, FoodiaryMotionPattern.FORWARD_AXIS_X)
        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, mealDetailsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun isTodayTargetDay(): Boolean {
        return targetDayStartTimestamp == normalizeDayStart(System.currentTimeMillis())
    }

    private fun normalizeDayStart(timestamp: Long): Long {
        return java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun buildSavedNote(originalNote: String): String {
        if (selectedMeasureUnit == MeasureUnit.GRAM) return originalNote

        val unitMarker = "[ед.изм.: ${selectedMeasureUnit.shortLabel}]"
        return if (originalNote.isBlank()) {
            unitMarker
        } else {
            "$unitMarker $originalNote"
        }
    }

    private fun openCustomEditor() {
        val food = loadedFood ?: return
        val fragment = if (food.category == "custom_recipe") {
            CreateRecipeFragment.newEditInstance(mealType, food.id)
        } else {
            CreateCustomFoodFragment.newEditInstance(mealType, food.id)
        }

        replaceFragmentSafely(fragment, motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y)
    }

    private fun parseQuantity(input: EditText): Double {
        return input.text?.toString()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: 0.0
    }

    private fun mealTypeLabel(mealType: MealType): String {
        return when (mealType) {
            MealType.AFTERNOON_SNACK -> "Полдник"
            MealType.LATE_DINNER -> "Поздний ужин"
            MealType.BREAKFAST -> "Завтрак"
            MealType.LUNCH -> "Обед"
            MealType.DINNER -> "Ужин"
            MealType.SNACK -> "Перекус"
        }
    }

    private fun formatKcal(value: Double): String = "${formatPlainNumber(value)} ккал"

    private fun formatMacro(value: Double): String = "${formatPlainNumber(value)} г"

    private fun formatPlainNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun formatScore(score: Int): String {
        return "${(score / 10.0).roundToInt().coerceIn(0, 10)}/10"
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }

    private enum class MeasureUnit(val shortLabel: String) {
        GRAM("г"),
        MILLILITER("мл")
    }
}
