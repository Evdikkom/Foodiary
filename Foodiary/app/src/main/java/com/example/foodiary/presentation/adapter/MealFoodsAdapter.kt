package com.example.foodiary.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import coil.load
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.R

class MealFoodsAdapter(
    private val onClick: (MealFoodRowUi) -> Unit,
    private val onLongPress: (MealFoodRowUi) -> Unit
) : ListAdapter<MealFoodRowUi, MealFoodsAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_food_entry, parent, false)
        return VH(view, onClick, onLongPress)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onClick: (MealFoodRowUi) -> Unit,
        private val onLongPress: (MealFoodRowUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val image: ImageView = itemView.findViewById(R.id.imageFood)
        private val time: TextView = itemView.findViewById(R.id.textFoodTime)
        private val foodName: TextView = itemView.findViewById(R.id.textFoodName)
        private val amount: TextView = itemView.findViewById(R.id.textFoodAmount)
        private val action: TextView = itemView.findViewById(R.id.textFoodAction)

        fun bind(item: MealFoodRowUi) {
            time.text = item.timeText
            foodName.text = item.foodName
            amount.text = item.gramsText
            action.text = "Изменить"
            bindImage(item.imageUrl)

            itemView.setOnClickListener {
                onClick(item)
            }

            itemView.setOnLongClickListener {
                onLongPress(item)
                true
            }

        }

        private fun bindImage(ref: String?) {
            val normalized = ref?.trim().orEmpty()
            if (normalized.startsWith("drawable://")) {
                val resId = itemView.resources.getIdentifier(normalized.removePrefix("drawable://"), "drawable", itemView.context.packageName)
                if (resId != 0) image.setImageResource(resId) else image.setImageResource(R.drawable.ic_custom_food_placeholder)
            } else if (normalized.isBlank()) {
                image.setImageResource(R.drawable.ic_custom_food_placeholder)
            } else {
                image.load(normalized)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MealFoodRowUi>() {
        override fun areItemsTheSame(oldItem: MealFoodRowUi, newItem: MealFoodRowUi): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MealFoodRowUi, newItem: MealFoodRowUi): Boolean =
            oldItem == newItem
    }
}

data class MealFoodRowUi(
    val id: Long,
    val foodId: String,
    val quantityInGrams: Double,
    val timeText: String,
    val foodName: String,
    val gramsText: String,
    val note: String,
    val imageUrl: String?
)
