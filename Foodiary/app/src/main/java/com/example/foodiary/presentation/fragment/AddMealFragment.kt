package com.example.foodiary.presentation.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.imageLoader
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.local.preferences.FavoriteFoodsStorage
import com.example.foodiary.data.remote.off.OpenFoodFactsApiFactory
import com.example.foodiary.data.repository.AllergenRepositoryImpl
import com.example.foodiary.data.repository.FavoriteFoodsRepositoryImpl
import com.example.foodiary.data.repository.FoodImportRepositoryImpl
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.databinding.FragmentAddMealBinding
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.HistoryMealTemplate
import com.example.foodiary.domain.model.FoodRecommendation
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.Meal
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.GetDailyNutritionUseCase
import com.example.foodiary.domain.usecase.GetHistoryMealTemplatesUseCase
import com.example.foodiary.domain.usecase.GetPersonalizedFoodRecommendationsUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.ImportFoodFromSearchItemUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import com.example.foodiary.data.repository.UserRepositoryImpl
import com.example.foodiary.presentation.viewmodel.AddMealViewModel
import com.example.foodiary.presentation.viewmodel.AddMealViewModelFactory
import com.example.foodiary.presentation.dialog.BarcodeImportDialogFragment
import com.example.foodiary.presentation.dialog.CreateChoiceDialogFragment
import com.example.foodiary.presentation.dialog.HistoryMealTemplatesDialogFragment
import com.example.foodiary.presentation.util.FoodiaryMotionPattern
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.prepareFoodiaryTransition
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import java.util.Locale

