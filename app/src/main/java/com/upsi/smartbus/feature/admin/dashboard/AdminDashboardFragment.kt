package com.upsi.smartbus.feature.admin.dashboard

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.databinding.FragmentAdminDashboardBinding
import com.upsi.smartbus.databinding.ItemFleetMonitorRowBinding
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }
    private val etaPredictor = EtaPredictor()

    companion object {
        private const val CAMPUS_SPEED_LIMIT = 40
    }

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
        val isSpeeding: Boolean get() = speed > CAMPUS_SPEED_LIMIT && status.equals("working", true)
        val isActive: Boolean get() = status.equals("working", true)
        val isResting: Boolean get() = status.equals("resting", true)
    }

    private val allFleetItems = mutableListOf<FleetItem>()
    private val displayFleetItems = mutableListOf<FleetItem>()
    private lateinit var fleetAdapter: FleetMonitorAdapter
    private var busListener: ListenerRegistration? = null
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnHeroDrawer.setOnClickListener {
            (activity as? AdminActivity)?.openDrawer()
        }
        setupDateHeader()
        setupSearch()
        setupFleetMonitor()
        loadFleetData()
        listenToBusUpdates()
    }

    // ══════════════════════════════════════════════════════════════
    // HERO HEADER
    // ══════════════════════════════════════════════════════════════

    private fun setupDateHeader() {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
        binding.tvCurrentDate.text = sdf.format(cal.time)

        when (dayOfWeek) {
            Calendar.SUNDAY -> {
                binding.tvDayType.text = "NO SERVICE"
            }
            Calendar.SATURDAY -> {
                binding.tvDayType.text = "SATURDAY"
            }
            else -> {
                binding.tvDayType.text = "WEEKDAY"
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // REAL-TIME SEARCH & FILTER
    // ══════════════════════════════════════════════════════════════

    private fun setupSearch() {
        binding.etSearchFleet.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty().lowercase()
                binding.btnClearFleetSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterFleetList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearFleetSearch.setOnClickListener {
            binding.etSearchFleet.setText("")
        }
    }

    private fun filterFleetList() {
        displayFleetItems.clear()
        if (searchQuery.isEmpty()) {
            displayFleetItems.addAll(allFleetItems)
        } else {
            displayFleetItems.addAll(allFleetItems.filter { item ->
                item.route.name.lowercase().contains(searchQuery) ||
                        item.route.shortName.lowercase().contains(searchQuery) ||
                        item.driverName.lowercase().contains(searchQuery) ||
                        item.busId.lowercase().contains(searchQuery) ||
                        item.plateNumber.lowercase().contains(searchQuery) ||
                        item.nextStop.lowercase().contains(searchQuery) ||
                        item.status.lowercase().contains(searchQuery) ||
                        item.route.stops.any { it.lowercase().contains(searchQuery) }
            })
        }

        if (displayFleetItems.isEmpty()) {
            binding.llEmptyFleet.visibility = View.VISIBLE
            binding.rvFleetMonitor.visibility = View.GONE
        } else {
            binding.llEmptyFleet.visibility = View.GONE
            binding.rvFleetMonitor.visibility = View.VISIBLE
        }

        fleetAdapter.notifyDataSetChanged()
    }

    // ══════════════════════════════════════════════════════════════
    // FLEET MONITOR LIST
    // ══════════════════════════════════════════════════════════════

    private fun setupFleetMonitor() {
        fleetAdapter = FleetMonitorAdapter(
            items = displayFleetItems,
            onClick = { item ->
                (activity as? AdminActivity)?.navigateToLiveMap(item.route.name)
            }
        )
        binding.rvFleetMonitor.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFleetMonitor.adapter = fleetAdapter
    }

    private fun loadFleetData() {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isSaturday = dayOfWeek == Calendar.SATURDAY

        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            allFleetItems.clear()
            val canonical = AdminSortHelper.canonicalRoutes()
            val todayRoutes = when {
                isSaturday -> routes.filter { it.scheduleType.equals("SATURDAY", true) }
                isWeekday -> routes.filter { it.scheduleType.equals("WEEKDAY", true) }
                else -> emptyList()
            }.let { AdminSortHelper.sortRoutes(it) }

            todayRoutes.forEach { route ->
                val order = canonical.indexOfFirst { it.name == route.name }
                    .let { if (it >= 0) it + 1 else AdminSortHelper.routeOrderKey(route) }
                allFleetItems.add(
                    FleetItem(
                        route = route,
                        routeOrder = order,
                        driverName = "Unassigned",
                        busId = "—",
                        plateNumber = "—",
                        isActiveDay = dayOfWeek != Calendar.SUNDAY
                    )
                )
            }

            // Load driver assignments
            db.collection("users").whereEqualTo("role", "DRIVER").get()
                .addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    for (doc in snapshot.documents) {
                        if (doc.id.startsWith("driver_laluan") || doc.id.startsWith("driver_shuttle")) continue
                        val assignedRoute = doc.getString("assignedRoute") ?: continue
                        val idx = allFleetItems.indexOfFirst { it.route.name == assignedRoute }
                        if (idx >= 0) {
                            val item = allFleetItems[idx]
                            allFleetItems[idx] = item.copy(
                                driverName = doc.getString("name") ?: item.driverName,
                                driverUid = doc.id,
                                busId = doc.getString("assignedBus").orEmpty().ifEmpty { item.busId },
                                plateNumber = doc.getString("plateNumber").orEmpty().ifEmpty { item.plateNumber }
                            )
                        }
                    }
                    filterFleetList()
                }

            filterFleetList()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // REAL-TIME BUS STATUS LISTENER (Realtime Database)
    // ══════════════════════════════════════════════════════════════

    private var rtdbListener: com.google.firebase.database.ValueEventListener? = null

    private fun listenToBusUpdates() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        rtdbListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (_binding == null) return

                val busMap = mutableMapOf<String, com.upsi.smartbus.core.model.LiveBusLocation>()
                for (child in snapshot.children) {
                    val busId = child.key ?: continue
                    val lat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("longitude").getValue(Double::class.java) ?: 0.0
                    val speed = child.child("speed").getValue(Double::class.java) ?: 0.0
                    val status = child.child("status").getValue(String::class.java) ?: "IDLE"
                    val nextStop = child.child("nextStop").getValue(String::class.java) ?: ""
                    val dist = child.child("distanceToNext").getValue(Double::class.java) ?: 0.0
                    val routeName = child.child("routeName").getValue(String::class.java) ?: ""
                    val lastUpdated = child.child("lastUpdated").getValue(Long::class.java) ?: System.currentTimeMillis()

                    val liveLoc = com.upsi.smartbus.core.model.LiveBusLocation(
                        latitude = lat,
                        longitude = lng,
                        speed = speed,
                        status = status,
                        nextStop = nextStop,
                        distanceToNext = dist,
                        routeName = routeName,
                        lastUpdated = lastUpdated
                    )
                    busMap[busId] = liveLoc
                    if (routeName.isNotEmpty()) {
                        busMap[routeName] = liveLoc
                    }
                }

                var online = 0; var resting = 0; var offline = 0

                allFleetItems.indices.forEach { i ->
                    val item = allFleetItems[i]
                    val bus = busMap[item.busId] ?: busMap[item.route.name]

                    allFleetItems[i] = item.copy(
                        status = bus?.status ?: "IDLE",
                        speed = bus?.speed ?: 0.0,
                        nextStop = bus?.nextStop ?: "",
                        distanceToNext = bus?.distanceToNext ?: 0.0,
                        lastUpdated = bus?.lastUpdated ?: 0L
                    )

                    if (item.isActiveDay) {
                        when ((bus?.status ?: "IDLE").lowercase()) {
                            "working" -> online++
                            "resting" -> resting++
                            else -> offline++
                        }
                    } else {
                        offline++
                    }
                }

                // Sort: Speeding first → Active → Resting → Offline
                allFleetItems.sortWith(compareBy<FleetItem> {
                    when {
                        it.isSpeeding -> 0
                        it.isActive -> 1
                        it.isResting -> 2
                        else -> 3
                    }
                }.thenBy { it.routeOrder })

                binding.tvTotalFleetCount.text = "${allFleetItems.size}"
                binding.tvStatOnlineCountSmall.text = "$online"
                binding.tvStatRestingCount.text = "$resting"
                binding.tvStatOfflineCount.text = "$offline"

                filterFleetList()
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.addValueEventListener(rtdbListener!!)
    }

    override fun onDestroyView() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    // ══════════════════════════════════════════════════════════════
    // FLEET MONITOR ADAPTER
    // ══════════════════════════════════════════════════════════════

    inner class FleetMonitorAdapter(
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

            val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val aiEta = etaPredictor.predictEta(item.distanceToNext, hourOfDay)
            val etaMinutes = if (item.speed > 5.0) {
                kotlin.math.ceil(item.distanceToNext / item.speed * 60.0).toInt().coerceAtLeast(1)
            } else {
                aiEta
            }

            when {
                item.isSpeeding -> {
                    b.statusBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                    b.statusDot.setBackgroundResource(R.drawable.dot_green)
                    b.tvStatusLabel.text = "SPEEDING"
                    b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                    b.llStatusPill.setBackgroundResource(R.drawable.bg_status_moving)
                    b.llSpeedAlert.visibility = View.VISIBLE
                    b.tvSpeedAlert.text = "⚠️ EXCEEDING LIMIT (${String.format(Locale.ENGLISH, "%.0f", item.speed)} km/h)"
                    b.tvFleetSpeed.setTextColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                    b.tvFleetSpeed.text = "${String.format(Locale.ENGLISH, "%.0f", item.speed)} km/h"
                    b.tvFleetNextStop.text = "Next: ${item.nextStop.ifEmpty { "En Route" }} (${String.format(Locale.ENGLISH, "%.1f", item.distanceToNext)} km)"
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
                    b.tvFleetSpeed.text = "${String.format(Locale.ENGLISH, "%.0f", item.speed)} km/h"
                    b.tvFleetNextStop.text = "Next: ${item.nextStop.ifEmpty { "En Route" }} (${String.format(Locale.ENGLISH, "%.1f", item.distanceToNext)} km)"
                    b.tvFleetEta.text = "ETA: $etaMinutes min"
                    b.tvFleetAiEta.text = "AI: ~$aiEta min"
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
                    stops.joinToString(" <font color='#D1D5DB'>\u2192</font> ") { "<font color='#6B7280'>$it</font>" }
                }

                b.tvFleetStops.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            } else {
                b.tvFleetStops.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }
    }
}
