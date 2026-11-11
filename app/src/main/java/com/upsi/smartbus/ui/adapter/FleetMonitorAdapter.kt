package com.upsi.smartbus.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.R
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.network.AiApiClient
import com.upsi.smartbus.core.network.AnomalyRequest
import com.upsi.smartbus.core.network.AnomalyResponse
import com.upsi.smartbus.core.util.BusEtaCalculator
import com.upsi.smartbus.core.util.BusSpeedCalculator
import com.upsi.smartbus.core.util.DriverSafetyEvaluator
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.databinding.ItemFleetMonitorRowBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class FleetItem(
    val route: Route,
    val routeOrder: Int,
    val driverName: String,
    val driverUid: String = "",
    val busId: String,
    val plateNumber: String,
    var status: String = "OFFLINE",
    var speed: Double = 0.0,
    var nextStop: String = "",
    var distanceToNext: Double = 0.0,
    var lastUpdated: Long = 0L,
    val isActiveDay: Boolean = true
) {
    val isSpeeding: Boolean get() =
        DriverSafetyEvaluator.evaluateSpeed(
            speed,
            DriverSafetyEvaluator.DEFAULT_CAMPUS_SPEED_LIMIT
        ) != DriverSafetyEvaluator.SafetyLevel.NORMAL && status.equals("working", true)
    val isActive: Boolean get() = status.equals("working", true)
    val isResting: Boolean get() = status.equals("resting", true)
}

