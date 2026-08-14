package com.example.foodiary.presentation.dialog

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foodiary.R
import com.example.foodiary.data.local.database.AppDatabase
import com.example.foodiary.data.mapper.toDomain
import com.example.foodiary.domain.model.Food
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class IngredientPickerDialogFragment : DialogFragment(R.layout.dialog_pick_ingredient) {

    companion object {
        const val REQUEST_KEY = "ingredient_picker_result"
        const val RESULT_FOOD_ID = "result_food_id"
        private const val PICKER_LIMIT = 60

        fun newInstance(): IngredientPickerDialogFragment = IngredientPickerDialogFragment()
    }

    private lateinit var adapter: PickerAdapter
    private var searchJob: Job? = null

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

        adapter = PickerAdapter { food ->
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putString(RESULT_FOOD_ID, food.id) }
            )
            dismissAllowingStateLoss()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerFoods)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<ImageView>(R.id.buttonClose).setOnClickListener {
            dismissAllowingStateLoss()
        }

        val searchInput = view.findViewById<EditText>(R.id.editSearchFood)
        searchInput.doAfterTextChanged { text ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(220)
                loadFoods(text?.toString().orEmpty())
            }
        }

        loadFoods("")
    }

    private fun loadFoods(queryRaw: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getInstance(requireContext()).foodDao()
            val query = queryRaw.trim()
            val items = if (query.isBlank()) {
                dao.getFoodsForPicker(PICKER_LIMIT)
            } else {
                val variants = buildSearchVariants(query)
                dao.searchFoodsForPicker(
                    query = variants.getOrElse(0) { query },
                    altQuery = variants.getOrElse(1) { query },
                    thirdQuery = variants.getOrElse(2) { query },
                    limit = PICKER_LIMIT
                )
            }.map { it.toDomain() }
            adapter.submit(items)
        }
    }

    private fun buildSearchVariants(query: String): List<String> {
        val lower = query.lowercase(Locale("ru"))
        val variants = linkedSetOf(query)
        mapOf(
            "ris" to "рис",
            "rice" to "рис",
            "grechka" to "гречка",
            "kurica" to "курица",
            "chicken" to "курица",
            "tvorog" to "творог",
            "ovsyanka" to "овсянка"
        )[lower]?.let(variants::add)
        return variants.take(3)
    }

    private class PickerAdapter(
        private val onClick: (Food) -> Unit
    ) : RecyclerView.Adapter<PickerAdapter.VH>() {

        private val items = mutableListOf<Food>()

        fun submit(newItems: List<Food>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val context = parent.context
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 10)
                }
                setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
                setBackgroundResource(R.drawable.bg_addmeal_soft_card)
            }

            val title = TextView(context).apply {
                textSize = 16f
                setTextColor(0xFF2F2433.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val subtitle = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(context, 6) }
                textSize = 13f
                setTextColor(0xFF6B5B73.toInt())
            }

            root.addView(title)
            root.addView(subtitle)
            return VH(root, title, subtitle, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        private class VH(
            itemView: View,
            private val title: TextView,
            private val subtitle: TextView,
            private val onClick: (Food) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private var current: Food? = null

            init {
                itemView.setOnClickListener { current?.let(onClick) }
            }

            fun bind(food: Food) {
                current = food
                title.text = food.name
                subtitle.text = buildString {
                    if (food.isCustom) append(if (food.category == "custom_recipe") "Блюдо, " else "Мой, ")
                    append(
                        "${format(food.caloriesPer100g)} ккал, Б ${format(food.proteinPer100g)} г, Ж ${format(food.fatPer100g)} г, У ${format(food.carbsPer100g)} г"
                    )
                }
            }

            private fun format(value: Double): String {
                return if (value % 1.0 == 0.0) value.toInt().toString()
                else String.format(java.util.Locale.US, "%.1f", value)
            }
        }

        companion object {
            private fun dp(context: android.content.Context, value: Int): Int {
                return (value * context.resources.displayMetrics.density).toInt()
            }
        }
    }
}
