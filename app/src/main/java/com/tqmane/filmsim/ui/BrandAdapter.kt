package com.tqmane.filmsim.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tqmane.filmsim.R
import com.tqmane.filmsim.data.LutBrand

class BrandAdapter(
    private val brands: List<LutBrand>,
    private val onBrandSelected: (LutBrand) -> Unit
) : RecyclerView.Adapter<BrandAdapter.BrandViewHolder>() {

    private var selectedPosition = 0

    class BrandViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chip: com.google.android.material.chip.Chip = view as com.google.android.material.chip.Chip
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrandViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_brand, parent, false)
        return BrandViewHolder(view)
    }

    override fun onBindViewHolder(holder: BrandViewHolder, position: Int) {
        val brand = brands[position]
        holder.chip.text = brand.displayName
        holder.chip.isChecked = (position == selectedPosition)

        holder.chip.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onBrandSelected(brand)
        }
    }

    override fun getItemCount() = brands.size
    
    fun selectFirst() {
        if (brands.isNotEmpty()) {
            onBrandSelected(brands[0])
        }
    }
}
