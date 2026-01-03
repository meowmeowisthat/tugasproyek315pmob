package com.proyek.tugasproyek

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.proyek.tugasproyek.databinding.ItemMealBinding

class MealAdapter(
    private val onItemClick: (Meal) -> Unit
) : ListAdapter<Meal, MealAdapter.MealViewHolder>(MealDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val binding = ItemMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MealViewHolder(private val binding: ItemMealBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meal: Meal) {
            with(binding) {
                tvMealName.text = meal.food
                tvMealType.text = meal.type
                tvTime.text = meal.time
                tvDate.text = meal.date

                tvCalories.text = "${meal.calorie} kal"

                val context = root.context
                val (bgColor, iconColor, iconRes) = when (meal.type) {
                    "Makan Malam" -> Triple("#E0F2F1", "#009688", R.drawable.ic_menu)
                    "Makan Siang" -> Triple("#E8F5E9", "#4CAF50", R.drawable.ic_menu)
                    "Sarapan" -> Triple("#FFF3E0", "#FF9800", R.drawable.ic_menu)
                    "Cemilan" -> Triple("#F3E5F5", "#9C27B0", R.drawable.ic_menu)
                    else -> Triple("#F5F5F5", "#9E9E9E", R.drawable.ic_menu)
                }

                iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColor))

                ivMealIcon.setColorFilter(Color.parseColor(iconColor))
                ivMealIcon.setImageResource(iconRes)

                root.setOnClickListener {
                    onItemClick(meal)
                }
            }
        }
    }
}

class MealDiffCallback : DiffUtil.ItemCallback<Meal>() {
    override fun areItemsTheSame(oldItem: Meal, newItem: Meal): Boolean {
        return oldItem.date == newItem.date && oldItem.time == newItem.time && oldItem.food == newItem.food
    }

    override fun areContentsTheSame(oldItem: Meal, newItem: Meal): Boolean {
        return oldItem == newItem
    }
}