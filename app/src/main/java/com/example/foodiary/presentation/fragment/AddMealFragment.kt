package com.example.foodiary.presentation.fragment

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.usecase.AddMealUseCase
import com.example.foodiary.presentation.viewmodel.AddMealViewModel
import com.example.foodiary.presentation.viewmodel.AddMealViewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.presentation.adapter.FoodSearchAdapter


class AddMealFragment : Fragment(R.layout.fragment_add_meal) {

    private val viewModel: AddMealViewModel by viewModels {
        provideFactory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editSearch = view.findViewById<EditText>(R.id.editSearchFood)
        val recyclerFoods = view.findViewById<RecyclerView>(R.id.recyclerFoods)
        val textSelectedFood = view.findViewById<TextView>(R.id.textSelectedFood)

        val editQuantity = view.findViewById<EditText>(R.id.editQuantity)
        val spinnerMealType = view.findViewById<Spinner>(R.id.spinnerMealType)
        val editNote = view.findViewById<EditText>(R.id.editNote)

        val textError = view.findViewById<TextView>(R.id.textError)
        val buttonSave = view.findViewById<Button>(R.id.buttonSave)
        val progress = view.findViewById<ProgressBar>(R.id.progressSaving)

        // Spinner MealType
        val types = MealType.values().map { it.name }
        spinnerMealType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            types
        )

        // Recycler + Adapter
        val adapter = FoodSearchAdapter { food ->
            viewModel.selectFood(food.id)
        }
        recyclerFoods.layoutManager = LinearLayoutManager(requireContext())
        recyclerFoods.adapter = adapter

        // Поиск
        editSearch.addTextChangedListener { editable ->
            viewModel.onSearchQueryChanged(editable?.toString().orEmpty())
        }

        // Save
        buttonSave.setOnClickListener {
            val qty = editQuantity.text.toString().trim().toDoubleOrNull() ?: 0.0
            val typeName = spinnerMealType.selectedItem as String
            val type = MealType.valueOf(typeName)
            val note = editNote.text.toString()

            viewModel.saveMeal(
                quantityInGrams = qty,
                mealType = type,
                note = note
            )
        }

        // Observers
        viewModel.selectedFoodName.observe(viewLifecycleOwner) { name ->
            textSelectedFood.text = name
        }

        viewModel.foods.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        viewModel.isSaving.observe(viewLifecycleOwner) { saving ->
            progress.visibility = if (saving) View.VISIBLE else View.GONE
            buttonSave.isEnabled = !saving
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg.isNullOrBlank()) {
                textError.visibility = View.GONE
            } else {
                textError.visibility = View.VISIBLE
                textError.text = msg
            }
        }

        viewModel.saved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }





    private fun provideFactory(): AddMealViewModelFactory {
        val db = AppDatabase.getInstance(requireContext())

        val foodRepository = FoodRepositoryImpl(db.foodDao())
        val mealRepository = MealRepositoryImpl(db.mealDao(), foodRepository)

        val useCase = AddMealUseCase(mealRepository)

        return AddMealViewModelFactory(
            foodRepository = foodRepository,
            addMealUseCase = useCase
        )
    }
}
