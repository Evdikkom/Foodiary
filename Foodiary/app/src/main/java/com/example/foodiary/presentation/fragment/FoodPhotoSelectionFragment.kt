package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.remote.vision.FoodRecognitionApiFactory
import com.example.foodiary.data.remote.vision.dto.AnalyzeFoodResponseDto
import com.example.foodiary.data.remote.vision.dto.DetectedDishItemDto
import com.example.foodiary.data.remote.vision.dto.NutritionSummaryDto
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.adapter.ValidatedFoodRecognitionItemAdapter
import com.example.foodiary.presentation.util.ImageCompressionUtils
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale

class FoodPhotoSelectionFragment : Fragment(R.layout.fragment_food_photo_analysis) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_IMAGE_PATH = "arg_image_path"
        private const val ARG_TARGET_DAY_START = "arg_target_day_start"

        fun newInstance(
            mealType: MealType,
            imagePath: String,
            targetDayStart: Long
        ): FoodPhotoSelectionFragment {
            return FoodPhotoSelectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putString(ARG_IMAGE_PATH, imagePath)
                    putLong(ARG_TARGET_DAY_START, targetDayStart)
                }
            }
        }
    }

    private data class WeightEstimate(
        val grams: Double,
        val fromApi: Boolean,
        val weightedItems: Int,
        val selectedItems: Int,
    )

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    private val imagePath: String by lazy {
        arguments?.getString(ARG_IMAGE_PATH).orEmpty()
    }

    private val targetDayStart: Long by lazy {
        arguments?.getLong(ARG_TARGET_DAY_START) ?: System.currentTimeMillis()
    }

    private lateinit var itemsAdapter: ValidatedFoodRecognitionItemAdapter
    private var analyzeResponse: AnalyzeFoodResponseDto? = null
    private val selectedItemPositions = linkedSetOf<Int>()
    private val sourceItemWeights = linkedMapOf<Int, Double?>()
    private val confirmedItemWeights = linkedMapOf<Int, Double?>()
    private var isApplyingWeightText = false
    private var hasUserAdjustedWeight = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticUi(view)
        setupRecycler(view)
        setupActions(view)
        bindPhoto(view)
        analyzePhoto(view)
    }

    private fun setupStaticUi(root: View) {
        root.findViewById<TextView>(R.id.textScreenTitle).text = "Анализ блюда по фото"
        root.findViewById<TextView>(R.id.textSubtitle).text =
            "Подтвердите только те части блюда, которые действительно есть на фото. КБЖУ считаются по выбранным позициям."
        root.findViewById<EditText>(R.id.editDishName).setText("Блюдо по фото")
        applyEstimatedWeight(root, 100.0)
        root.findViewById<EditText>(R.id.editEstimatedWeight).doAfterTextChanged {
            if (!isApplyingWeightText) {
                hasUserAdjustedWeight = true
                analyzeResponse?.let { response -> refreshSelectionDrivenUi(root, response) }
            }
        }
        root.findViewById<TextView>(R.id.textSelectionHint).text =
            "Вес ниже нужен для нормализации результата к 100 г. Если LogMeal прислал граммовку по выбранным позициям, мы подставим её автоматически."
        root.findViewById<TextView>(R.id.textSummaryContext).text =
            "Карточки выше показывают КБЖУ всей выбранной порции."
        root.findViewById<TextView>(R.id.textPer100Preview).text =
            "После сохранения блюдо будет храниться в Foodiary как продукт на 100 г."
        root.findViewById<Button>(R.id.buttonSaveDraft).text = "Сохранить блюдо и выбрать порцию"
        root.findViewById<Button>(R.id.buttonSaveDraft).isEnabled = false
    }

    private fun setupRecycler(root: View) {
        itemsAdapter = ValidatedFoodRecognitionItemAdapter(
            onSelectionChanged = { position, isSelected ->
                val response = analyzeResponse ?: return@ValidatedFoodRecognitionItemAdapter
                if (isSelected) {
                    selectedItemPositions += position
                } else {
                    selectedItemPositions -= position
                }
                itemsAdapter.submit(
                    response.items,
                    selectedItemPositions,
                    confirmedItemWeights,
                    sourceItemWeights
                )
                refreshSelectionDrivenUi(root, response)
            },
            onWeightChanged = { position, weight ->
                val response = analyzeResponse ?: return@ValidatedFoodRecognitionItemAdapter
                confirmedItemWeights[position] = weight
                refreshSelectionDrivenUi(root, response)
            }
        )

        root.findViewById<RecyclerView>(R.id.recyclerRecognizedItems).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = itemsAdapter
        }
    }

    private fun setupActions(root: View) {
        root.findViewById<View>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }

        root.findViewById<Button>(R.id.buttonRetryAnalysis).setDebouncedClickListener {
            analyzePhoto(root)
        }

        root.findViewById<Button>(R.id.buttonRetake).setDebouncedClickListener {
            popBackStackSafely()
        }

        root.findViewById<Button>(R.id.buttonSaveDraft).setDebouncedClickListener {
            saveDraftAndOpenProduct(root)
        }

    }

    private fun bindPhoto(root: View) {
        root.findViewById<ImageView>(R.id.imageCapturedFood).load(File(imagePath))
    }

    private fun analyzePhoto(root: View) {
        val photoFile = File(imagePath)
        if (!photoFile.exists()) {
            showError(root, "Файл снимка не найден. Попробуйте выбрать или сделать фото ещё раз.")
            return
        }

        renderLoading(root, true)
        clearError(root)
        root.findViewById<TextView>(R.id.textLoadingHint).text =
            "Оптимизируем фото и отправляем на анализ..."

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val preparedFile = prepareImageForUpload(photoFile)
                    val requestBody = preparedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val imagePart = MultipartBody.Part.createFormData(
                        "image",
                        preparedFile.name,
                        requestBody
                    )
                    FoodRecognitionApiFactory.create(appContext).analyzeFood(imagePart)
                }

                analyzeResponse = response
                bindAnalysis(root, response)
            } catch (e: Exception) {
                showError(
                    root,
                    e.message ?: "Не удалось отправить фото на анализ. Проверьте, запущен ли backend."
                )
            } finally {
                renderLoading(root, false)
                root.findViewById<TextView>(R.id.textLoadingHint).text =
                    "Анализируем снимок блюда..."
            }
        }
    }

    private fun prepareImageForUpload(sourceFile: File): File {
        val uploadDir = File(requireContext().cacheDir, "photo_uploads")
        return ImageCompressionUtils.compressForUpload(
            sourceFile = sourceFile,
            outputDir = uploadDir,
            maxBytes = 950 * 1024,
            maxDimension = 1280,
        )
    }

    private fun bindAnalysis(root: View, response: AnalyzeFoodResponseDto) {
        val detectedName = when {
            response.items.size > 1 -> "Блюдо по фото"
            !response.rawDishLabel.isNullOrBlank() -> response.rawDishLabel
            else -> response.items.firstOrNull()?.topCandidate?.name ?: "Блюдо по фото"
        }

        root.findViewById<EditText>(R.id.editDishName).setText(detectedName)
        selectedItemPositions.clear()
        selectedItemPositions.addAll(response.items.indices)
        sourceItemWeights.clear()
        confirmedItemWeights.clear()
        response.items.forEachIndexed { index, item ->
            val sourceWeight = parseWeightFromServing(item.servingSize)
            sourceItemWeights[index] = sourceWeight
            confirmedItemWeights[index] = sourceWeight
        }
        itemsAdapter.submit(
            response.items,
            selectedItemPositions,
            confirmedItemWeights,
            sourceItemWeights
        )
        hasUserAdjustedWeight = false

        root.findViewById<View>(R.id.cardResult).isVisible = true
        refreshSelectionDrivenUi(root, response)
    }

    private fun refreshSelectionDrivenUi(
        root: View,
        response: AnalyzeFoodResponseDto
    ) {
        val selectedItems = selectedItemEntries(response)
        val selectionSummary = resolveSelectedSummary(response, selectedItems)
        val apiWeightEstimate = estimateTotalWeight(selectedItems)

        if (!hasUserAdjustedWeight) {
            applyEstimatedWeight(root, apiWeightEstimate.grams)
        }

        val currentWeight = parseDouble(
            root.findViewById<EditText>(R.id.editEstimatedWeight).text?.toString()
        )

        root.findViewById<TextView>(R.id.textSummaryCalories).text =
            formatKcal(selectionSummary.caloriesKcal)
        root.findViewById<TextView>(R.id.textSummaryProtein).text =
            formatMacro(selectionSummary.proteinG)
        root.findViewById<TextView>(R.id.textSummaryFat).text =
            formatMacro(selectionSummary.fatG)
        root.findViewById<TextView>(R.id.textSummaryCarbs).text =
            formatMacro(selectionSummary.carbsG)
        root.findViewById<TextView>(R.id.textRecognitionNote).text =
            buildNote(response, apiWeightEstimate, currentWeight)
        root.findViewById<TextView>(R.id.textSummaryContext).text =
            buildSummaryContext(apiWeightEstimate)
        root.findViewById<TextView>(R.id.textPer100Preview).text =
            buildPer100Preview(selectionSummary, currentWeight)
        root.findViewById<TextView>(R.id.textSelectionStatus).text =
            buildSelectionStatus(selectedItems.size, response.items.size)

        root.findViewById<Button>(R.id.buttonSaveDraft).isEnabled = selectedItems.isNotEmpty()
        if (selectedItems.isEmpty()) {
            showError(root, "Выберите хотя бы одну позицию, чтобы сформировать итоговое блюдо.")
        } else {
            clearError(root)
        }
    }

    private fun saveDraftAndOpenProduct(root: View) {
        val response = analyzeResponse ?: run {
            Toast.makeText(requireContext(), "Сначала дождитесь анализа фото", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedItems = selectedItemEntries(response)
        if (selectedItems.isEmpty()) {
            showError(root, "Выберите хотя бы одну распознанную позицию перед сохранением.")
            return
        }

        val name = root.findViewById<EditText>(R.id.editDishName).text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            showError(root, "Укажите название блюда перед сохранением")
            return
        }

        val estimatedWeight = parseDouble(
            root.findViewById<EditText>(R.id.editEstimatedWeight).text?.toString()
        )
        if (estimatedWeight <= 0.0) {
            showError(root, "Укажите примерный вес блюда в граммах")
            return
        }

        val summary = resolveSelectedSummary(response, selectedItems)
        val sourceWeightEstimate = estimateTotalWeight(selectedItems)
        val factor = 100.0 / estimatedWeight
        val caloriesPer100 = (summary.caloriesKcal ?: 0.0) * factor
        val proteinPer100 = (summary.proteinG ?: 0.0) * factor
        val fatPer100 = (summary.fatG ?: 0.0) * factor
        val carbsPer100 = (summary.carbsG ?: 0.0) * factor

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val persistedImagePath = withContext(Dispatchers.IO) { persistImage(imagePath) }
                val foodId = "photo_ai_${System.currentTimeMillis()}"
                val entity = FoodEntity(
                    id = foodId,
                    name = name,
                    imageUrl = persistedImagePath,
                    caloriesPer100g = caloriesPer100,
                    proteinPer100g = proteinPer100,
                    fatPer100g = fatPer100,
                    carbsPer100g = carbsPer100,
                    category = "photo_ai",
                    isCustom = true
                )

                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getInstance(requireContext())
                    database.foodDao().insertFood(entity)
                    AllergenRepositoryImpl(
                        allergenDao = database.allergenDao(),
                        foodAllergenDao = database.foodAllergenDao(),
                        userRestrictionDao = database.userRestrictionDao()
                    ).applyInferredFoodAllergens(
                        foodId = foodId,
                        names = selectedItems.mapNotNull { (_, item) -> item.topCandidate?.name },
                        ingredientHints = selectedItems.flatMap { (_, item) -> item.ingredients }
                    )
                }

                Toast.makeText(requireContext(), "Черновик блюда сохранён", Toast.LENGTH_SHORT).show()
                replaceFragmentSafely(
                    ProductConfigFragment.newInstance(
                        mealType = mealType,
                        foodId = foodId,
                        initialQuantityInGrams = estimatedWeight,
                        sourcePortionWeightInGrams = sourceWeightEstimate.takeIf { it.fromApi }?.grams,
                        targetDayStartTimestamp = targetDayStart
                    )
                )
            } catch (e: Exception) {
                showError(root, e.message ?: "Не удалось сохранить черновик блюда")
            }
        }
    }

    private fun selectedItemEntries(
        response: AnalyzeFoodResponseDto
    ): List<Pair<Int, DetectedDishItemDto>> {
        return response.items.mapIndexedNotNull { index, item ->
            if (index in selectedItemPositions) index to item else null
        }
    }

    private fun resolveSelectedSummary(
        response: AnalyzeFoodResponseDto,
        selectedItems: List<Pair<Int, DetectedDishItemDto>>
    ): NutritionSummaryDto {
        if (selectedItems.isEmpty()) {
            return NutritionSummaryDto()
        }

        val selectedSummary = aggregateItemSummary(selectedItems)
        if (hasAnyNutrition(selectedSummary)) {
            return selectedSummary
        }

        return if (selectedItems.size == response.items.size) {
            response.summary ?: selectedSummary
        } else {
            selectedSummary
        }
    }

    private fun aggregateItemSummary(
        items: List<Pair<Int, DetectedDishItemDto>>
    ): NutritionSummaryDto {
        fun List<Double?>.sumOrNull(): Double? {
            val values = filterNotNull()
            return if (values.isEmpty()) null else values.sum()
        }

        return NutritionSummaryDto(
            caloriesKcal = items.map { (index, item) -> scaledValue(item.caloriesKcal, index) }.sumOrNull(),
            proteinG = items.map { (index, item) -> scaledValue(item.proteinG, index) }.sumOrNull(),
            fatG = items.map { (index, item) -> scaledValue(item.fatG, index) }.sumOrNull(),
            carbsG = items.map { (index, item) -> scaledValue(item.carbsG, index) }.sumOrNull(),
        )
    }

    private fun estimateTotalWeight(
        items: List<Pair<Int, DetectedDishItemDto>>
    ): WeightEstimate {
        val extracted = items.mapNotNull { (index, _) -> confirmedItemWeights[index] }
            .filter { it > 0.0 }
        val sum = extracted.sum()
        return if (sum > 0.0) {
            WeightEstimate(
                grams = sum,
                fromApi = extracted.size == items.size,
                weightedItems = extracted.size,
                selectedItems = items.size
            )
        } else {
            WeightEstimate(
                grams = 100.0,
                fromApi = false,
                weightedItems = 0,
                selectedItems = items.size
            )
        }
    }

    private fun scaledValue(value: Double?, index: Int): Double? {
        val base = sourceItemWeights[index]
        val current = confirmedItemWeights[index]
        val safeValue = value ?: return null

        if (base != null && base > 0.0 && current != null && current > 0.0) {
            return safeValue * (current / base)
        }

        return safeValue
    }

    private fun hasAnyNutrition(summary: NutritionSummaryDto): Boolean {
        return summary.caloriesKcal != null ||
            summary.proteinG != null ||
            summary.fatG != null ||
            summary.carbsG != null
    }

    private fun parseWeightFromServing(serving: String?): Double? {
        val safe = serving?.trim().orEmpty()
        if (safe.isBlank()) return null

        val kgRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|kgs|kilogram|kilograms|кг)(?:\b|$)""", RegexOption.IGNORE_CASE)
        kgRegex.find(safe)?.let { match ->
            return match.groupValues.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?.times(1000.0)
        }

        val gramsRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(g|gr|gram|grams|г|гр|грамм|грамма|граммов)(?:\b|$)""", RegexOption.IGNORE_CASE)
        gramsRegex.find(safe)?.let { match ->
            return match.groupValues.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
        }

        return safe.replace(',', '.').toDoubleOrNull()
    }

    private fun persistImage(sourcePath: String): String {
        val source = File(sourcePath)
        if (!source.exists()) return sourcePath

        val targetDir = File(requireContext().filesDir, "photo_ai_dishes")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val target = File(targetDir, "dish_${System.currentTimeMillis()}.jpg")
        source.copyTo(target, overwrite = true)
        return target.absolutePath
    }

    private fun renderLoading(root: View, loading: Boolean) {
        root.findViewById<ProgressBar>(R.id.progressAnalysis).isVisible = loading
        root.findViewById<Button>(R.id.buttonRetryAnalysis).isEnabled = !loading
        root.findViewById<Button>(R.id.buttonRetake).isEnabled = !loading
        root.findViewById<Button>(R.id.buttonSaveDraft).isEnabled =
            !loading && analyzeResponse != null && selectedItemPositions.isNotEmpty()
        root.findViewById<TextView>(R.id.textLoadingHint).isVisible = loading
    }

    private fun showError(root: View, message: String) {
        root.findViewById<TextView>(R.id.textError).apply {
            isVisible = true
            text = message
        }
    }

    private fun clearError(root: View) {
        root.findViewById<TextView>(R.id.textError).isVisible = false
    }

    private fun buildSelectionStatus(selectedCount: Int, totalCount: Int): String {
        return when {
            totalCount == 0 ->
                "LogMeal не выделил отдельные позиции, блюдо будет сохранено как единый результат."
            selectedCount == 0 ->
                "Сейчас ничего не выбрано: итоговое блюдо не будет сформировано."
            selectedCount == totalCount ->
                "Сейчас учитываются все распознанные позиции: $selectedCount из $totalCount."
            else ->
                "Сейчас учитываются $selectedCount из $totalCount распознанных позиций."
        }
    }

    private fun buildNote(
        response: AnalyzeFoodResponseDto,
        apiWeightEstimate: WeightEstimate,
        currentWeight: Double
    ): String {
        val backendNote = response.notes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val weightPart = when {
            apiWeightEstimate.selectedItems == 0 ->
                "Сначала выберите хотя бы одну позицию."
            apiWeightEstimate.fromApi ->
                "LogMeal прислал оценку веса выбранных позиций: примерно ${formatPlain(apiWeightEstimate.grams)} г."
            else ->
                "LogMeal не прислал понятную граммовку для выбранных позиций, поэтому в поле ниже стоит стартовое значение 100 г."
        }

        val customWeightPart = when {
            currentWeight <= 0.0 -> "Укажите вес больше нуля, чтобы получить корректный пересчёт на 100 г."
            apiWeightEstimate.fromApi && kotlin.math.abs(currentWeight - apiWeightEstimate.grams) > 0.1 ->
                "Сейчас вы вручную указали ${formatPlain(currentWeight)} г, и именно этот вес будет использован для нормализации к 100 г."
            else ->
                "Текущее значение веса будет использовано для нормализации блюда к 100 г при сохранении."
        }

        val apiPart = if (backendNote.isNotBlank()) backendNote else ""
        return listOf(weightPart, customWeightPart, apiPart)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun buildSummaryContext(weightEstimate: WeightEstimate): String {
        return when {
            weightEstimate.selectedItems == 0 ->
                "Сейчас КБЖУ не считаются, потому что не выбрана ни одна позиция."
            weightEstimate.fromApi ->
                "Карточки выше показывают КБЖУ всей выбранной порции примерно на ${formatPlain(weightEstimate.grams)} г."
            else ->
                "Карточки выше показывают КБЖУ всей выбранной порции, но точная граммовка от LogMeal не пришла."
        }
    }

    private fun buildPer100Preview(summary: NutritionSummaryDto, totalWeight: Double): String {
        if (totalWeight <= 0.0) {
            return "Чтобы получить корректный пересчёт на 100 г, укажите вес больше нуля."
        }

        val factor = 100.0 / totalWeight
        val calories = (summary.caloriesKcal ?: 0.0) * factor
        val protein = (summary.proteinG ?: 0.0) * factor
        val fat = (summary.fatG ?: 0.0) * factor
        val carbs = (summary.carbsG ?: 0.0) * factor

        return "Эквивалент на 100 г при текущем весе: ${formatPlain(calories)} ккал, Б ${formatPlain(protein)} г, Ж ${formatPlain(fat)} г, У ${formatPlain(carbs)} г"
    }

    private fun applyEstimatedWeight(root: View, grams: Double) {
        isApplyingWeightText = true
        root.findViewById<EditText>(R.id.editEstimatedWeight).setText(formatPlain(grams))
        isApplyingWeightText = false
    }

    private fun formatKcal(value: Double?): String {
        return "${formatPlain(value ?: 0.0)} ккал"
    }

    private fun formatMacro(value: Double?): String {
        return "${formatPlain(value ?: 0.0)} г"
    }

    private fun formatPlain(value: Double): String {
        val safe = value.coerceAtLeast(0.0)
        return if (safe % 1.0 == 0.0) {
            safe.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", safe)
        }
    }

    private fun parseDouble(raw: String?): Double {
        return raw?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    }
}
