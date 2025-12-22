package com.proyek.tugasproyek

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MealAdapter(
    private val onEdit: (Meal) -> Unit,
    private val onDelete: (Meal) -> Unit
) : ListAdapter<Meal, MealAdapter.MealViewHolder>(MealDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal, parent, false)
        return MealViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvFood: TextView = itemView.findViewById(R.id.tvFood)
        private val tvPortion: TextView = itemView.findViewById(R.id.tvPortion)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(meal: Meal) {
            tvType.text = meal.type
            tvTime.text = meal.time
            tvFood.text = meal.food
            tvPortion.text = "${meal.portion} porsi"

            btnEdit.setOnClickListener { onEdit(meal) }
            btnDelete.setOnClickListener { onDelete(meal) }
        }
    }
}

class MealDiffCallback : DiffUtil.ItemCallback<Meal>() {
    override fun areItemsTheSame(oldItem: Meal, newItem: Meal): Boolean {
        // Identifikasi unik: kombinasi date + type
        return oldItem.date == newItem.date && oldItem.type == newItem.type
    }

    override fun areContentsTheSame(oldItem: Meal, newItem: Meal): Boolean {
        return oldItem == newItem
    }
}
