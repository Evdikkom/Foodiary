package com.example.foodiary.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.R
import com.example.foodiary.domain.model.MealType
import com.example.foodiary.presentation.model.MealItemUi

class MealsTodayAdapter(
    private val onLongPress: (MealItemUi) -> Unit
) : ListAdapter<MealItemUi, MealsTodayAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_today, parent, false)
        return VH(view, onLongPress)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onLongPress: (MealItemUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val time: TextView = itemView.findViewById(R.id.textMealTime)
        private val typeChip: TextView = itemView.findViewById(R.id.textMealTypeChip)
        private val foodName: TextView = itemView.findViewById(R.id.textMealFoodName)
        private val amount: TextView = itemView.findViewById(R.id.textMealAmount)

        fun bind(item: MealItemUi) {
            time.text = item.timeText
            typeChip.text = mealTypeLabel(item.mealType)
            foodName.text = item.foodName
            amount.text = item.gramsText

            itemView.setOnLongClickListener {
                onLongPress(item)
                true
            }
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
    }

    private object Diff : DiffUtil.ItemCallback<MealItemUi>() {
        override fun areItemsTheSame(oldItem: MealItemUi, newItem: MealItemUi): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MealItemUi, newItem: MealItemUi): Boolean =
            oldItem == newItem
    }
}
