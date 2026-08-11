package com.upsi.smartbus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.databinding.ItemRouteCardBinding
import com.upsi.smartbus.model.Route

class RouteCardAdapter(
    private val routes: List<Route>
) : RecyclerView.Adapter<RouteCardAdapter.RouteCardViewHolder>() {

    inner class RouteCardViewHolder(
        val binding: ItemRouteCardBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteCardViewHolder {
        val binding = ItemRouteCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RouteCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteCardViewHolder, position: Int) {
        val route = routes[position]
        with(holder.binding) {
            tvShortName.text = route.shortName
            tvRouteName.text = route.name
            tvStopChain.text = route.stops.joinToString(" ➔ ")
        }
    }

    override fun getItemCount(): Int = routes.size
}
