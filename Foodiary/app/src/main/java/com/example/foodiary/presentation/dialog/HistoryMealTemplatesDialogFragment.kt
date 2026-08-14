package com.example.foodiary.presentation.dialog

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.foodiary.R
import com.example.foodiary.domain.model.HistoryMealTemplate
import java.util.Locale

class HistoryMealTemplatesDialogFragment :
    DialogFragment(R.layout.dialog_history_meal_templates) {

    companion object {
        const val REQUEST_KEY = "history_meal_templates_request"
        const val RESULT_TEMPLATE_ID = "result_template_id"

        private const val ARG_TEMPLATES = "arg_templates"
        private const val ARG_MEAL_LABEL = "arg_meal_label"
        private const val ARG_DAY_LABEL = "arg_day_label"

        fun newInstance(
            templates: ArrayList<HistoryMealTemplate>,
            mealLabel: String,
            dayLabel: String,
        ): HistoryMealTemplatesDialogFragment {
            return HistoryMealTemplatesDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_TEMPLATES, templates)
                    putString(ARG_MEAL_LABEL, mealLabel)
                    putString(ARG_DAY_LABEL, dayLabel)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val templates: List<HistoryMealTemplate> by lazy {
        arguments?.getSerializable(ARG_TEMPLATES) as? ArrayList<HistoryMealTemplate> ?: arrayListOf()
    }

    private val mealLabel: String by lazy {
        arguments?.getString(ARG_MEAL_LABEL).orEmpty()
    }

    private val dayLabel: String by lazy {
        arguments?.getString(ARG_DAY_LABEL).orEmpty()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
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

        view.findViewById<TextView>(R.id.textSubtitle).text = buildSubtitle()
        view.findViewById<TextView>(R.id.textTemplatesCount).text = buildTemplatesCountLabel()

        val container = view.findViewById<LinearLayout>(R.id.layoutTemplatesContainer)
        container.removeAllViews()
        templates.forEachIndexed { index, template ->
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_history_meal_template, container, false)

            bindTemplateCard(card, template, index)

            if (index > 0) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 12.dp(view)
            }
            container.addView(card)
        }
    }

    private fun buildSubtitle(): String {
        return if (templates.size == 1) {
            "Нашли привычный набор для $mealLabel. Нажмите кнопку на карточке, и он сразу добавится в $dayLabel."
        } else {
            "Нашли несколько привычных наборов для $mealLabel. Выберите нужный вариант, и он сразу добавится в $dayLabel."
        }
    }

    private fun buildTemplatesCountLabel(): String {
        return when (templates.size) {
            1 -> "1 готовый вариант"
            in 2..4 -> "${templates.size} готовых варианта"
            else -> "${templates.size} готовых вариантов"
        }
    }

    private fun bindTemplateCard(card: View, template: HistoryMealTemplate, index: Int) {
        card.findViewById<TextView>(R.id.textLeadBadge).apply {
            isVisible = index == 0
            text = if (templates.size == 1) "Ваш привычный вариант" else "Самый частый"
        }
        card.findViewById<TextView>(R.id.textTemplateTitle).text = template.title
        card.findViewById<TextView>(R.id.textTemplateOccurrences).text =
            "Повторялся ${buildOccurrencesLabel(template.occurrencesCount)}"
        card.findViewById<TextView>(R.id.textTemplateProductsCount).text =
            buildProductsCountLabel(template.items.size)
        card.findViewById<TextView>(R.id.textTemplateWeight).text =
            "${formatCompactNumber(template.totalWeightInGrams)} г"
        card.findViewById<TextView>(R.id.textTemplateCalories).text =
            "${formatCompactNumber(template.totalCalories)} ккал"
        card.findViewById<TextView>(R.id.textTemplatePreview).text = buildPreview(template)

        val applyButton = card.findViewById<Button>(R.id.buttonApplyTemplate)
        applyButton.text = "Добавить в $mealLabel"
        applyButton.setOnClickListener {
            submitTemplate(template.id)
        }
    }

    private fun buildOccurrencesLabel(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "$count раз"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count раза"
            else -> "$count раз"
        }
    }

    private fun buildProductsCountLabel(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "$count продукт"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count продукта"
            else -> "$count продуктов"
        }
    }

    private fun buildPreview(template: HistoryMealTemplate): String {
        val visibleItems = template.items.take(3).joinToString("\n") { item ->
            "${item.foodName} ${formatCompactNumber(item.quantityInGrams)} г"
        }

        return if (template.items.size > 3) {
            "$visibleItems\nИ ещё ${template.items.size - 3} ${buildTailLabel(template.items.size - 3)}"
        } else {
            visibleItems
        }
    }

    private fun buildTailLabel(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "продукт"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "продукта"
            else -> "продуктов"
        }
    }

    private fun submitTemplate(templateId: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_TEMPLATE_ID to templateId)
        )
        dismissAllowingStateLoss()
    }

    private fun formatCompactNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun Int.dp(view: View): Int {
        return (this * view.resources.displayMetrics.density).toInt()
    }
}
