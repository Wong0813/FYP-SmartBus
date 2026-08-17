package com.upsi.smartbus.feature.student.map

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.R
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.databinding.DialogBusSelectorBinding
import com.upsi.smartbus.databinding.ItemBusSelectorRouteBinding
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper

object BusSelectorDialog {

    sealed class SelectorItem {
        object AllBuses : SelectorItem()
        data class RouteItem(val route: Route) : SelectorItem()
    }

    fun show(
        context: Context,
        routes: List<Route>,
        activeBuses: List<Bus>,
        selectedRouteName: String?,
        onAllBusesSelected: () -> Unit,
        onRouteSelected: (Route, Bus?) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogBusSelectorBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels * 0.90).toInt().coerceAtMost(600)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        var currentFilter = "ALL" // "ALL", "WEEKDAY", "SATURDAY"

        fun getFilteredItems(): List<SelectorItem> {
            val list = mutableListOf<SelectorItem>()
            list.add(SelectorItem.AllBuses)

            // Strictly sorted: Laluan 1..18, then Shuttle Campus
            val sortedAll = AdminSortHelper.sortRoutes(routes)

            val filteredRoutes = when (currentFilter) {
                "WEEKDAY" -> sortedAll.filter { it.scheduleType.equals("WEEKDAY", ignoreCase = true) }
                "SATURDAY" -> sortedAll.filter { it.scheduleType.equals("SATURDAY", ignoreCase = true) }
                else -> sortedAll
            }
            filteredRoutes.forEach { list.add(SelectorItem.RouteItem(it)) }
            return list
        }

        val adapter = RoutePickerAdapter(
            items = getFilteredItems().toMutableList(),
            activeBuses = activeBuses,
            selectedRouteName = selectedRouteName,
            onItemClicked = { item ->
                dialog.dismiss()
                when (item) {
                    is SelectorItem.AllBuses -> onAllBusesSelected()
                    is SelectorItem.RouteItem -> {
                        val liveBus = activeBuses.find { it.routeName.equals(item.route.name, ignoreCase = true) }
                        onRouteSelected(item.route, liveBus)
                    }
                }
            }
        )

        binding.rvRoutesList.layoutManager = LinearLayoutManager(context)
        binding.rvRoutesList.adapter = adapter

        fun updateFilterUi() {
            val activeBg = R.drawable.bg_button_crimson
            val inactiveBg = R.drawable.bg_filter_chip
            val activeText = ContextCompat.getColor(context, R.color.text_white)
            val inactiveText = ContextCompat.getColor(context, R.color.text_secondary)

            binding.chipFilterAll.setBackgroundResource(if (currentFilter == "ALL") activeBg else inactiveBg)
            binding.chipFilterAll.setTextColor(if (currentFilter == "ALL") activeText else inactiveText)

            binding.chipFilterWeekday.setBackgroundResource(if (currentFilter == "WEEKDAY") activeBg else inactiveBg)
            binding.chipFilterWeekday.setTextColor(if (currentFilter == "WEEKDAY") activeText else inactiveText)

            binding.chipFilterSaturday.setBackgroundResource(if (currentFilter == "SATURDAY") activeBg else inactiveBg)
            binding.chipFilterSaturday.setTextColor(if (currentFilter == "SATURDAY") activeText else inactiveText)

            adapter.updateItems(getFilteredItems())
        }

