package com.example.foodiary.presentation.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.repository.FoodRepositoryImpl
import com.example.foodiary.data.repository.MealRepositoryImpl
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.domain.usecase.DeleteMealUseCase
import com.example.foodiary.domain.usecase.GetMealsForPeriodUseCase
import com.example.foodiary.presentation.adapter.MealFoodRowUi
import com.example.foodiary.presentation.util.popBackStackSafely
import com.example.foodiary.presentation.util.replaceFragmentSafely
import com.example.foodiary.presentation.util.setDebouncedClickListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MealDetailsFragment : Fragment(R.layout.fragment_meal_details) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"
        private const val ARG_DAY_START = "arg_day_start"

        fun newInstance(
            mealType: MealType,
            dayStartTimestamp: Long = normalizeDayStart(System.currentTimeMillis())
        ): MealDetailsFragment {
            return MealDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                    putLong(ARG_DAY_START, dayStartTimestamp)
                }
            }
        }

        private fun normalizeDayStart(timestamp: Long): Long {
            return Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    private val dayStartTimestamp: Long by lazy {
        if (arguments?.containsKey(ARG_DAY_START) == true) {
            arguments?.getLong(ARG_DAY_START) ?: normalizeDayStart(System.currentTimeMillis())
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

    private val getMealsForPeriodUseCase by lazy {
        GetMealsForPeriodUseCase(mealRepository)
    }

    private val deleteMealUseCase by lazy {
        DeleteMealUseCase(mealRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStaticUi(view)
        setupActions(view)
        loadMealDetails(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadMealDetails(it) }
    }

    private fun setupStaticUi(root: View) {
        root.findViewById<TextView>(R.id.textMealTitle).text = mealTypeLabel(mealType)

        val subtitleFormat = SimpleDateFormat("d MMMM", Locale("ru"))
        root.findViewById<TextView>(R.id.textMealSubtitle).text =
            if (isTodaySelected()) {
                "Сегодня, ${subtitleFormat.format(Date(dayStartTimestamp))}"
            } else {
                subtitleFormat.format(Date(dayStartTimestamp))
            }
    }

    private fun setupActions(root: View) {
        root.findViewById<View>(R.id.buttonBack).setDebouncedClickListener {
            popBackStackSafely()
        }

        root.findViewById<Button>(R.id.buttonAddFood).setDebouncedClickListener {
            replaceFragmentSafely(AddMealFragment.newInstance(mealType, dayStartTimestamp))
        }
    }

    private fun confirmDelete(item: MealFoodRowUi) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить продукт?")
            .setMessage("${item.foodName}, ${item.gramsText}")
            .setPositiveButton("Удалить") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    deleteMealUseCase(item.id)
                    parentFragmentManager.setFragmentResult(
                        DailyNutritionFragment.REQUEST_MEALS_CHANGED,
                        Bundle.EMPTY
                    )
                    view?.let { loadMealDetails(it) }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openMealEditor(item: MealFoodRowUi) {
        replaceFragmentSafely(
            ProductConfigFragment.newEditMealInstance(
                mealType = mealType,
                foodId = item.foodId,
                mealId = item.id,
                initialQuantityInGrams = item.quantityInGrams
            )
        )
    }

    private fun loadMealDetails(root: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val meals = getMealsForPeriodUseCase(
                startTimestamp = dayStartTimestamp,
                endTimestamp = endOfDay()
            ).filter { it.mealType == mealType }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            var totalCalories = 0.0
            var totalProtein = 0.0
            var totalFat = 0.0
            var totalCarbs = 0.0
            var totalWeight = 0.0

            val rows = meals.map { meal ->
                val food = foodRepository.getFoodById(meal.foodId)

                totalWeight += meal.quantityInGrams
                totalCalories += food.caloriesPer100g * meal.quantityInGrams / 100.0
                totalProtein += food.proteinPer100g * meal.quantityInGrams / 100.0
                totalFat += food.fatPer100g * meal.quantityInGrams / 100.0
                totalCarbs += food.carbsPer100g * meal.quantityInGrams / 100.0

                MealFoodRowUi(
                    id = meal.id,
                    foodId = meal.foodId,
                    quantityInGrams = meal.quantityInGrams,
                    timeText = timeFormat.format(Date(meal.timestamp)),
                    foodName = food.name,
                    gramsText = formatWeight(meal.quantityInGrams),
                    note = meal.note,
                    imageUrl = food.imageUrl
                )
            }

            renderMealRows(root, rows)

            root.findViewById<TextView>(R.id.textMealSummary).text =
                "${buildProductCountLabel(rows.size)}, ${formatKcal(totalCalories)}"

            root.findViewById<TextView>(R.id.textMealWeight).text =
                "Общий вес: ${formatWeight(totalWeight)}"

            root.findViewById<TextView>(R.id.textMealProtein).text = formatWeight(totalProtein)
            root.findViewById<TextView>(R.id.textMealFat).text = formatWeight(totalFat)
            root.findViewById<TextView>(R.id.textMealCarbs).text = formatWeight(totalCarbs)

            root.findViewById<View>(R.id.layoutEmptyState).isVisible = rows.isEmpty()
        }
    }

    private fun renderMealRows(root: View, rows: List<MealFoodRowUi>) {
        val container = root.findViewById<LinearLayout>(R.id.layoutMealFoods)
        container.removeAllViews()
        container.isVisible = rows.isNotEmpty()

        rows.forEach { row ->
            val itemView = layoutInflater.inflate(R.layout.item_meal_food_entry, container, false)
            bindMealRow(itemView, row)
            container.addView(itemView)
        }
    }

    private fun bindMealRow(itemView: View, item: MealFoodRowUi) {
        itemView.findViewById<TextView>(R.id.textFoodTime).text = item.timeText
        itemView.findViewById<TextView>(R.id.textFoodName).text = item.foodName
        itemView.findViewById<TextView>(R.id.textFoodAmount).text = item.gramsText
        itemView.findViewById<TextView>(R.id.textFoodAction).text = "\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c"
        bindImage(itemView.findViewById(R.id.imageFood), item.imageUrl)

        itemView.setOnClickListener {
            openMealEditor(item)
        }
        itemView.setOnLongClickListener {
            confirmDelete(item)
            true
        }
    }

    private fun bindImage(image: ImageView, ref: String?) {
        val normalized = ref?.trim().orEmpty()
        if (normalized.startsWith("drawable://")) {
            val resId = resources.getIdentifier(
                normalized.removePrefix("drawable://"),
                "drawable",
                requireContext().packageName
            )
            if (resId != 0) {
                image.setImageResource(resId)
            } else {
                image.setImageResource(R.drawable.ic_custom_food_placeholder)
            }
        } else if (normalized.isBlank()) {
            image.setImageResource(R.drawable.ic_custom_food_placeholder)
        } else {
            image.load(normalized)
        }
    }

    private fun endOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStartTimestamp
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    private fun isTodaySelected(): Boolean {
        return dayStartTimestamp == normalizeDayStart(System.currentTimeMillis())
    }

    private fun mealTypeLabel(type: MealType): String {
        return when (type) {
            MealType.AFTERNOON_SNACK -> "Полдник"
            MealType.LATE_DINNER -> "Поздний ужин"
            MealType.BREAKFAST -> "Завтрак"
            MealType.LUNCH -> "Обед"
            MealType.DINNER -> "Ужин"
            MealType.SNACK -> "Перекус"
        }
    }

    private fun buildProductCountLabel(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "$count продукт"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count продукта"
            else -> "$count продуктов"
        }
    }

    private fun formatKcal(value: Double): String = "${value.toInt()} ккал"

    private fun formatWeight(value: Double): String {
        val rounded = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
        return "$rounded г"
    }
}
