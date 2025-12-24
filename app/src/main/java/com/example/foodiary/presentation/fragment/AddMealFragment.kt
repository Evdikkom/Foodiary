package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.remote.off.OpenFoodFactsApiFactory
import com.example.foodiary.domain.repository.FoodImportRepositoryImpl
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.domain.model.Food
import com.example.foodiary.domain.model.FoodSearchItem
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.domain.usecase.ImportFoodByBarcodeUseCase
import com.example.foodiary.domain.usecase.SearchFoodsByNameUseCase
import com.example.foodiary.presentation.viewmodel.AddMealViewModel
import com.example.foodiary.presentation.viewmodel.AddMealViewModelFactory

class AddMealFragment : Fragment(R.layout.fragment_add_meal) {

    private val viewModel: AddMealViewModel by viewModels { provideFactory() }

    private lateinit var localAdapter: FoodsAdapter
    private lateinit var remoteAdapter: RemoteFoodsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editSearchFood = view.findViewById<EditText>(R.id.editSearchFood)
        val recyclerFoods = view.findViewById<RecyclerView>(R.id.recyclerFoods)

        val textSelectedFood = view.findViewById<TextView>(R.id.textSelectedFood)
        val editQuantity = view.findViewById<EditText>(R.id.editQuantity)
        val spinnerMealType = view.findViewById<Spinner>(R.id.spinnerMealType)
        val editNote = view.findViewById<EditText>(R.id.editNote)

        val buttonSave = view.findViewById<Button>(R.id.buttonSave)
        val progressSaving = view.findViewById<ProgressBar>(R.id.progressSaving)

        val editBarcode = view.findViewById<EditText>(R.id.editBarcode)
        val buttonImport = view.findViewById<Button>(R.id.buttonImport)
        val progressImport = view.findViewById<ProgressBar>(R.id.progressImport)

        val textError = view.findViewById<TextView>(R.id.textError)

        // ---- НОВОЕ: поиск в OpenFoodFacts по словам ----
        val buttonSearchRemote = view.findViewById<Button>(R.id.buttonSearchRemote)
        val progressRemoteSearch = view.findViewById<ProgressBar>(R.id.progressRemoteSearch)
        val recyclerRemoteFoods = view.findViewById<RecyclerView>(R.id.recyclerRemoteFoods)

        // RecyclerView: локальные продукты
        localAdapter = FoodsAdapter { food -> viewModel.selectFood(food.id) }
        recyclerFoods.layoutManager = LinearLayoutManager(requireContext())
        recyclerFoods.adapter = localAdapter

        // RecyclerView: remote продукты (OpenFoodFacts)
        remoteAdapter = RemoteFoodsAdapter { item ->
            viewModel.importFromRemoteItem(item)
        }
        recyclerRemoteFoods.layoutManager = LinearLayoutManager(requireContext())
        recyclerRemoteFoods.adapter = remoteAdapter

