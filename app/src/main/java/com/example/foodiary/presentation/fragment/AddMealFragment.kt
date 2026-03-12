package com.example.foodiary.presentation.fragment

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.remote.off.OpenFoodFactsApiFactory
import com.example.foodiary.data.repository.FoodImportRepositoryImpl
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.databinding.FragmentAddMealBinding
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.ImportFoodFromSearchItemUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import com.example.foodiary.presentation.viewmodel.AddMealViewModel
import com.example.foodiary.presentation.viewmodel.AddMealViewModelFactory

class AddMealFragment : Fragment(R.layout.fragment_add_meal) {

    private var _binding: FragmentAddMealBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddMealViewModel by viewModels { provideFactory() }

    private lateinit var localAdapter: FoodsAdapter
    private lateinit var remoteAdapter: RemoteFoodsAdapter

    private var isRemoteSectionExpanded = false
    private var isBarcodeSectionExpanded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAddMealBinding.bind(view)

        setupAdapters()
        setupSpinner()
        setupListeners()
        observeViewModel()
        setupInitialUiState()
        applyRoundedImage(binding.imageSelectedFood, 22f)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupInitialUiState() {
        binding.cardSelectedFood.visibility = View.GONE
        binding.imageSelectedFood.visibility = View.GONE
        binding.textSelectedFoodNutrition.visibility = View.GONE
        binding.textError.visibility = View.GONE
        binding.textRemoteSearchStatus.visibility = View.GONE
        binding.recyclerRemoteFoods.visibility = View.GONE
        binding.buttonShowMore.visibility = View.GONE
        binding.textLocalResultsHint.visibility = View.GONE

        setRemoteSectionExpanded(false)
        setBarcodeSectionExpanded(false)
    }

    private fun setupAdapters() {
        localAdapter = FoodsAdapter(::bindImageRef) { food ->
            ensureDefaultQuantity()
            viewModel.selectFood(food.id)
        }
        binding.recyclerFoods.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFoods.adapter = localAdapter

        remoteAdapter = RemoteFoodsAdapter { item ->
            ensureDefaultQuantity()
            viewModel.importFromRemoteItem(item)
        }
        binding.recyclerRemoteFoods.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRemoteFoods.adapter = remoteAdapter
    }

    private fun setupSpinner() {
        val mealTypeItems = MealType.values().map { mealTypeToLabel(it) }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mealTypeItems
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMealType.adapter = spinnerAdapter
    }

    private fun setupListeners() {
        binding.editSearchFood.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            viewModel.onSearchQueryChanged(query)

            if (query.isBlank()) {
                hideRemoteResults()
                binding.textLocalResultsHint.visibility = View.GONE
            }
        }

        binding.buttonToggleRemote.setOnClickListener {
            setRemoteSectionExpanded(!isRemoteSectionExpanded)
        }

        binding.buttonSearchRemote.setOnClickListener {
            val query = binding.editSearchFood.text?.toString().orEmpty().trim()
            if (query.isBlank()) {
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Сначала введите название продукта в поле поиска сверху"
                return@setOnClickListener
            }

            setRemoteSectionExpanded(true)
            hideRemoteResults()
            viewModel.searchRemoteByName(query)
        }

        binding.buttonShowMore.setOnClickListener {
            viewModel.loadMoreRemoteFoods()
        }

        binding.buttonToggleBarcode.setOnClickListener {
            setBarcodeSectionExpanded(!isBarcodeSectionExpanded)
        }

        binding.buttonImport.setOnClickListener {
            ensureDefaultQuantity()
            viewModel.importByBarcode(binding.editBarcode.text?.toString().orEmpty())
        }

