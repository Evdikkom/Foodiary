package com.example.foodiary.presentation.dialog

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.foodiary.R
import com.example.foodiary.data.local.preferences.MealSchedulePreferences
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.fragment.AddMealFragment
import com.example.foodiary.presentation.fragment.ProductConfigFragment
import com.example.foodiary.presentation.util.displayName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MealTypePickerDialogFragment : DialogFragment(R.layout.dialog_meal_type_picker) {

    companion object {
        private const val ARG_TARGET_DAY_START = "arg_target_day_start"
        private const val ARG_FOOD_ID = "arg_food_id"
        private const val ARG_SUGGESTED_MEAL_TYPE = "arg_suggested_meal_type"
        private const val ARG_INITIAL_QUANTITY = "arg_initial_quantity"

        fun newInstance(targetDayStart: Long): MealTypePickerDialogFragment {
            return MealTypePickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TARGET_DAY_START, targetDayStart)
                }
            }
        }

        fun newProductInstance(
            foodId: String,
            targetDayStart: Long,
            suggestedMealType: MealType,
            initialQuantityInGrams: Double? = null
        ): MealTypePickerDialogFragment {
            return MealTypePickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TARGET_DAY_START, targetDayStart)
                    putString(ARG_FOOD_ID, foodId)
                    putString(ARG_SUGGESTED_MEAL_TYPE, suggestedMealType.name)
                    initialQuantityInGrams?.let { putDouble(ARG_INITIAL_QUANTITY, it) }
                }
            }
        }
    }

    private val targetDayStart: Long by lazy {
        arguments?.getLong(ARG_TARGET_DAY_START) ?: System.currentTimeMillis()
    }

    private val targetFoodId: String? by lazy {
        arguments?.getString(ARG_FOOD_ID)?.takeIf { it.isNotBlank() }
    }

    private val suggestedMealType: MealType? by lazy {
        arguments?.getString(ARG_SUGGESTED_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
    }

    private val initialQuantityInGrams: Double? by lazy {
        if (arguments?.containsKey(ARG_INITIAL_QUANTITY) == true) {
            arguments?.getDouble(ARG_INITIAL_QUANTITY)
        } else {
            null
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                attributes = attributes.apply { blurBehindRadius = 22 }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.buttonClose).setOnClickListener {
            dismissAllowingStateLoss()
        }
        view.findViewById<TextView>(R.id.textSubtitle).text = if (targetFoodId == null) {
            "Новый продукт будет добавлен в ${buildDayLabel()}"
        } else {
            "Выберите прием пищи, затем проверьте порцию и сохраните продукт"
        }

        val optionsContainer = view.findViewById<LinearLayout>(R.id.layoutMealTypeOptions)
        optionsContainer.removeAllViews()

        val enabledMealTypes = MealSchedulePreferences(requireContext()).getEnabledMealTypes()
            .ifEmpty {
                listOf(
                    MealType.BREAKFAST,
                    MealType.LUNCH,
                    MealType.DINNER,
                    MealType.SNACK
                )
            }
        val mealTypes = (listOfNotNull(suggestedMealType).filter { it in enabledMealTypes } + enabledMealTypes)
            .distinct()

        mealTypes.forEach { mealType ->
            val row = layoutInflater.inflate(R.layout.item_settings_nav_row, optionsContainer, false)
            row.findViewById<ImageView>(R.id.imageIcon).setImageResource(android.R.drawable.ic_menu_add)
            row.findViewById<TextView>(R.id.textTitle).text = mealType.displayName()
            row.findViewById<TextView>(R.id.textSubtitle).apply {
                text = if (targetFoodId == null) {
                    "Открыть добавление в ${mealType.displayName().lowercase(Locale("ru"))}"
                } else {
                    "Добавить продукт в ${mealType.displayName().lowercase(Locale("ru"))}"
                }
                visibility = View.VISIBLE
            }
            row.setOnClickListener {
                openMealType(mealType)
            }
            optionsContainer.addView(row)
        }
    }

    private fun openMealType(mealType: MealType) {
        val foodId = targetFoodId
        val targetFragment = if (foodId == null) {
            AddMealFragment.newInstance(mealType, targetDayStart)
        } else {
            ProductConfigFragment.newInstance(
                mealType = mealType,
                foodId = foodId,
                initialQuantityInGrams = initialQuantityInGrams,
                targetDayStartTimestamp = targetDayStart
            )
        }
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                targetFragment
            )
            .addToBackStack(null)
            .commit()
        dismissAllowingStateLoss()
    }

    private fun buildDayLabel(): String {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return if (targetDayStart == todayStart) {
            "сегодня"
        } else {
            SimpleDateFormat("d MMMM", Locale("ru")).format(targetDayStart)
        }
    }
}
