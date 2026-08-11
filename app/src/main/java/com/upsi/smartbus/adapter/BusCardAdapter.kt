package com.upsi.smartbus.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.R
import com.upsi.smartbus.databinding.ItemBusCardBinding
import com.upsi.smartbus.logic.EtaPredictor
import com.upsi.smartbus.model.Bus
import java.util.Calendar

class BusCardAdapter(
    private var busList: List<Bus>,
    private val onItemClick: (Bus) -> Unit
) : RecyclerView.Adapter<BusCardAdapter.BusCardViewHolder>() {

    private val etaPredictor = EtaPredictor()

    inner class BusCardViewHolder(
        val binding: ItemBusCardBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusCardViewHolder {
        val binding = ItemBusCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BusCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BusCardViewHolder, position: Int) {
        val bus = busList[position]
        val context = holder.itemView.context

        with(holder.binding) {
            // Get plate number
            val plate = when {
                bus.plateNumber.isNotBlank() -> bus.plateNumber
                bus.licensePlate.isNotBlank() -> bus.licensePlate
                else -> "WAA 1234"
            }

            // Primary title: Route Name + Plate Number
            val titleText = if (bus.routeName.isNotBlank()) {
                "${bus.routeName}  [$plate]"
            } else {
                "${bus.name}  [$plate]"
            }
            tvBusName.text = titleText

            // Route stop sequence description
            val stopsChain = bus.routeStops.joinToString(" ➔ ")
            tvRoute.text = if (stopsChain.isNotBlank()) stopsChain else "KAB ➔ PT ➔ SS ➔ DKP ➔ KAB"

            // Start badge
            tvStartStop.text = "Start: ${bus.startStop.ifEmpty { "KAB" }}"

            // Next stop indicator
            tvNextStop.text = "📍 Next: ${bus.nextStop.ifEmpty { "--" }}"

            // Accent strip color based on status
            val accentColor = when (bus.status) {
                "Working" -> ContextCompat.getColor(context, R.color.status_moving)
                "Resting" -> ContextCompat.getColor(context, R.color.status_resting)
                else -> ContextCompat.getColor(context, R.color.status_offline)
            }
            accentStrip.setBackgroundColor(accentColor)

            // AI ETA
            val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val aiEta = etaPredictor.predictEta(bus.distanceToNext, hourOfDay)
            tvAiEta.text = context.getString(R.string.eta_format, aiEta)

            // Click handler
            root.setOnClickListener { onItemClick(bus) }
        }
    }

    override fun getItemCount(): Int = busList.size

    fun updateList(newList: List<Bus>) {
        busList = newList
        notifyDataSetChanged()
    }
}
