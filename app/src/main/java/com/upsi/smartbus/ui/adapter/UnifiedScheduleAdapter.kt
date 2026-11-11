package com.upsi.smartbus.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.R
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.DriverAccount
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.databinding.ItemScheduleCardBinding

data class UnifiedBusScheduleItem(
    val route: Route,
    val liveBus: Bus?,
    val driver: DriverAccount,
    val isActive: Boolean
)

class UnifiedScheduleAdapter(
    private var items: List<UnifiedBusScheduleItem>,
    private val onItemClick: (UnifiedBusScheduleItem) -> Unit
) : RecyclerView.Adapter<UnifiedScheduleAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemScheduleCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val isWeekday = item.route.scheduleType.equals("WEEKDAY", true)
        val interval = if (isWeekday) "Every 30m" else "Every 60m"

        with(holder.binding) {
            tvScheduleRouteName.text = item.route.name
            tvScheduleRouteDesc.text = item.route.shortName.ifEmpty { "Official Campus Route" }

            // Active vs Offline styling
            if (item.isActive && item.liveBus != null) {
                val isWorking = item.liveBus.status.equals("WORKING", true)
                statusBar.setBackgroundResource(if (isWorking) R.drawable.bg_status_bar_green else R.drawable.bg_status_bar_orange)
                llStatusPill.setBackgroundResource(if (isWorking) R.drawable.bg_status_moving else R.drawable.bg_status_resting)
                statusDot.setBackgroundResource(if (isWorking) R.drawable.dot_green else R.drawable.dot_orange)
                tvStatusLabel.text = if (isWorking) "ACTIVE" else "RESTING"
                tvStatusLabel.setTextColor(
                    ContextCompat.getColor(context, if (isWorking) R.color.status_moving else R.color.status_resting)
                )

                val plate = item.liveBus.plateNumber.ifEmpty { item.driver.plateNumber }
                val next = item.liveBus.nextStop.ifEmpty { item.route.stops.getOrNull(1).orEmpty() }
                tvScheduleBusInfo.text = "Bus: $plate • Next: $next • $interval"
            } else {
                statusBar.setBackgroundColor(android.graphics.Color.parseColor("#CBD5E1"))
                llStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
                statusDot.setBackgroundResource(R.drawable.dot_gray)
                tvStatusLabel.text = "OFFLINE"
                tvStatusLabel.setTextColor(ContextCompat.getColor(context, R.color.status_offline))

                val plate = item.driver.plateNumber
                tvScheduleBusInfo.text = "Bus: $plate • Scheduled: $interval"
            }

            // Driver info
            val phone = if (item.driver.phone.isNotEmpty()) " • 📞 ${item.driver.phone}" else ""
            tvScheduleDriverName.text = "${item.driver.driverName}$phone"

            // Stops sequence
            tvScheduleStops.text = item.route.stops.joinToString(" ➔ ")

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<UnifiedBusScheduleItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
