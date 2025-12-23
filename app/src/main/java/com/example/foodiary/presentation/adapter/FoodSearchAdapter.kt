package com.example.foodiary.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.R
import com.example.foodiary.domain.model.Food

class FoodSearchAdapter(
    private val onClick: (Food) -> Unit
) : ListAdapter<Food, FoodSearchAdapter.FoodViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_search, parent, false)
        return FoodViewHolder(view as TextView, onClick)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FoodViewHolder(
        private val textView: TextView,
        private val onClick: (Food) -> Unit
    ) : RecyclerView.ViewHolder(textView) {

        fun bind(item: Food) {
            textView.text = item.name
            textView.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Food>() {
        override fun areItemsTheSame(oldItem: Food, newItem: Food): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Food, newItem: Food): Boolean = oldItem == newItem
    }
}
