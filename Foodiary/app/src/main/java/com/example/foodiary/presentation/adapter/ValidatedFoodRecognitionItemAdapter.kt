package com.example.foodiary.presentation.adapter

import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.R
import com.example.foodiary.data.remote.vision.dto.DetectedDishItemDto
import java.util.Locale

class ValidatedFoodRecognitionItemAdapter(
    private val onSelectionChanged: (position: Int, isSelected: Boolean) -> Unit,
    private val onWeightChanged: (position: Int, weight: Double?) -> Unit
) : RecyclerView.Adapter<ValidatedFoodRecognitionItemAdapter.ItemViewHolder>() {

    private val items = mutableListOf<DetectedDishItemDto>()
    private val selectedPositions = mutableSetOf<Int>()
    private val currentWeights = mutableMapOf<Int, Double?>()
    private val originalWeights = mutableMapOf<Int, Double?>()

    fun submit(
        newItems: List<DetectedDishItemDto>,
        selectedIndexes: Set<Int>,
        confirmedWeights: Map<Int, Double?>,
        sourceWeights: Map<Int, Double?>
    ) {
        items.clear()
        items.addAll(newItems)
        selectedPositions.clear()
        selectedPositions.addAll(selectedIndexes)
        currentWeights.clear()
        currentWeights.putAll(confirmedWeights)
        originalWeights.clear()
        originalWeights.putAll(sourceWeights)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_recognition_result, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            isSelected = position in selectedPositions,
            currentWeight = currentWeights[position],
            originalWeight = originalWeights[position],
            onToggle = { checked ->
                onSelectionChanged(position, checked)
            },
            onWeightChanged = { weight ->
                currentWeights[position] = weight
                onWeightChanged(position, weight)
            }
        )
    }

    override fun getItemCount(): Int = items.size

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val checkIncludeItem = itemView.findViewById<CheckBox>(R.id.checkIncludeItem)
        private val titleView = itemView.findViewById<TextView>(R.id.textDishName)
        private val metaView = itemView.findViewById<TextView>(R.id.textDishMeta)
        private val macrosView = itemView.findViewById<TextView>(R.id.textDishMacros)
        private val weightHintView = itemView.findViewById<TextView>(R.id.textDishWeightHint)
        private val weightInput = itemView.findViewById<EditText>(R.id.editDishWeight)
        private val ingredientsView = itemView.findViewById<TextView>(R.id.textDishIngredients)

        fun bind(
            item: DetectedDishItemDto,
            isSelected: Boolean,
            currentWeight: Double?,
            originalWeight: Double?,
            onToggle: (Boolean) -> Unit,
            onWeightChanged: (Double?) -> Unit
        ) {
            val title = item.topCandidate?.name
                ?.takeIf { it.isNotBlank() }
                ?: item.candidates.firstOrNull()?.name
                ?: "Неопределённый продукт"

            itemView.alpha = if (isSelected) 1f else 0.56f
            titleView.text = title
            metaView.text = buildMeta(item, originalWeight)
            macrosView.text = buildMacros(item, currentWeight, originalWeight)
            weightHintView.text = buildWeightHint(originalWeight)

            bindWeightInput(item, currentWeight, originalWeight, onWeightChanged)
            weightInput.alpha = if (isSelected) 1f else 0.85f

            checkIncludeItem.setOnCheckedChangeListener(null)
            checkIncludeItem.isChecked = isSelected
            checkIncludeItem.text = if (isSelected) "Учитывать" else "Исключено"
            checkIncludeItem.setOnCheckedChangeListener { _, checked ->
                onToggle(checked)
            }
            itemView.setOnClickListener {
                checkIncludeItem.performClick()
            }

            if (item.ingredients.isEmpty()) {
                ingredientsView.visibility = View.GONE
            } else {
                ingredientsView.visibility = View.VISIBLE
                ingredientsView.text = "Ингредиенты: ${item.ingredients.joinToString()}"
            }
        }

        private fun bindWeightInput(
            item: DetectedDishItemDto,
            currentWeight: Double?,
            originalWeight: Double?,
            onWeightChanged: (Double?) -> Unit
        ) {
            val previousWatcher = weightInput.getTag(R.id.editDishWeight) as? TextWatcher
            if (previousWatcher != null) {
                weightInput.removeTextChangedListener(previousWatcher)
            }

            val formatted = currentWeight?.let { formatNumber(it) }.orEmpty()
            if (weightInput.text?.toString() != formatted) {
                weightInput.setText(formatted)
                if (formatted.isNotEmpty()) {
                    weightInput.setSelection(formatted.length)
                }
            }

            val watcher = weightInput.doAfterTextChanged { editable ->
                val parsed = editable?.toString()
                    ?.trim()
                    ?.replace(',', '.')
                    ?.takeIf { it.isNotBlank() }
                    ?.toDoubleOrNull()
                macrosView.text = buildMacros(item, parsed, originalWeight)
                onWeightChanged(parsed)
            }
            weightInput.setTag(R.id.editDishWeight, watcher)
        }

        private fun buildMeta(item: DetectedDishItemDto, originalWeight: Double?): String {
            val parts = mutableListOf<String>()
            item.topCandidate?.confidence?.let { confidence ->
                parts += "уверенность ${formatPercent(confidence)}"
            }
            originalWeight?.let { grams ->
                parts += "LogMeal: ${formatNumber(grams)} г"
            } ?: item.servingSize?.takeIf { it.isNotBlank() }?.let { serving ->
                parts += serving
            }
            return if (parts.isEmpty()) "Распознано по фото" else parts.joinToString(", ")
        }

        private fun buildMacros(
            item: DetectedDishItemDto,
            currentWeight: Double?,
            originalWeight: Double?
        ): String {
            val factor = when {
                originalWeight != null && originalWeight > 0.0 && currentWeight != null && currentWeight > 0.0 ->
                    currentWeight / originalWeight
                else -> 1.0
            }
            val kcal = (item.caloriesKcal ?: 0.0) * factor
            val protein = (item.proteinG ?: 0.0) * factor
            val fat = (item.fatG ?: 0.0) * factor
            val carbs = (item.carbsG ?: 0.0) * factor
            return "${formatNumber(kcal)} ккал, Б ${formatNumber(protein)} г, Ж ${formatNumber(fat)} г, У ${formatNumber(carbs)} г"
        }

        private fun buildWeightHint(originalWeight: Double?): String {
            return if (originalWeight != null && originalWeight > 0.0) {
                "Подтвердите или поправьте вес этой позиции. Вклад в КБЖУ пересчитается пропорционально."
            } else {
                "Если LogMeal не дал вес, его можно ввести вручную для этой позиции."
            }
        }

        private fun formatPercent(value: Double): String {
            val percent = (value * 100.0).coerceIn(0.0, 100.0)
            return if (percent % 1.0 == 0.0) {
                "${percent.toInt()}%"
            } else {
                String.format(Locale.US, "%.1f%%", percent)
            }
        }

        private fun formatNumber(value: Double?): String {
            val safe = (value ?: 0.0).coerceAtLeast(0.0)
            return if (safe % 1.0 == 0.0) {
                safe.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", safe)
            }
        }
    }
}