        binding.buttonSave.setOnClickListener {
            val qty = binding.editQuantity.text?.toString()
                ?.trim()
                ?.replace(',', '.')
                ?.toDoubleOrNull() ?: 0.0

            val selectedLabel = binding.spinnerMealType.selectedItem?.toString().orEmpty()
            val mealType = labelToMealType(selectedLabel)

            val note = binding.editNote.text?.toString().orEmpty()

            viewModel.saveMeal(
                quantityInGrams = qty,
                mealType = mealType,
                note = note
            )
        }
    }

    private fun observeViewModel() {
        viewModel.foods.observe(viewLifecycleOwner) { foods ->
            localAdapter.submit(foods)

            val hasQuery = binding.editSearchFood.text?.toString()?.trim()?.isNotBlank() == true
            binding.textLocalResultsHint.isVisible = hasQuery && foods.isEmpty()
        }

        viewModel.remoteFoods.observe(viewLifecycleOwner) { items ->
            remoteAdapter.submit(items)

            val hasItems = items.isNotEmpty()
            binding.recyclerRemoteFoods.visibility = if (hasItems) View.VISIBLE else View.GONE

            if (hasItems) {
                setRemoteSectionExpanded(true)
            } else if (!binding.textRemoteSearchStatus.isVisible) {
                binding.buttonShowMore.visibility = View.GONE
            }
        }

        viewModel.remoteSearchStatus.observe(viewLifecycleOwner) { status ->
            binding.textRemoteSearchStatus.visibility =
                if (status.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.textRemoteSearchStatus.text = status.orEmpty()

            if (!status.isNullOrBlank()) {
                setRemoteSectionExpanded(true)
            }
        }

        viewModel.canLoadMoreRemoteFoods.observe(viewLifecycleOwner) { canLoadMore ->
            val hasItems = !viewModel.remoteFoods.value.isNullOrEmpty()
            val searching = viewModel.isRemoteSearching.value == true
            binding.buttonShowMore.visibility =
                if (canLoadMore == true && hasItems && !searching) View.VISIBLE else View.GONE
        }

        viewModel.selectedFoodName.observe(viewLifecycleOwner) { name ->
            val hasSelection = !name.isNullOrBlank() && name != "Продукт ещё не выбран"

            binding.cardSelectedFood.visibility = if (hasSelection) View.VISIBLE else View.GONE
            binding.textSelectedFood.text = if (hasSelection) name else ""

            if (hasSelection) {
                ensureDefaultQuantity()
                focusQuantityField()
                clearBarcodeField()
            }
        }

        viewModel.selectedFoodNutrition.observe(viewLifecycleOwner) { nutrition ->
            binding.textSelectedFoodNutrition.visibility =
                if (nutrition.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.textSelectedFoodNutrition.text = nutrition.orEmpty()
        }

        viewModel.selectedFoodImageUrl.observe(viewLifecycleOwner) { imageRef ->
            val normalized = imageRef?.trim().orEmpty()

            if (normalized.isBlank()) {
                binding.imageSelectedFood.visibility = View.GONE
                binding.imageSelectedFood.setImageDrawable(null)
                return@observe
            }

            binding.imageSelectedFood.visibility = View.VISIBLE
            bindImageRef(binding.imageSelectedFood, normalized)
        }

        viewModel.isImporting.observe(viewLifecycleOwner) { importing ->
            binding.progressImport.visibility = if (importing) View.VISIBLE else View.GONE
            binding.buttonImport.isEnabled = !importing
        }

        viewModel.isRemoteSearching.observe(viewLifecycleOwner) { searching ->
            binding.progressRemoteSearch.visibility = if (searching) View.VISIBLE else View.GONE
            binding.buttonSearchRemote.isEnabled = !searching
            binding.buttonShowMore.isEnabled = !searching

            if (searching) {
                binding.buttonShowMore.visibility = View.GONE
                setRemoteSectionExpanded(true)
            } else {
                val hasItems = !viewModel.remoteFoods.value.isNullOrEmpty()
                val canLoadMore = viewModel.canLoadMoreRemoteFoods.value == true
                binding.buttonShowMore.visibility =
                    if (canLoadMore && hasItems) View.VISIBLE else View.GONE
            }
        }

        viewModel.isSaving.observe(viewLifecycleOwner) { saving ->
            binding.progressSaving.visibility = if (saving) View.VISIBLE else View.GONE
            binding.buttonSave.isEnabled = !saving
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            binding.textError.visibility = if (msg.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.textError.text = msg.orEmpty()
        }

        viewModel.saved.observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                Toast.makeText(requireContext(), "Приём пищи сохранён", Toast.LENGTH_SHORT).show()
                clearFormBeforeClose()
                viewModel.onSaveHandled()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun applyRoundedImage(imageView: ImageView, radiusDp: Float) {
        val radiusPx = radiusDp * resources.displayMetrics.density
        imageView.clipToOutline = true
        imageView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.parseColor("#F4D4FB"))
        }
    }

    private fun bindImageRef(imageView: ImageView, imageRef: String?) {
        val normalized = imageRef?.trim().orEmpty()

        if (normalized.isBlank()) {
            imageView.setImageDrawable(ColorDrawable(Color.parseColor("#F4D4FB")))
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
                imageView.setImageDrawable(ColorDrawable(Color.parseColor("#F4D4FB")))
            }
        } else {
            imageView.load(normalized) {
                crossfade(true)
                placeholder(ColorDrawable(Color.parseColor("#F4D4FB")))
                error(ColorDrawable(Color.parseColor("#F4D4FB")))
            }
        }
    }

    private fun ensureDefaultQuantity() {
        if (binding.editQuantity.text?.toString().isNullOrBlank()) {
            binding.editQuantity.setText("100")
        }
    }

    private fun focusQuantityField() {
        binding.editQuantity.requestFocus()
        binding.editQuantity.setSelection(binding.editQuantity.text?.length ?: 0)
    }

    private fun clearBarcodeField() {
        binding.editBarcode.text?.clear()
    }

    private fun hideRemoteResults() {
        binding.recyclerRemoteFoods.visibility = View.GONE
        binding.textRemoteSearchStatus.visibility = View.GONE
        binding.buttonShowMore.visibility = View.GONE
    }

    private fun clearFormBeforeClose() {
        binding.editBarcode.text?.clear()
        binding.editNote.text?.clear()
        binding.textError.text = ""
        binding.textError.visibility = View.GONE
        binding.textRemoteSearchStatus.text = ""
        binding.textRemoteSearchStatus.visibility = View.GONE
        binding.recyclerRemoteFoods.visibility = View.GONE
        binding.buttonShowMore.visibility = View.GONE
    }

    private fun setRemoteSectionExpanded(expanded: Boolean) {
        isRemoteSectionExpanded = expanded
        binding.layoutRemoteSection.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.buttonToggleRemote.text = if (expanded) {
            "Скрыть общую базу продуктов"
        } else {
            "Найти в общей базе продуктов"
        }
    }

    private fun setBarcodeSectionExpanded(expanded: Boolean) {
        isBarcodeSectionExpanded = expanded
        binding.layoutBarcodeSection.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.buttonToggleBarcode.text = if (expanded) {
            "Скрыть поиск по штрихкоду"
        } else {
            "Добавить по штрихкоду"
        }
    }

    private fun mealTypeToLabel(type: MealType): String {
        return when (type) {
            MealType.BREAKFAST -> "Завтрак"
            MealType.LUNCH -> "Обед"
            MealType.DINNER -> "Ужин"
            MealType.SNACK -> "Перекус"
        }
    }

    private fun labelToMealType(label: String): MealType {
        return when (label) {
            "Завтрак" -> MealType.BREAKFAST
            "Обед" -> MealType.LUNCH
            "Ужин" -> MealType.DINNER
            "Перекус" -> MealType.SNACK
            else -> MealType.BREAKFAST
        }
    }

    private fun provideFactory(): AddMealViewModelFactory {
        val db = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(foodDao = db.foodDao())
        val mealRepository = MealRepositoryImpl(
            mealDao = db.mealDao(),
            foodRepository = foodRepository
        )

        val addMealUseCase = AddMealUseCase(mealRepository)

        val api = OpenFoodFactsApiFactory.create()
        val importRepo = FoodImportRepositoryImpl(
            api = api,
            foodDao = db.foodDao()
        )

        val importByBarcodeUseCase = ImportFoodByBarcodeUseCase(importRepo)
        val importFromSearchItemUseCase = ImportFoodFromSearchItemUseCase(importRepo)
        val searchUseCase = SearchFoodsByNameUseCase(importRepo)

        return AddMealViewModelFactory(
            foodRepository = foodRepository,
            addMealUseCase = addMealUseCase,
            importFoodByBarcodeUseCase = importByBarcodeUseCase,
            importFoodFromSearchItemUseCase = importFromSearchItemUseCase,
            searchFoodsByNameUseCase = searchUseCase
        )
    }

    private class FoodsAdapter(
        private val imageBinder: (ImageView, String?) -> Unit,
        private val onClick: (Food) -> Unit
    ) : RecyclerView.Adapter<FoodsAdapter.VH>() {

        private val items = mutableListOf<Food>()

        fun submit(newItems: List<Food>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val context = parent.context

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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

            val titleView = TextView(context).apply {
                textSize = 15f
                setTextColor(Color.parseColor("#2F2433"))
            }

            val subtitleView = TextView(context).apply {
                textSize = 13f
                setTextColor(Color.parseColor("#6B5B73"))
            }

            textContainer.addView(titleView)
            textContainer.addView(subtitleView)

            root.addView(imageView)
            root.addView(textContainer)

            return VH(root, imageView, titleView, subtitleView, imageBinder, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class VH(
            itemView: View,
            private val imageView: ImageView,
            private val titleView: TextView,
            private val subtitleView: TextView,
            private val imageBinder: (ImageView, String?) -> Unit,
            private val onClick: (Food) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private var current: Food? = null

            init {
                itemView.setOnClickListener {
                    current?.let(onClick)
                }
            }

            fun bind(item: Food) {
                current = item
                titleView.text = item.name
                subtitleView.text =
                    "Ккал: ${item.caloriesPer100g} • Б: ${item.proteinPer100g} • Ж: ${item.fatPer100g} • У: ${item.carbsPer100g}"
                imageBinder(imageView, item.imageUrl)
            }
        }

        companion object {
            private fun dp(context: android.content.Context, value: Int): Int {
                return (value * context.resources.displayMetrics.density).toInt()
            }
        }
    }

    private class RemoteFoodsAdapter(
        private val onImport: (FoodSearchItem) -> Unit
    ) : RecyclerView.Adapter<RemoteFoodsAdapter.VH>() {

        private val items = mutableListOf<FoodSearchItem>()

        fun submit(newItems: List<FoodSearchItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val context = parent.context

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 18).toFloat()
                    setColor(Color.parseColor("#FFFBEA"))
                }
            }

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 72), dp(context, 72))
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 18).toFloat()
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
                    marginEnd = dp(context, 12)
                }
            }

            val titleView = TextView(context).apply {
                textSize = 15f
                setTextColor(Color.parseColor("#2F2433"))
            }

            val subtitleView = TextView(context).apply {
                textSize = 13f
                setTextColor(Color.parseColor("#6B5B73"))
            }

            val btn = Button(context).apply {
                text = "Добавить"
                setTextColor(Color.WHITE)
            }

            textContainer.addView(titleView)
            textContainer.addView(subtitleView)

            root.addView(imageView)
            root.addView(textContainer)
            root.addView(btn)

            return VH(root, imageView, titleView, subtitleView, btn, onImport)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class VH(
            itemView: View,
            private val imageView: ImageView,
            private val titleView: TextView,
            private val subtitleView: TextView,
            private val btn: Button,
            private val onImport: (FoodSearchItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private var current: FoodSearchItem? = null

            init {
                btn.setOnClickListener {
                    current?.let(onImport)
                }
            }

            fun bind(item: FoodSearchItem) {
                current = item

                val brand = item.brand?.takeIf { it.isNotBlank() } ?: "бренд не указан"
                val kcal = item.caloriesPer100g ?: 0.0
                val protein = item.proteinPer100g ?: 0.0
                val fat = item.fatPer100g ?: 0.0
                val carbs = item.carbsPer100g ?: 0.0

                titleView.text = item.name
                subtitleView.text = "$brand\nКкал: $kcal • Б: $protein • Ж: $fat • У: $carbs"

                val imageUrl = item.imageUrl?.takeIf { it.isNotBlank() }
                if (imageUrl != null) {
                    imageView.load(imageUrl) {
                        crossfade(true)
                        placeholder(ColorDrawable(Color.parseColor("#F4D4FB")))
                        error(ColorDrawable(Color.parseColor("#F4D4FB")))
                    }
                } else {
                    imageView.setImageDrawable(ColorDrawable(Color.parseColor("#F4D4FB")))
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