        binding.chipFilterAll.setOnClickListener { currentFilter = "ALL"; updateFilterUi() }
        binding.chipFilterWeekday.setOnClickListener { currentFilter = "WEEKDAY"; updateFilterUi() }
        binding.chipFilterSaturday.setOnClickListener { currentFilter = "SATURDAY"; updateFilterUi() }
        binding.btnCloseDialog.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private class RoutePickerAdapter(
        private var items: MutableList<SelectorItem>,
        private val activeBuses: List<Bus>,
        private val selectedRouteName: String?,
        private val onItemClicked: (SelectorItem) -> Unit
    ) : RecyclerView.Adapter<RoutePickerAdapter.VH>() {

        fun updateItems(newItems: List<SelectorItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemBusSelectorRouteBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemBusSelectorRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val b = holder.binding
            val ctx = holder.itemView.context

            when (val item = items[position]) {
                is SelectorItem.AllBuses -> {
                    val isSelected = selectedRouteName == null
                    b.flBadgeContainer.setBackgroundResource(R.drawable.bg_circle_amber)
                    b.tvShortName.text = "★"
                    b.tvShortName.setTextColor(Color.WHITE)
                    b.tvRouteName.text = "All Buses (All Active Buses)"
                    b.tvRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))

                    val activeCount = activeBuses.count {
                        it.status.equals("working", true) || it.status.equals("resting", true)
                    }
                    b.tvStopChain.text = if (activeCount > 0) "$activeCount buses currently operating on map" else "No active buses running currently"
                    b.tvStopChain.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))

                    b.llLivePill.visibility = if (activeCount > 0) View.VISIBLE else View.GONE
                    b.tvLiveStatus.text = "$activeCount LIVE"
                    b.llLivePill.setBackgroundResource(R.drawable.bg_status_moving)
                    b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_moving))

                    b.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                    b.cardRouteItem.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, if (isSelected) R.color.crimson_alpha_06 else R.color.card_bg)
                    )

                    holder.itemView.setOnClickListener { onItemClicked(item) }
                }
                is SelectorItem.RouteItem -> {
                    val route = item.route
                    val isSelected = selectedRouteName.equals(route.name, ignoreCase = true)

                    // Check active status
                    val matchingBus = activeBuses.find { it.routeName.equals(route.name, true) }
                    val isWorking = matchingBus?.status.equals("working", true)
                    val isResting = matchingBus?.status.equals("resting", true)

                    val key = AdminSortHelper.routeOrderKey(route)
                    val badgeLabel = when {
                        key in 1..99 -> "L$key"
                        key == 901 -> "SC1"
                        key == 902 -> "SC2"
                        else -> route.shortName.take(3)
                    }
                    b.tvShortName.text = badgeLabel

                    b.tvRouteName.text = route.name
                    b.tvStopChain.text = if (route.stops.isNotEmpty()) route.stops.joinToString(" ➔ ") else "No stops available"

                    if (isWorking) {
                        // Active Working -> Crimson Badge & Green LIVE
                        b.flBadgeContainer.setBackgroundResource(R.drawable.bg_circle_crimson)
                        b.tvShortName.setTextColor(Color.WHITE)
                        b.tvRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                        b.tvStopChain.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                        b.llLivePill.visibility = View.VISIBLE
                        b.llLivePill.setBackgroundResource(R.drawable.bg_status_moving)
                        b.tvLiveStatus.text = "LIVE"
                        b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_moving))
                    } else if (isResting) {
                        // Resting -> Amber Badge & Orange RESTING
                        b.flBadgeContainer.setBackgroundResource(R.drawable.bg_circle_amber)
                        b.tvShortName.setTextColor(Color.WHITE)
                        b.tvRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                        b.tvStopChain.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                        b.llLivePill.visibility = View.VISIBLE
                        b.llLivePill.setBackgroundResource(R.drawable.bg_status_resting)
                        b.tvLiveStatus.text = "RESTING"
                        b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_resting))
                    } else {
                        // Offline / Inactive -> Clean Grey appearance
                        b.flBadgeContainer.setBackgroundResource(R.drawable.bg_filter_chip)
                        b.tvShortName.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                        b.tvRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                        b.tvStopChain.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                        b.llLivePill.visibility = View.GONE
                    }

                    b.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                    b.cardRouteItem.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, if (isSelected) R.color.crimson_alpha_06 else R.color.card_bg)
                    )

                    holder.itemView.setOnClickListener { onItemClicked(item) }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
