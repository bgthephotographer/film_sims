package com.tqmane.filmsim.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tqmane.filmsim.R
import com.tqmane.filmsim.data.LutCategory
import com.tqmane.filmsim.data.LutGenre

class GenreAdapter(
    private var items: List<LutCategory>,
    private val onCategorySelected: (LutCategory) -> Unit
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {

    private var selectedPosition = 0

    class GenreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chip: com.google.android.material.chip.Chip = view as com.google.android.material.chip.Chip
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_genre, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val category = items[position]
        holder.chip.text = category.displayName
        holder.chip.isChecked = (position == selectedPosition)

        holder.chip.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onCategorySelected(category)
        }
    }

    override fun getItemCount() = items.size
    
    fun updateCategories(newItems: List<LutCategory>) {
        items = newItems
        selectedPosition = 0
        notifyDataSetChanged()
        
        // Auto-select first category
        if (newItems.isNotEmpty()) {
            onCategorySelected(newItems[0])
        }
    }
}