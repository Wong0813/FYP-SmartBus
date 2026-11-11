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
import com.upsi.smartbus.core.util.BusEtaCalculator
import com.upsi.smartbus.core.util.BusSpeedCalculator
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.core.util.FleetPriorityHelper
import com.upsi.smartbus.databinding.FragmentAdminDashboardBinding
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import com.upsi.smartbus.ui.adapter.FleetItem
import com.upsi.smartbus.ui.adapter.FleetMonitorAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }
    private val etaPredictor = com.upsi.smartbus.core.util.EtaPredictor()

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
        binding.tvCurrentDate.text = com.upsi.smartbus.core.util.DateTimeHelper.getFormattedCurrentDate()
        binding.tvDayType.text = com.upsi.smartbus.core.util.DateTimeHelper.getTodayScheduleType()
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

        android.util.Log.d("SMARTBUS_FLEET", "loadFleetData called. dayOfWeek=$dayOfWeek isWeekday=$isWeekday isSaturday=$isSaturday")

        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            android.util.Log.d("SMARTBUS_FLEET", "Routes loaded: total=${routes.size}")
            routes.forEach { android.util.Log.d("SMARTBUS_FLEET", "  Route: name=${it.name} scheduleType=${it.scheduleType}") }

            allFleetItems.clear()
            val canonical = AdminSortHelper.canonicalRoutes()
            val todayRoutes = when {
                isSaturday -> routes.filter { it.scheduleType.equals("SATURDAY", true) }
                isWeekday -> routes.filter { it.scheduleType.equals("WEEKDAY", true) }
                else -> routes // Show all routes even on Sunday so dashboard is never blank
            }.let { AdminSortHelper.sortRoutes(it) }

            android.util.Log.d("SMARTBUS_FLEET", "Today's routes: ${todayRoutes.size}")

            if (todayRoutes.isEmpty()) {
                // Fallback: always show all canonical routes so dashboard is never fully blank
                android.util.Log.w("SMARTBUS_FLEET", "No routes for today — showing all canonical routes as fallback")
                val allRoutes = if (isWeekday) {
                    AdminSortHelper.sortRoutes(routes.filter { it.scheduleType.equals("WEEKDAY", true) })
                        .ifEmpty { AdminSortHelper.canonicalRoutes().filter { it.scheduleType.equals("WEEKDAY", true) } }
                } else {
                    AdminSortHelper.canonicalRoutes()
                }
                allRoutes.forEach { route ->
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
            } else {
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
            }

            android.util.Log.d("SMARTBUS_FLEET", "allFleetItems built: ${allFleetItems.size}")

            // Load driver assignments from cache or Firestore
            val cachedUsers = com.upsi.smartbus.core.data.AppCache.getCachedUsers()
            if (cachedUsers != null) {
                val drivers = cachedUsers.filter { it.role.equals("DRIVER", true) }
                for (d in drivers) {
                    if (d.uid.startsWith("driver_laluan") || d.uid.startsWith("driver_shuttle")) continue
                    val assignedRoute = d.assignedRoute.ifEmpty { null } ?: continue
                    val idx = allFleetItems.indexOfFirst { it.route.name == assignedRoute }
                    if (idx >= 0) {
                        val item = allFleetItems[idx]
                        allFleetItems[idx] = item.copy(
                            driverName = d.name.ifEmpty { item.driverName },
                            driverUid = d.uid,
                            busId = d.assignedBus.ifEmpty { item.busId },
                            plateNumber = d.plateNumber.ifEmpty { item.plateNumber }
                        )
                    }
                }
                filterFleetList()
            } else {
                fun applyCanonicalFallback() {
                    val entries = com.upsi.smartbus.feature.admin.seeder.AccountSeeder.alignedEntries()
                    for (entry in entries) {
                        val idx = allFleetItems.indexOfFirst { it.route.name == entry.route.name }
                        if (idx >= 0) {
                            val item = allFleetItems[idx]
                            allFleetItems[idx] = item.copy(
                                driverName = entry.driver.driverName,
                                driverUid = com.upsi.smartbus.feature.admin.seeder.AccountSeeder.driverDocId(entry.number),
                                busId = entry.driver.busId,
                                plateNumber = entry.driver.plateNumber
                            )
                        }
                    }
                    filterFleetList()
                }

                db.collection("users").whereEqualTo("role", "DRIVER").get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null || snapshot == null || snapshot.isEmpty) {
                            applyCanonicalFallback()
                            return@addOnSuccessListener
                        }
                        val usersList = mutableListOf<com.upsi.smartbus.core.model.UserProfile>()
                        for (doc in snapshot.documents) {
                            doc.toObject(com.upsi.smartbus.core.model.UserProfile::class.java)?.let {
                                usersList.add(it.copy(uid = doc.id))
                            }
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
                        if (usersList.isNotEmpty()) {
                            com.upsi.smartbus.core.data.AppCache.putUsers(usersList)
                        }
                        filterFleetList()
                    }
                    .addOnFailureListener {
                        applyCanonicalFallback()
                    }
            }

            // Immediately initialize top stats so it renders instantly
            binding.tvTotalFleetCount.text = "${allFleetItems.size}"
            binding.tvStatOnlineCountSmall.text = "0"
            binding.tvStatRestingCount.text = "0"
            binding.tvStatOfflineCount.text = "${allFleetItems.size}"

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

                val now = System.currentTimeMillis()
                val STALE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes without GPS update = OFFLINE

                var online = 0
                var resting = 0
                var offline = 0

                allFleetItems.indices.forEach { i ->
                    val item = allFleetItems[i]
                    val bus = busMap[item.busId] ?: busMap[item.route.name]

                    val isStale = (bus == null) || (bus.lastUpdated > 0L && (now - bus.lastUpdated) > STALE_TIMEOUT_MS)
                    val effectiveStatus = if (isStale) "IDLE" else (bus?.status ?: "IDLE")

                    // FleetItem has var fields — assign directly instead of copy()
                    item.status = effectiveStatus
                    item.speed = if (isStale) 0.0 else (bus?.speed ?: 0.0)
                    item.nextStop = if (isStale) "" else (bus?.nextStop ?: "")
                    item.distanceToNext = if (isStale) 0.0 else (bus?.distanceToNext ?: 0.0)
                    item.lastUpdated = bus?.lastUpdated ?: 0L

                    if (item.isActiveDay && !isStale) {
                        when (effectiveStatus.lowercase()) {
                            "working" -> online++
                            "resting" -> resting++
                            else -> offline++
                        }
                    } else {
                        offline++
                    }
                }

                // Sort: Speeding first → Active → Resting → Offline (Fast pure Java priority comparator)
                allFleetItems.sortWith { a, b ->
                    FleetPriorityHelper.compareFleet(
                        a.isSpeeding, a.isActive, a.isResting, a.routeOrder,
                        b.isSpeeding, b.isActive, b.isResting, b.routeOrder
                    )
                }

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
        val listener = rtdbListener
        if (listener != null) {
            com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(listener)
        }
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