class AddMealFragment : Fragment(R.layout.fragment_add_meal) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_TARGET_DAY_START = "arg_target_day_start"
        private const val CUSTOM_FOODS_LIMIT = 100
        private const val REMOTE_SEARCH_MIN_QUERY_LENGTH = 2

        fun newInstance(
            mealType: MealType,
            targetDayStart: Long = normalizeDayStart(System.currentTimeMillis())
        ): AddMealFragment {
            return AddMealFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putLong(ARG_TARGET_DAY_START, targetDayStart)
                }
            }
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
    }

    private enum class LocalSection {
        RECENT,
        FAVORITES,
        CUSTOM
    }

    private sealed class SearchRow {
        data class LocalFoodRow(
            val food: Food,
            val recommendationHint: String? = null
        ) : SearchRow()
        data class RemoteFoodRow(val item: FoodSearchItem) : SearchRow()
    }

    private var _binding: FragmentAddMealBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddMealViewModel by viewModels { provideFactory() }

    private lateinit var unifiedAdapter: UnifiedFoodsAdapter
    private lateinit var favoriteFoodsStorage: FavoriteFoodsStorage
    private lateinit var localFoodRepository: FoodRepositoryImpl
    private lateinit var mealRepository: MealRepositoryImpl
    private lateinit var addMealUseCase: AddMealUseCase
    private lateinit var historyMealTemplatesUseCase: GetHistoryMealTemplatesUseCase


    private var currentLocalSearchFoods: List<Food> = emptyList()
    private var currentRecommendations: List<FoodRecommendation> = emptyList()
    private var currentRecommendedFoods: List<Food> = emptyList()
    private var currentRecommendationHints: Map<String, String> = emptyMap()
    private var currentFavoriteFoods: List<Food> = emptyList()
    private var currentCustomFoods: List<Food> = emptyList()
    private var currentRemoteFoods: List<FoodSearchItem> = emptyList()
    private var currentSeedFoods: List<Food> = emptyList()
    private var currentHistoryTemplates: List<HistoryMealTemplate> = emptyList()

    private var currentSection: LocalSection = LocalSection.RECENT
    private var lastOpenedFoodId: String? = null
    private var pendingFavoriteRemoteFoodId: String? = null

    private val selectedMealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    private val targetDayStart: Long by lazy {
        if (arguments?.containsKey(ARG_TARGET_DAY_START) == true) {
            arguments?.getLong(ARG_TARGET_DAY_START) ?: normalizeDayStart(System.currentTimeMillis())
        } else {
            normalizeDayStart(System.currentTimeMillis())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAddMealBinding.bind(view)

        val database = AppDatabase.getInstance(requireContext())
        favoriteFoodsStorage = FavoriteFoodsStorage(requireContext())
        localFoodRepository = FoodRepositoryImpl(
            foodDao = database.foodDao()
        )
        mealRepository = MealRepositoryImpl(
            mealDao = database.mealDao(),
            foodRepository = localFoodRepository
        )
        addMealUseCase = AddMealUseCase(mealRepository)
        historyMealTemplatesUseCase = GetHistoryMealTemplatesUseCase(
            mealRepository = mealRepository,
            foodRepository = localFoodRepository
        )

        setupMealTypeUi()
        setupAdapter()
        setupListeners()
        observeViewModel()
        setupInitialUiState()
        reloadSectionData()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            reloadSectionData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMealTypeUi() {
        binding.textMealTypeValue.text = mealTypeToLabel(selectedMealType)
        binding.textMealDateValue.text = formatTargetDayLabel()
    }

    private fun setupInitialUiState() {
        binding.textError.visibility = View.GONE
        binding.textLocalResultsHint.visibility = View.GONE
        binding.progressRemoteSearch.visibility = View.GONE
        binding.textRemoteSearchStatus.visibility = View.GONE
        binding.layoutInlineSearchStatus.visibility = View.GONE
        binding.buttonShowMore.visibility = View.GONE

        updateSectionChips()
        renderSearchResults()
    }

    private fun setupAdapter() {
        unifiedAdapter = UnifiedFoodsAdapter(
            imageBinder = ::bindImageRef,
            favoriteIdsProvider = { favoriteFoodsStorage.getAllFavoriteIds() },
            onLocalClick = { food -> openProductConfig(food.id) },
            onRemoteClick = { item -> viewModel.importFromRemoteItem(item, openAfterImport = true) },
            onLocalFavoriteClick = { food -> toggleFavoriteFromList(food) },
            onRemoteFavoriteClick = { item -> handleRemoteFavoriteClick(item) },
            onLocalMenuClick = { anchor, food -> showCustomFoodActions(anchor, food) }
        )
        binding.recyclerFoods.layoutManager = LinearLayoutManager(requireContext())
        // The list is frequently rebuilt while search/templates update. RecyclerView's default
        // animator can recycle a tmp-detached holder during fast navigation, so row reveal motion
        // is handled inside the ViewHolder instead.
        binding.recyclerFoods.itemAnimator = null
        binding.recyclerFoods.adapter = unifiedAdapter
    }

    private fun setupListeners() {
        binding.buttonBack.setDebouncedClickListener {
            popBackStackSafely()
        }

        binding.editSearchFood.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            viewModel.onSearchQueryChanged(query)
            renderSearchResults()
        }
        binding.editSearchFood.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(textView)
                binding.recyclerFoods.requestFocus()
                true
            } else {
                false
            }
        }

        binding.chipSectionRecent.setOnClickListener {
            currentSection = LocalSection.RECENT
            updateSectionChips()
            renderSearchResults()
        }

        binding.chipSectionFavorites.setOnClickListener {
            currentSection = LocalSection.FAVORITES
            updateSectionChips()
            renderSearchResults()
        }

        binding.chipSectionCustom.setOnClickListener {
            currentSection = LocalSection.CUSTOM
            updateSectionChips()
            renderSearchResults()
        }

        binding.buttonCreateFood.setDebouncedClickListener {
            CreateChoiceDialogFragment.newInstance(selectedMealType)
                .show(parentFragmentManager, "create_choice")
        }

        binding.buttonQuickBarcode.setDebouncedClickListener {
            openBarcodeScanner()
        }

        binding.buttonPhotoAnalyze.setDebouncedClickListener {
            openFoodPhotoCamera()
        }

        binding.buttonHistoryTemplates.setDebouncedClickListener {
            openHistoryTemplatesDialog()
        }

        binding.buttonShowMore.setOnClickListener {
            viewModel.loadMoreRemoteFoods()
        }

        parentFragmentManager.setFragmentResultListener(
            BarcodeImportDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val barcode = bundle.getString(BarcodeImportDialogFragment.RESULT_BARCODE).orEmpty()
            if (barcode.isNotBlank()) {
                viewModel.importByBarcode(barcode)
            }
        }

        childFragmentManager.setFragmentResultListener(
            HistoryMealTemplatesDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val templateId = bundle.getString(HistoryMealTemplatesDialogFragment.RESULT_TEMPLATE_ID).orEmpty()
            currentHistoryTemplates.firstOrNull { it.id == templateId }?.let { template ->
                applyHistoryTemplate(template)
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun observeViewModel() {
        viewModel.foods.observe(viewLifecycleOwner) { foods ->
            currentLocalSearchFoods = foods
            renderSearchResults()
        }

        viewModel.recommendedFoods.observe(viewLifecycleOwner) { recommendations ->
            currentRecommendations = recommendations
            currentRecommendedFoods = recommendations.map { it.food }
            currentRecommendationHints = recommendations.associate { recommendation ->
                val hint = recommendation.secondaryReason?.let { "${recommendation.primaryReason}. $it" }
                    ?: recommendation.primaryReason
                recommendation.food.id to hint
            }
            renderSearchResults()
        }

        viewModel.remoteFoods.observe(viewLifecycleOwner) { items ->
            currentRemoteFoods = items
            renderSearchResults()
        }

        viewModel.remoteSearchStatus.observe(viewLifecycleOwner) { status ->
            binding.textRemoteSearchStatus.text = status.orEmpty()
            updateInlineSearchStatusVisibility()
        }

        viewModel.canLoadMoreRemoteFoods.observe(viewLifecycleOwner) {
            updateShowMoreVisibility()
        }

        viewModel.selectedFoodId.observe(viewLifecycleOwner) { foodId ->
            if (!foodId.isNullOrBlank() && foodId != lastOpenedFoodId) {
                lastOpenedFoodId = foodId
                reloadSectionData()
                openProductConfig(foodId)
            }
        }

        viewModel.silentlyImportedFoodId.observe(viewLifecycleOwner) { foodId ->
            if (foodId.isNullOrBlank()) return@observe

            if (pendingFavoriteRemoteFoodId == foodId && !favoriteFoodsStorage.isFavorite(foodId)) {
                favoriteFoodsStorage.toggleFavorite(foodId)
            }

            pendingFavoriteRemoteFoodId = null
            reloadSectionData()
            binding.textRemoteSearchStatus.text = "Продукт добавлен в избранное"
            updateInlineSearchStatusVisibility()
            viewModel.onSilentlyImportedFoodHandled()
        }

        viewModel.isImporting.observe(viewLifecycleOwner) { importing ->
            binding.progressImport.visibility = if (importing) View.VISIBLE else View.GONE
            binding.buttonQuickBarcode.isEnabled = !importing
            binding.buttonPhotoAnalyze.isEnabled = !importing
            binding.buttonHistoryTemplates.isEnabled = !importing
        }

        viewModel.isRemoteSearching.observe(viewLifecycleOwner) { searching ->
            binding.progressRemoteSearch.visibility = if (shouldShowRemoteState()) {
                if (searching) View.VISIBLE else View.GONE
            } else {
                View.GONE
            }
            renderSearchResults()
            updateInlineSearchStatusVisibility()
            updateShowMoreVisibility()
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            binding.textError.visibility = if (msg.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.textError.text = msg.orEmpty()
            if (!msg.isNullOrBlank()) {
                pendingFavoriteRemoteFoodId = null
            }
        }
    }

    private fun reloadSectionData() {
        if (isTodayTargetDay()) {
            viewModel.loadRecommendations(selectedMealType)
        } else {
            currentRecommendations = emptyList()
            currentRecommendedFoods = emptyList()
            currentRecommendationHints = emptyMap()
        }
        loadFavoriteFoods()
        loadCustomFoods()
        loadSeedFoods()
    }

    private fun loadFavoriteFoods() {
        val favoriteIds = favoriteFoodsStorage.getAllFavoriteIds().toList()

        if (favoriteIds.isEmpty()) {
            currentFavoriteFoods = emptyList()
            renderSearchResults()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            currentFavoriteFoods = try {
                localFoodRepository.getFoodsByIds(favoriteIds)
            } catch (_: Exception) {
                emptyList()
            }
            if (_binding == null) return@launch
            renderSearchResults()
        }
    }

    private fun loadCustomFoods() {
        viewLifecycleOwner.lifecycleScope.launch {
            currentCustomFoods = try {
                localFoodRepository.getCustomFoods(CUSTOM_FOODS_LIMIT)
            } catch (_: Exception) {
                emptyList()
            }
            if (_binding == null) return@launch
            renderSearchResults()
        }
    }

    private fun loadSeedFoods() {
        viewLifecycleOwner.lifecycleScope.launch {
            currentSeedFoods = try {
                localFoodRepository.getFoodsByIds(seedIdsForMealType(selectedMealType))
            } catch (_: Exception) {
                emptyList()
            }
            if (_binding == null) return@launch
            renderSearchResults()
        }
    }

    private fun openProductConfig(foodId: String) {
        replaceFragmentSafely(
            ProductConfigFragment.newInstance(
                mealType = selectedMealType,
                foodId = foodId,
                targetDayStartTimestamp = targetDayStart
            )
        )
    }

    private fun openBarcodeScanner() {
        replaceFragmentSafely(
            BarcodeScannerFragment.newInstance(),
            motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y
        )
    }

    private fun openFoodPhotoCamera() {
        replaceFragmentSafely(
            FoodPhotoCameraFragment.newInstance(selectedMealType, targetDayStart),
            motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y
        )
    }

    private fun openHistoryTemplatesDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.buttonHistoryTemplates.isEnabled = false

            try {
                val templates = historyMealTemplatesUseCase(selectedMealType)
                if (templates.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Пока нет шаблона из истории")
                        .setMessage(
                            "Foodiary ещё не увидел повторяющийся ${mealTypeToLabel(selectedMealType).lowercase(Locale("ru"))}. " +
                                "Чтобы здесь появился вариант \"Как обычно\", соберите похожий приём пищи хотя бы в 2 разных днях."
                        )
                        .setPositiveButton("Понятно", null)
                        .show()
                    return@launch
                }

                showHistoryTemplatesBottomSheet(templates)
            } catch (_: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Не удалось собрать шаблоны из истории",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                if (_binding != null) {
                    binding.buttonHistoryTemplates.isEnabled = true
                }
            }
        }
    }

    private fun showHistoryTemplatesBottomSheet(templates: List<HistoryMealTemplate>) {
        currentHistoryTemplates = templates

        val existingDialog =
            childFragmentManager.findFragmentByTag("history_meal_templates") as? HistoryMealTemplatesDialogFragment
        existingDialog?.dismissAllowingStateLoss()

        HistoryMealTemplatesDialogFragment.newInstance(
            templates = ArrayList(templates),
            mealLabel = mealTypeToLabel(selectedMealType).lowercase(Locale("ru")),
            dayLabel = formatTargetDayLabel()
        ).show(childFragmentManager, "history_meal_templates")
    }

    private fun applyHistoryTemplate(template: HistoryMealTemplate) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val baseTimestamp = buildDefaultMealTimestampForTargetDay()
                template.items.forEachIndexed { index, item ->
                    addMealUseCase(
                        Meal(
                            foodId = item.foodId,
                            quantityInGrams = item.quantityInGrams,
                            mealType = selectedMealType,
                            timestamp = baseTimestamp + index * 60_000L,
                            note = ""
                        )
                    )
                }

                Toast.makeText(
                    requireContext(),
                    "Добавили привычный приём пищи",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMealScreenAfterAdd()
            } catch (_: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Не удалось добавить шаблон из истории",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun navigateToMealScreenAfterAdd() {
        val fragmentManager = activity?.supportFragmentManager ?: return
        if (fragmentManager.isStateSaved) return

        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }

        val current = fragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is MealDetailsFragment) return

        val mealDetailsFragment = MealDetailsFragment.newInstance(selectedMealType, targetDayStart)
        prepareFoodiaryTransition(current, mealDetailsFragment, FoodiaryMotionPattern.FORWARD_AXIS_X)
        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, mealDetailsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun isTodayTargetDay(): Boolean {
        return targetDayStart == normalizeDayStart(System.currentTimeMillis())
    }

    private fun formatTargetDayLabel(): String {
        val formatter = java.text.SimpleDateFormat("d MMMM", Locale("ru"))
        return if (isTodayTargetDay()) {
            "Сегодня, ${formatter.format(java.util.Date(targetDayStart))}"
        } else {
            formatter.format(java.util.Date(targetDayStart))
        }
    }

    private fun buildDefaultMealTimestampForTargetDay(): Long {
        if (isTodayTargetDay()) return System.currentTimeMillis()

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = targetDayStart
        }
        when (selectedMealType) {
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
            MealType.AFTERNOON_SNACK -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 15)
                calendar.set(java.util.Calendar.MINUTE, 30)
            }
            MealType.LATE_DINNER -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 21)
                calendar.set(java.util.Calendar.MINUTE, 0)
            }
        }
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun toggleFavoriteFromList(food: Food) {
        favoriteFoodsStorage.toggleFavorite(food.id)
        reloadSectionData()
        renderSearchResults()
    }

    private fun handleRemoteFavoriteClick(item: FoodSearchItem) {
        val importedFoodId = remoteImportedId(item)
        if (importedFoodId.isBlank()) return

        if (favoriteFoodsStorage.isFavorite(importedFoodId)) {
            favoriteFoodsStorage.toggleFavorite(importedFoodId)
            reloadSectionData()
            renderSearchResults()
            binding.textRemoteSearchStatus.text = "Продукт убран из избранного"
            updateInlineSearchStatusVisibility()
            return
        }

        pendingFavoriteRemoteFoodId = importedFoodId
        viewModel.importFromRemoteItem(item, openAfterImport = false)
    }

    private fun renderSearchResults() {
        if (_binding == null) return
        val query = binding.editSearchFood.text?.toString()?.trim().orEmpty()
        val isBlankQuery = query.isBlank()

        binding.textSectionTitle.text = buildSectionTitle(isBlankQuery)

        val rows = if (isBlankQuery) {
            getHomeRowsForCurrentSection()
        } else {
            getSearchRowsForCurrentSection(query)
        }

        if (rows.isNotEmpty()) {
            unifiedAdapter.submit(rows)
            binding.recyclerFoods.visibility = View.VISIBLE
            binding.textLocalResultsHint.visibility = View.VISIBLE
            binding.textLocalResultsHint.text = buildSectionHint(
                isBlankQuery = isBlankQuery,
                hasItems = true,
                query = query
            )
        } else {
            unifiedAdapter.submit(emptyList())
            binding.recyclerFoods.visibility = View.GONE
            binding.textLocalResultsHint.visibility = View.VISIBLE
            binding.textLocalResultsHint.text = buildSectionHint(
                isBlankQuery = isBlankQuery,
                hasItems = false,
                query = query
            )
        }

        updateInlineSearchStatusVisibility()
        updateShowMoreVisibility()
    }

    private fun getHomeRowsForCurrentSection(): List<SearchRow> {
        return when (currentSection) {
            LocalSection.RECENT -> {
                buildSmartRecentFoods()
                    .map { food ->
                        SearchRow.LocalFoodRow(
                            food = food,
                            recommendationHint = currentRecommendationHints[food.id]
                        )
                    }
            }
            LocalSection.FAVORITES -> {
                sortLocalFoodsByCurrentContext(currentFavoriteFoods, "")
                    .map { SearchRow.LocalFoodRow(it) }
            }
            LocalSection.CUSTOM -> {
                sortLocalFoodsByCurrentContext(currentCustomFoods, "")
                    .map { SearchRow.LocalFoodRow(it) }
            }
        }
    }

    private fun getSearchRowsForCurrentSection(query: String): List<SearchRow> {
        return when (currentSection) {
            LocalSection.RECENT -> buildUnifiedRecentSearchRows(query)
            LocalSection.FAVORITES -> {
                val favoriteIds = favoriteFoodsStorage.getAllFavoriteIds()
                sortLocalFoodsByCurrentContext(
                    currentLocalSearchFoods.filter { it.id in favoriteIds },
                    query
                ).map { SearchRow.LocalFoodRow(it) }
            }
            LocalSection.CUSTOM -> {
                sortLocalFoodsByCurrentContext(
                    currentLocalSearchFoods.filter { it.isCustom },
                    query
                ).map { SearchRow.LocalFoodRow(it) }
            }
        }
    }

    private fun buildUnifiedRecentSearchRows(query: String): List<SearchRow> {
        val localFoods = sortLocalFoodsByCurrentContext(currentLocalSearchFoods, query)
        val localIds = localFoods.map { it.id }.toSet()

        val remoteFoods = currentRemoteFoods
            .filter { remoteImportedId(it) !in localIds }
            .sortedWith(
                compareByDescending<FoodSearchItem> { remoteRelevanceScore(it, query) }
                    .thenByDescending { remoteCompletenessScore(it) }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )

        val rows = mutableListOf<SearchRow>()
        rows += localFoods.map { food ->
            SearchRow.LocalFoodRow(
                food = food,
                recommendationHint = currentRecommendationHints[food.id]
            )
        }
        rows += remoteFoods.map { SearchRow.RemoteFoodRow(it) }
        return rows
    }

    private fun sortLocalFoodsByCurrentContext(
        foods: List<Food>,
        query: String
    ): List<Food> {
        val recentRank = currentRecommendedFoods
            .mapIndexed { index, food -> food.id to index }
            .toMap()

        return foods.sortedWith(
            compareByDescending<Food> { localRelevanceScore(it, query) }
                .thenBy { recentRank[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { localCompletenessScore(it) }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
    }

    private fun localRelevanceScore(food: Food, query: String): Int {
        if (query.isBlank()) return 0
        val name = food.name.lowercase(Locale.getDefault())
        val queries = buildLocalSearchKeys(query)

        return queries.maxOf { q ->
            when {
                name == q -> 500
                name.startsWith(q) -> 400
                name.contains(" $q") -> 320
                name.contains(q) -> 250
                else -> 0
            }
        }
    }

    private fun buildLocalSearchKeys(query: String): List<String> {
        val lower = query.lowercase(Locale("ru")).trim()
        val aliases = mapOf(
            "ris" to "рис",
            "rice" to "рис",
            "grechka" to "гречка",
            "kurica" to "курица",
            "chicken" to "курица",
            "tvorog" to "творог",
            "ovsyanka" to "овсянка",
            "oatmeal" to "овсянка"
        )
        return linkedSetOf(lower, aliases[lower]).filterNotNull().filter { it.isNotBlank() }
    }

    private fun remoteRelevanceScore(item: FoodSearchItem, query: String): Int {
        if (query.isBlank()) return 0
        val name = item.name.lowercase(Locale.getDefault())
        val q = query.lowercase(Locale.getDefault())

        return when {
            name == q -> 500
            name.startsWith(q) -> 390
            name.contains(" $q") -> 300
            name.contains(q) -> 230
            else -> 0
        }
    }

    private fun localCompletenessScore(food: Food): Int {
        var score = 0
        if (food.caloriesPer100g > 0.0) score += 1
        if (food.proteinPer100g >= 0.0) score += 1
        if (food.fatPer100g >= 0.0) score += 1
        if (food.carbsPer100g >= 0.0) score += 1
        return score
    }

    private fun remoteCompletenessScore(item: FoodSearchItem): Int {
        var score = 0
        if ((item.caloriesPer100g ?: 0.0) > 0.0) score += 1
        if (item.proteinPer100g != null) score += 1
        if (item.fatPer100g != null) score += 1
        if (item.carbsPer100g != null) score += 1
        return score
    }

    private fun buildSmartRecentFoods(): List<Food> {
        if (currentRecommendedFoods.isNotEmpty()) {
            return currentRecommendedFoods
        }

        return (currentFavoriteFoods + currentCustomFoods + currentSeedFoods)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Food> { localCompletenessScore(it) }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
    }

    private fun seedIdsForMealType(type: MealType): List<String> {
        return when (type) {
            MealType.BREAKFAST -> listOf(
                "oatmeal",
                "oat_porridge",
                "banana",
                "apple",
                "berries_mix",
                "strawberries",
                "greek_yogurt",
                "skyr",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "egg",
                "egg_white",
                "wholegrain_bread",
                "rye_bread",
                "milk_2_5",
                "peanut_butter",
                "chia_seeds",
                "avocado"
            )
            MealType.LUNCH -> listOf(
                "chicken_breast",
                "turkey_fillet",
                "beef_lean",
                "pork_tenderloin",
                "rice",
                "brown_rice",
                "buckwheat",
                "quinoa",
                "bulgur",
                "pasta",
                "tomato",
                "cucumber",
                "broccoli",
                "bell_pepper",
                "salmon",
                "cod",
                "potato",
                "sweet_potato",
                "lentils",
                "chickpeas",
                "beans_red"
            )
            MealType.DINNER -> listOf(
                "salmon",
                "trout",
                "cod",
                "tuna",
                "turkey_fillet",
                "chicken_breast",
                "tofu",
                "lentils",
                "broccoli",
                "avocado",
                "cucumber",
                "tomato",
                "cauliflower",
                "zucchini",
                "eggplant",
                "mushrooms",
                "egg",
                "potato"
            )
            MealType.SNACK -> listOf(
                "apple",
                "banana",
                "pear",
                "orange",
                "kiwi",
                "grapes",
                "watermelon",
                "melon",
                "greek_yogurt",
                "kefir_2_5",
                "skyr",
                "almonds",
                "cashews",
                "pistachios",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "berries_mix",
                "hummus",
                "carrot",
                "dark_chocolate"
            )
            MealType.AFTERNOON_SNACK -> listOf(
                "apple",
                "banana",
                "pear",
                "orange",
                "mandarin",
                "strawberries",
                "blueberries",
                "greek_yogurt",
                "skyr",
                "almonds",
                "walnuts",
                "peanuts",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "berries_mix",
                "dried_apricots",
                "raisins",
                "dark_chocolate",
                "hummus",
                "carrot"
            )
            MealType.LATE_DINNER -> listOf(
                "kefir_2_5",
                "skyr",
                "cottage_cheese",
                "cottage_cheese_lowfat",
                "egg_white",
                "egg",
                "cod",
                "turkey_fillet",
                "chicken_breast",
                "broccoli",
                "cauliflower",
                "zucchini",
                "mushrooms",
                "cucumber",
                "tomato",
                "lettuce"
            )
        }
    }

    private fun showCustomFoodActions(anchor: View, food: Food) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Редактировать")
        if (food.category == "custom_recipe") {
            popup.menu.add(0, 2, 1, "Открыть рецепт")
        }
        popup.menu.add(0, 3, 2, "Удалить")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    openCustomEditor(food)
                    true
                }
                2 -> {
                    com.example.foodiary.presentation.dialog.RecipeDetailsBottomSheet
                        .newInstance(food.id)
                        .show(parentFragmentManager, "recipe_details")
                    true
                }
                3 -> {
                    deleteCustomFood(food)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun openCustomEditor(food: Food) {
        val fragment = if (food.category == "custom_recipe") {
            CreateRecipeFragment.newEditInstance(selectedMealType, food.id)
        } else {
            CreateCustomFoodFragment.newEditInstance(selectedMealType, food.id)
        }

        replaceFragmentSafely(fragment, motionPattern = FoodiaryMotionPattern.MODAL_AXIS_Y)
    }

    private fun deleteCustomFood(food: Food) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(requireContext())
                val allergenRepository = AllergenRepositoryImpl(
                    allergenDao = db.allergenDao(),
                    foodAllergenDao = db.foodAllergenDao(),
                    userRestrictionDao = db.userRestrictionDao()
                )
                if (food.category == "custom_recipe") {
                    db.recipeDao().getRecipeByFoodId(food.id)?.let { recipe ->
                        db.recipeDao().deleteIngredientsForRecipe(recipe.id)
                        db.recipeDao().deleteRecipeById(recipe.id)
                    }
                }
                allergenRepository.deleteFoodAllergens(food.id)
                db.foodDao().deleteFoodById(food.id)
                favoriteFoodsStorage.removeFavorite(food.id)
                loadFavoriteFoods()
                loadCustomFoods()
                loadSeedFoods()
                renderSearchResults()
                binding.textRemoteSearchStatus.text = if (food.category == "custom_recipe") "Блюдо удалено" else "Продукт удалён"
                updateInlineSearchStatusVisibility()
            } catch (_: Exception) {
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Не удалось удалить элемент"
            }
        }
    }

    private fun buildSectionTitle(isBlankQuery: Boolean): String {
        return if (isBlankQuery) {
            when (currentSection) {
                LocalSection.RECENT -> "Рекомендации на ${mealTypeToLabel(selectedMealType)}"
                LocalSection.FAVORITES -> "Избранные продукты"
                LocalSection.CUSTOM -> "Мои продукты"
            }
        } else {
            when (currentSection) {
                LocalSection.RECENT -> "Результаты поиска"
                LocalSection.FAVORITES -> "Поиск в избранном"
                LocalSection.CUSTOM -> "Поиск в моих продуктах"
            }
        }
    }

    private fun buildSectionHint(
        isBlankQuery: Boolean,
        hasItems: Boolean,
        query: String
    ): String {
        if (hasItems) {
            return when (currentSection) {
                LocalSection.RECENT ->
                    if (isBlankQuery) {
                        "Умные рекомендации по вашим привычкам, избранному и типу приёма пищи"
                    } else {
                        "Показаны подходящие локальные и внешние продукты по одному запросу"
                    }
                LocalSection.FAVORITES ->
                    if (isBlankQuery) {
                        "Ваши сохранённые избранные продукты"
                    } else {
                        "Результаты внутри раздела «Избранное»"
                    }
                LocalSection.CUSTOM ->
                    if (isBlankQuery) {
                        "Продукты и блюда, которые вы создали сами. Откройте меню по трём точкам для действий"
                    } else {
                        "Результаты внутри раздела «Мои продукты»"
                    }
            }
        }

        return when (currentSection) {
            LocalSection.RECENT ->
                if (isBlankQuery) {
                    "Здесь будут умные рекомендации по вашим привычкам и базовой полезной подборке."
                } else if (query.length < REMOTE_SEARCH_MIN_QUERY_LENGTH) {
                    "Введите ещё минимум ${REMOTE_SEARCH_MIN_QUERY_LENGTH - query.length} символ(а), чтобы подключить общую базу продуктов."
                } else if (viewModel.isRemoteSearching.value == true) {
                    "Ищу продукты с полным КБЖУ в общей базе..."
                } else {
                    "По этому запросу ничего не найдено."
                }
            LocalSection.FAVORITES ->
                if (isBlankQuery) {
                    "В избранном пока пусто. Нажмите сердечко у нужного продукта."
                } else {
                    "В разделе «Избранное» по этому запросу ничего не найдено."
                }
            LocalSection.CUSTOM ->
                if (isBlankQuery) {
                    "Здесь будут появляться созданные вами продукты."
                } else {
                    "В разделе «Мои продукты» по этому запросу ничего не найдено."
                }
        }
    }

    private fun updateSectionChips() {
        val selectedBg = R.drawable.bg_product_config_portion_chip_selected
        val normalBg = R.drawable.bg_product_config_portion_chip

        binding.chipSectionRecent.setBackgroundResource(
            if (currentSection == LocalSection.RECENT) selectedBg else normalBg
        )
        binding.chipSectionFavorites.setBackgroundResource(
            if (currentSection == LocalSection.FAVORITES) selectedBg else normalBg
        )
        binding.chipSectionCustom.setBackgroundResource(
            if (currentSection == LocalSection.CUSTOM) selectedBg else normalBg
        )
    }

    private fun shouldShowRemoteState(): Boolean {
        val query = binding.editSearchFood.text?.toString()?.trim().orEmpty()
        return currentSection == LocalSection.RECENT && query.length >= REMOTE_SEARCH_MIN_QUERY_LENGTH
    }

    private fun updateInlineSearchStatusVisibility() {
        val shouldShow = shouldShowRemoteState()
        val hasStatus = !binding.textRemoteSearchStatus.text.isNullOrBlank()
        val searching = viewModel.isRemoteSearching.value == true

        binding.progressRemoteSearch.visibility =
            if (shouldShow && searching) View.VISIBLE else View.GONE
        binding.textRemoteSearchStatus.visibility =
            if (shouldShow && hasStatus) View.VISIBLE else View.GONE
        binding.layoutInlineSearchStatus.visibility =
            if (binding.progressRemoteSearch.isVisible || binding.textRemoteSearchStatus.isVisible) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun updateShowMoreVisibility() {
        val query = binding.editSearchFood.text?.toString()?.trim().orEmpty()
        val canShow =
            currentSection == LocalSection.RECENT &&
                    query.length >= REMOTE_SEARCH_MIN_QUERY_LENGTH &&
                    viewModel.canLoadMoreRemoteFoods.value == true &&
                    viewModel.isRemoteSearching.value != true

        binding.buttonShowMore.visibility = if (canShow) View.VISIBLE else View.GONE
    }

    private fun bindImageRef(imageView: ImageView, imageRef: String?) {
        val normalized = imageRef?.trim().orEmpty()
        val placeholder = ColorDrawable(Color.parseColor("#F4D4FB"))

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

    private fun remoteImportedId(item: FoodSearchItem): String {
        val barcode = item.barcode.trim()
        return if (barcode.isBlank()) "" else "off_$barcode"
    }

    private fun mealTypeToLabel(type: MealType): String {
        return when (type) {
            MealType.AFTERNOON_SNACK -> "Полдник"
            MealType.LATE_DINNER -> "Поздний ужин"
            MealType.BREAKFAST -> "Завтрак"
            MealType.LUNCH -> "Обед"
            MealType.DINNER -> "Ужин"
            MealType.SNACK -> "Перекус"
        }
    }

    private fun formatCompactNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun provideFactory(): AddMealViewModelFactory {
        val db = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(foodDao = db.foodDao())
        val mealRepository = MealRepositoryImpl(
            mealDao = db.mealDao(),
            foodRepository = foodRepository
        )
        val userRepository = UserRepositoryImpl(
            userDao = db.userDao(),
            allergenDao = db.allergenDao(),
            userRestrictionDao = db.userRestrictionDao()
        )
        val favoriteFoodsRepository = FavoriteFoodsRepositoryImpl(requireContext())

        val addMealUseCase = AddMealUseCase(mealRepository)
        val nutritionTargetsResolver = com.example.foodiary.presentation.util.EffectiveNutritionTargetsResolver(requireContext())
        val getDailyNutritionUseCase = GetDailyNutritionUseCase(
            mealRepository = mealRepository,
            foodRepository = foodRepository
        )

        val api = OpenFoodFactsApiFactory.create(requireContext())
        val allergenRepository = AllergenRepositoryImpl(
            allergenDao = db.allergenDao(),
            foodAllergenDao = db.foodAllergenDao(),
            userRestrictionDao = db.userRestrictionDao()
        )
        val importRepo = FoodImportRepositoryImpl(
            api = api,
            foodDao = db.foodDao(),
            allergenRepository = allergenRepository
        )

        val importByBarcodeUseCase = ImportFoodByBarcodeUseCase(importRepo)
        val importFromSearchItemUseCase = ImportFoodFromSearchItemUseCase(importRepo)
        val searchUseCase = SearchFoodsByNameUseCase(importRepo)
        val recommendationUseCase = GetPersonalizedFoodRecommendationsUseCase(
            foodRepository = foodRepository,
            mealRepository = mealRepository,
            userRepository = userRepository,
            favoriteFoodsRepository = favoriteFoodsRepository,
            allergenRepository = allergenRepository,
            nutritionTargetsResolver = { user -> nutritionTargetsResolver.resolve(user) },
            getDailyNutritionUseCase = getDailyNutritionUseCase
        )

        return AddMealViewModelFactory(
            foodRepository = foodRepository,
            addMealUseCase = addMealUseCase,
            importFoodByBarcodeUseCase = importByBarcodeUseCase,
            importFoodFromSearchItemUseCase = importFromSearchItemUseCase,
            searchFoodsByNameUseCase = searchUseCase,
            getPersonalizedFoodRecommendationsUseCase = recommendationUseCase
        )
    }

    private class UnifiedFoodsAdapter(
        private val imageBinder: (ImageView, String?) -> Unit,
        private val favoriteIdsProvider: () -> Set<String>,
        private val onLocalClick: (Food) -> Unit,
        private val onRemoteClick: (FoodSearchItem) -> Unit,
        private val onLocalFavoriteClick: (Food) -> Unit,
        private val onRemoteFavoriteClick: (FoodSearchItem) -> Unit,
        private val onLocalMenuClick: (View, Food) -> Unit
    ) : RecyclerView.Adapter<UnifiedFoodsAdapter.VH>() {

        private val items = mutableListOf<SearchRow>()
        private val animatedItemIds = hashSetOf<Long>()

        init {
            setHasStableIds(true)
        }

        fun submit(newItems: List<SearchRow>) {
            val newIds = newItems.map { rowStableId(it) }.toSet()
            animatedItemIds.retainAll(newIds)
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getItemId(position: Int): Long {
            return rowStableId(items[position])
        }

        private fun rowStableId(item: SearchRow): Long {
            return when (item) {
                is SearchRow.LocalFoodRow -> item.food.id.hashCode().toLong()
                is SearchRow.RemoteFoodRow -> ("remote_" + item.item.barcode.trim()).hashCode().toLong()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val context = parent.context

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 10)
                }
                setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 18).toFloat()
                    setColor(Color.parseColor("#FFFBEA"))
                }
            }

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 64), dp(context, 64))
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 16).toFloat()
                    setColor(Color.parseColor("#F4D4FB"))
                }
            }

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp(context, 12)
                }
            }

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val titleView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                textSize = 15f
                setTextColor(Color.parseColor("#2F2433"))
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
            }

            val customBadgeView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(context, 8)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 10).toFloat()
                    setColor(Color.parseColor("#F4D4FB"))
                }
                setPadding(dp(context, 7), dp(context, 4), dp(context, 7), dp(context, 4))
                setTextColor(Color.parseColor("#6F3BB7"))
                setTypeface(typeface, Typeface.BOLD)
                textSize = 9f
                text = "МОЙ"
                visibility = View.GONE
            }

            val favoriteBadge = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)).apply {
                    marginStart = dp(context, 8)
                }
                setImageResource(R.drawable.ic_favorite_outline)
                setColorFilter(Color.parseColor("#B9A4E8"))
                alpha = 0.95f
                isClickable = true
                isFocusable = true
            }

            val menuBadge = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(context, 6)
                }
                text = "⋮"
                textSize = 18f
                setTextColor(Color.parseColor("#9F8BC4"))
                visibility = View.GONE
                isClickable = true
                isFocusable = true
            }

            val subtitleView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 4)
                }
                textSize = 13f
                maxLines = 3
                setTextColor(Color.parseColor("#6B5B73"))
            }

            titleRow.addView(titleView)
            titleRow.addView(customBadgeView)
            titleRow.addView(favoriteBadge)
            titleRow.addView(menuBadge)

            textContainer.addView(titleRow)
            textContainer.addView(subtitleView)

            root.addView(imageView)
            root.addView(textContainer)

            return VH(
                itemView = root,
                imageView = imageView,
                titleView = titleView,
                subtitleView = subtitleView,
                customBadgeView = customBadgeView,
                favoriteBadge = favoriteBadge,
                menuBadge = menuBadge,
                imageBinder = imageBinder,
                favoriteIdsProvider = favoriteIdsProvider,
                onLocalClick = onLocalClick,
                onRemoteClick = onRemoteClick,
                onLocalFavoriteClick = onLocalFavoriteClick,
                onRemoteFavoriteClick = onRemoteFavoriteClick,
                onLocalMenuClick = onLocalMenuClick
            )
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val itemId = getItemId(position)
            val shouldAnimate = animatedItemIds.add(itemId)
            holder.bind(item, shouldAnimate)
        }

        override fun onViewRecycled(holder: VH) {
            holder.resetTransientState()
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = items.size

        private class VH(
            itemView: View,
            private val imageView: ImageView,
            private val titleView: TextView,
            private val subtitleView: TextView,
            private val customBadgeView: TextView,
            private val favoriteBadge: ImageView,
            private val menuBadge: TextView,
            private val imageBinder: (ImageView, String?) -> Unit,
            private val favoriteIdsProvider: () -> Set<String>,
            private val onLocalClick: (Food) -> Unit,
            private val onRemoteClick: (FoodSearchItem) -> Unit,
            private val onLocalFavoriteClick: (Food) -> Unit,
            private val onRemoteFavoriteClick: (FoodSearchItem) -> Unit,
            private val onLocalMenuClick: (View, Food) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private var current: SearchRow? = null

            init {
                itemView.setOnClickListener {
                    when (val item = current) {
                        is SearchRow.LocalFoodRow -> onLocalClick(item.food)
                        is SearchRow.RemoteFoodRow -> onRemoteClick(item.item)
                        null -> Unit
                    }
                }

                favoriteBadge.setOnClickListener {
                    when (val item = current) {
                        is SearchRow.LocalFoodRow -> onLocalFavoriteClick(item.food)
                        is SearchRow.RemoteFoodRow -> onRemoteFavoriteClick(item.item)
                        null -> Unit
                    }
                }

                menuBadge.setOnClickListener {
                    when (val item = current) {
                        is SearchRow.LocalFoodRow -> onLocalMenuClick(menuBadge, item.food)
                        else -> Unit
                    }
                }
            }

            fun bind(item: SearchRow, shouldAnimate: Boolean) {
                current = item
                itemView.animate().cancel()
                itemView.alpha = 1f
                itemView.translationY = 0f

                if (shouldAnimate) {
                    itemView.alpha = 0f
                    itemView.translationY = 14f
                    itemView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .start()
                }

                when (item) {
                    is SearchRow.LocalFoodRow -> bindLocal(item.food, item.recommendationHint)
                    is SearchRow.RemoteFoodRow -> bindRemote(item.item)
                }
            }

            fun resetTransientState() {
                itemView.animate().cancel()
                itemView.alpha = 1f
                itemView.translationY = 0f
            }

            private fun bindLocal(food: Food, recommendationHint: String?) {
                val favoriteIds = favoriteIdsProvider()
                val isFavorite = food.id in favoriteIds

                titleView.text = food.name
                customBadgeView.visibility = if (food.isCustom) View.VISIBLE else View.GONE
                menuBadge.visibility = if (food.isCustom) View.VISIBLE else View.GONE
                customBadgeView.text = if (food.category == "custom_recipe") "БЛЮДО" else "МОЙ"
                val descriptor = when {
                    food.category == "custom_recipe" -> "Рецепт пользователя"
                    food.isCustom -> "Создан вами"
                    else -> "База Foodiary"
                }
                subtitleView.text = buildString {
                    recommendationHint?.takeIf { it.isNotBlank() }?.let {
                        append(it)
                        append('\n')
                    }
                    append(descriptor)
                    append('\n')
                    append("${formatNumber(food.caloriesPer100g)} ккал   Б ${formatNumber(food.proteinPer100g)}   Ж ${formatNumber(food.fatPer100g)}   У ${formatNumber(food.carbsPer100g)}")
                }

                favoriteBadge.setImageResource(
                    if (isFavorite) R.drawable.ic_favorite_filled
                    else R.drawable.ic_favorite_outline
                )
                favoriteBadge.contentDescription =
                    if (isFavorite) "Убрать из избранного" else "Добавить в избранное"

                imageBinder(imageView, food.imageUrl)
            }

            private fun bindRemote(item: FoodSearchItem) {
                val importedId = remoteFoodId(item)
                val favoriteIds = favoriteIdsProvider()
                val isFavorite = importedId in favoriteIds
                customBadgeView.visibility = View.GONE
                menuBadge.visibility = View.GONE

                val brand = item.brand?.takeIf { it.isNotBlank() } ?: "из общей базы"
                val kcal = item.caloriesPer100g ?: 0.0
                val protein = item.proteinPer100g ?: 0.0
                val fat = item.fatPer100g ?: 0.0
                val carbs = item.carbsPer100g ?: 0.0

                titleView.text = item.name
                subtitleView.text =
                    "$brand\n${formatNumber(kcal)} ккал   Б ${formatNumber(protein)}   Ж ${formatNumber(fat)}   У ${formatNumber(carbs)}"

                favoriteBadge.setImageResource(
                    if (isFavorite) R.drawable.ic_favorite_filled
                    else R.drawable.ic_favorite_outline
                )
                favoriteBadge.contentDescription =
                    if (isFavorite) "Убрать из избранного" else "Сохранить в избранное"

                imageBinder(imageView, item.imageUrl)
            }

            private fun remoteFoodId(item: FoodSearchItem): String {
                val barcode = item.barcode.trim()
                return if (barcode.isBlank()) "" else "off_$barcode"
            }

            private fun formatNumber(value: Double): String {
                return if (value % 1.0 == 0.0) {
                    value.toInt().toString()
                } else {
                    String.format(Locale.US, "%.1f", value)
                }
            }
        }

        companion object {
            private fun dp(context: android.content.Context, value: Int): Int {
                return (value * context.resources.displayMetrics.density).toInt()
            }
        }
    }
}