        // Spinner MealType (MealType — тип приёма пищи)
        val mealTypeItems = MealType.values().map { it.name }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mealTypeItems)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMealType.adapter = spinnerAdapter

        // Локальный поиск (Room)
        editSearchFood.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        // НОВОЕ: кнопка поиска в OpenFoodFacts по названию
        buttonSearchRemote.setOnClickListener {
            viewModel.searchRemoteByName(editSearchFood.text?.toString().orEmpty())
        }

        // Импорт по штрихкоду
        buttonImport.setOnClickListener {
            viewModel.importByBarcode(editBarcode.text?.toString().orEmpty())
        }

        // Сохранение Meal
        buttonSave.setOnClickListener {
            val qty = editQuantity.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val mealType = spinnerMealType.selectedItem?.toString()
                ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                ?: MealType.BREAKFAST
            val note = editNote.text?.toString().orEmpty()

            viewModel.saveMeal(quantityInGrams = qty, mealType = mealType, note = note)
        }

        // OBSERVE: локальный список
        viewModel.foods.observe(viewLifecycleOwner) { foods ->
            localAdapter.submit(foods)
        }

        // OBSERVE: remote список
        viewModel.remoteFoods.observe(viewLifecycleOwner) { items ->
            remoteAdapter.submit(items)
        }

        // OBSERVE: выбранный продукт
        viewModel.selectedFoodName.observe(viewLifecycleOwner) { name ->
            textSelectedFood.text = "Выбран: $name"
        }

        // OBSERVE: импорт (barcode/remote)
        viewModel.isImporting.observe(viewLifecycleOwner) { importing ->
            progressImport.visibility = if (importing) View.VISIBLE else View.GONE
            buttonImport.isEnabled = !importing
        }

        // OBSERVE: поиск remote
        viewModel.isRemoteSearching.observe(viewLifecycleOwner) { searching ->
            progressRemoteSearch.visibility = if (searching) View.VISIBLE else View.GONE
            buttonSearchRemote.isEnabled = !searching
        }

        // OBSERVE: сохранение
        viewModel.isSaving.observe(viewLifecycleOwner) { saving ->
            progressSaving.visibility = if (saving) View.VISIBLE else View.GONE
            buttonSave.isEnabled = !saving
        }

        // OBSERVE: ошибки
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            textError.visibility = if (msg.isNullOrBlank()) View.GONE else View.VISIBLE
            textError.text = msg.orEmpty()
        }

        // OBSERVE: успех сохранения
        viewModel.saved.observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                Toast.makeText(requireContext(), "Приём пищи сохранён", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun provideFactory(): AddMealViewModelFactory {
        val db = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(foodDao = db.foodDao())
        val mealRepository = MealRepositoryImpl(mealDao = db.mealDao(), foodRepository = foodRepository)

        val addMealUseCase = AddMealUseCase(mealRepository)

        val api = OpenFoodFactsApiFactory.create()
        val importRepo = FoodImportRepositoryImpl(api = api, foodDao = db.foodDao())

        val importUseCase = ImportFoodByBarcodeUseCase(importRepo)
        val searchUseCase = SearchFoodsByNameUseCase(importRepo)

        return AddMealViewModelFactory(
            foodRepository = foodRepository,
            addMealUseCase = addMealUseCase,
            importFoodByBarcodeUseCase = importUseCase,
            searchFoodsByNameUseCase = searchUseCase
        )
    }

    // ---------------- Adapter: локальные продукты ----------------
    private class FoodsAdapter(
        private val onClick: (Food) -> Unit
    ) : RecyclerView.Adapter<FoodsAdapter.VH>() {

        private val items = mutableListOf<Food>()

        fun submit(newItems: List<Food>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                setPadding(24, 18, 24, 18)
                textSize = 16f
            }
            return VH(tv, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        private class VH(
            private val tv: TextView,
            private val onClick: (Food) -> Unit
        ) : RecyclerView.ViewHolder(tv) {
            private var current: Food? = null
            init { tv.setOnClickListener { current?.let(onClick) } }
            fun bind(item: Food) {
                current = item
                tv.text = "${item.name} • ${item.caloriesPer100g} ккал/100г"
            }
        }
    }

    // ---------------- Adapter: remote результаты OpenFoodFacts ----------------
    private class RemoteFoodsAdapter(
        private val onImport: (FoodSearchItem) -> Unit
    ) : RecyclerView.Adapter<RemoteFoodsAdapter.VH>() {

        private val items = mutableListOf<FoodSearchItem>()

        fun submit(newItems: List<FoodSearchItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 18, 24, 18)
            }
            val tv = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 14f
            }
            val btn = Button(parent.context).apply {
                text = "Импорт"
            }
            root.addView(tv)
            root.addView(btn)
            return VH(root, tv, btn, onImport)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        private class VH(
            itemView: View,
            private val tv: TextView,
            private val btn: Button,
            private val onImport: (FoodSearchItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private var current: FoodSearchItem? = null

            init {
                btn.setOnClickListener { current?.let(onImport) }
            }

            fun bind(item: FoodSearchItem) {
                current = item
                val kcal = item.caloriesPer100g?.let { "${it} ккал/100г" } ?: "ккал ?"
                val brand = item.brand?.takeIf { it.isNotBlank() } ?: "бренд ?"
                tv.text = "${item.name} • $brand • $kcal\nbarcode: ${item.barcode}"
            }
        }
    }
}
