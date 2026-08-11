package com.upsi.smartbus.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.core.model.BusStop
import com.upsi.smartbus.databinding.ItemLegendBinding

class LegendAdapter(
    private val stops: List<BusStop>
) : RecyclerView.Adapter<LegendAdapter.LegendViewHolder>() {

    inner class LegendViewHolder(
        val binding: ItemLegendBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegendViewHolder {
        val binding = ItemLegendBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LegendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LegendViewHolder, position: Int) {
        val stop = stops[position]
        holder.binding.tvAbbreviation.text = stop.abbreviation
        holder.binding.tvFullName.text = stop.fullName
    }

    override fun getItemCount(): Int = stops.size
}