class FleetMonitorAdapter(
    private val items: List<FleetItem>,
    private val onClick: (FleetItem) -> Unit
) : RecyclerView.Adapter<FleetMonitorAdapter.VH>() {

    inner class VH(val binding: ItemFleetMonitorRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFleetMonitorRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding
        val ctx = holder.itemView.context

        b.tvFleetRouteName.text = item.route.name
        b.tvFleetDriverInfo.text = buildString {
            append(item.driverName)
            if (item.busId != "—") append("  •  ${item.busId}")
            if (item.plateNumber != "—") append(" (${item.plateNumber})")
        }

        val aiEta = EtaPredictor.predict(item.distanceToNext)
        val etaMinutes = BusEtaCalculator.calculateStandardEtaMinutes(item.distanceToNext, item.speed)

        when {
            item.isSpeeding -> {
                b.statusBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                b.statusDot.setBackgroundResource(R.drawable.dot_green)
                b.tvStatusLabel.text = "SPEEDING"
                b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                b.llStatusPill.setBackgroundResource(R.drawable.bg_status_moving)
                b.llSpeedAlert.visibility = View.VISIBLE
                b.tvSpeedAlert.text = DriverSafetyEvaluator.getSpeedAlertLabel(
                    item.speed,
                    DriverSafetyEvaluator.DEFAULT_CAMPUS_SPEED_LIMIT
                )
                b.tvFleetSpeed.setTextColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                b.tvFleetSpeed.text = BusSpeedCalculator.formatSpeed(item.speed)
                b.tvFleetNextStop.text = "Next: ${item.nextStop.ifEmpty { "En Route" }} (${BusEtaCalculator.formatDistance(item.distanceToNext)})"
                b.tvFleetEta.text = "ETA: $etaMinutes min"
                b.tvFleetAiEta.text = "AI: ~$aiEta min"
            }

            item.isActive -> {
                b.statusBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_moving))
                b.statusDot.setBackgroundResource(R.drawable.dot_green)
                b.tvStatusLabel.text = "ACTIVE"
                b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_moving))
                b.llStatusPill.setBackgroundResource(R.drawable.bg_status_moving)
                b.llSpeedAlert.visibility = View.GONE
                b.tvFleetSpeed.setTextColor(ContextCompat.getColor(ctx, R.color.status_moving))
                b.tvFleetSpeed.text = BusSpeedCalculator.formatSpeed(item.speed)
                b.tvFleetNextStop.text = "Next: ${item.nextStop.ifEmpty { "En Route" }} (${BusEtaCalculator.formatDistance(item.distanceToNext)})"
                b.tvFleetEta.text = "ETA: $etaMinutes min"
                b.tvFleetAiEta.text = "AI: ~$aiEta min"

                // Asynchronous AI Anomaly Check (Isolation Forest + Safety Rules)
                if (item.speed > 0 || item.distanceToNext > 0) {
                    checkFleetItemAnomaly(item, holder)
                }
            }

            item.isResting -> {
                b.statusBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_resting))
                b.statusDot.setBackgroundResource(R.drawable.dot_orange)
                b.tvStatusLabel.text = "RESTING"
                b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_resting))
                b.llStatusPill.setBackgroundResource(R.drawable.bg_status_resting)
                b.llSpeedAlert.visibility = View.GONE
                b.tvFleetSpeed.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                b.tvFleetSpeed.text = "0 km/h"
                b.tvFleetNextStop.text = "Next: Resting at depot"
                b.tvFleetEta.text = "ETA: —"
                b.tvFleetAiEta.text = "AI: —"
            }

            else -> {
                b.statusBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_offline))
                b.statusDot.setBackgroundResource(R.drawable.dot_gray)
                b.tvStatusLabel.text = "OFFLINE"
                b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_offline))
                b.llStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
                b.llSpeedAlert.visibility = View.GONE
                b.tvFleetSpeed.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                b.tvFleetSpeed.text = "0 km/h"
                b.tvFleetNextStop.text = "Next: Offline / Depot"
                b.tvFleetEta.text = "ETA: —"
                b.tvFleetAiEta.text = "AI: —"
            }
        }

        // Pure green stop chain highlight
        if (item.route.stops.isNotEmpty()) {
            b.tvFleetStops.visibility = View.VISIBLE
            val stops = item.route.stops
            val currentTarget = item.nextStop.trim()
            val targetIdx = stops.indexOfFirst { it.trim().equals(currentTarget, ignoreCase = true) }

            val html = if (targetIdx >= 0) {
                val sb = StringBuilder()
                stops.forEachIndexed { idx, stopName ->
                    if (idx > 0) sb.append(" <font color='#D1D5DB'>\u2192</font> ")
                    when {
                        idx == targetIdx -> {
                            sb.append("<font color='#059669'><b>$stopName</b></font>")
                        }
                        idx < targetIdx -> {
                            sb.append("<font color='#9CA3AF'>$stopName</font>")
                        }
                        else -> {
                            sb.append("<font color='#6B7280'>$stopName</font>")
                        }
                    }
                }
                sb.toString()
            } else {
                stops.joinToString(" <font color='#D1D5DB'>\u2192</font> ") { stopName -> "<font color='#6B7280'>$stopName</font>" }
            }

            b.tvFleetStops.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        } else {
            b.tvFleetStops.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }

    private fun checkFleetItemAnomaly(item: FleetItem, holder: VH) {
        val request = AnomalyRequest(
            speed_kmh = item.speed,
            status = if (item.isActive) "WORKING" else "RESTING"
        )
        AiApiClient.getService().checkAnomaly(request)
            .enqueue(object : Callback<AnomalyResponse> {
                override fun onResponse(
                    call: Call<AnomalyResponse>,
                    response: Response<AnomalyResponse>
                ) {
                    if (!holder.binding.root.isAttachedToWindow) return
                    val res = response.body()?.result ?: return
                    if (res.is_anomaly && !res.alerts.isNullOrEmpty()) {
                        holder.binding.llSpeedAlert.visibility = View.VISIBLE
                        val firstAlert = res.alerts.firstOrNull() ?: "ANOMALY"
                        holder.binding.tvSpeedAlert.text = "⚠️ $firstAlert"
                    }
                }

                override fun onFailure(call: Call<AnomalyResponse>, t: Throwable) {
                    // Ignore network failures for background telemetry
                }
            })
    }
}
