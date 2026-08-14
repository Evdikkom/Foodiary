package com.example.foodiary.presentation.dialog

import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.foodiary.R
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.fragment.CreateCustomFoodFragment
import com.example.foodiary.presentation.fragment.CreateRecipeFragment

class CreateChoiceDialogFragment : DialogFragment(R.layout.dialog_create_choice) {

    companion object {
        private const val ARG_MEAL_TYPE = "arg_meal_type"

        fun newInstance(mealType: MealType): CreateChoiceDialogFragment {
            return CreateChoiceDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEAL_TYPE, mealType.name)
                }
            }
        }
    }

    private val mealType: MealType by lazy {
        arguments?.getString(ARG_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.BREAKFAST
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                attributes = attributes.apply { blurBehindRadius = 18 }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.buttonClose).setOnClickListener {
            dismissAllowingStateLoss()
        }

        view.findViewById<View>(R.id.optionCustomFood).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    CreateCustomFoodFragment.newInstance(mealType)
                )
                .addToBackStack(null)
                .commit()
            dismissAllowingStateLoss()
        }

        view.findViewById<View>(R.id.optionRecipe).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    CreateRecipeFragment.newInstance(mealType)
                )
                .addToBackStack(null)
                .commit()
            dismissAllowingStateLoss()
        }

        view.findViewById<TextView>(R.id.textTitle).text = "Что вы хотите создать?"
    }
}
