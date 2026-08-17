package com.upsi.smartbus.feature.admin.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.databinding.FragmentAdminDashboardBinding
import com.upsi.smartbus.databinding.ItemScheduleCardBinding
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    data class ScheduleItem(
        val route: Route,
        val routeOrder: Int,
        val driverName: String,
        val driverUid: String = "",
        val driverNumber: Int = 0,
        val busId: String,
        val plateNumber: String,
        var status: String = "OFFLINE",
        val isActiveDay: Boolean = true
    )

    private val scheduleItems = mutableListOf<ScheduleItem>()
    private lateinit var scheduleAdapter: ScheduleAdapter
    private var busListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDateHeader()
        setupScheduleGrid()
        setupStatCardClicks()
        loadScheduleData()
        listenToBusStatuses()
    }

    private fun setupStatCardClicks() {
        binding.cardStatOnline.setOnClickListener {
            filterAndShowStatus("working", "Active Buses on Duty")
        }
        binding.cardStatResting.setOnClickListener {
            filterAndShowStatus("resting", "Buses on Break")
        }
        binding.cardStatOffline.setOnClickListener {
            filterAndShowStatus("idle", "Offline / Idle Buses")
        }
    }

    private fun filterAndShowStatus(status: String, title: String) {
        val filtered = scheduleItems.filter {
            it.status.lowercase() == status.lowercase()
        }
        val names = filtered.map { "${it.route.name} — ${it.driverName}" }
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), "No buses currently in this status.", Toast.LENGTH_SHORT).show()
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(names.toTypedArray(), null)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun setupDateHeader() {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
        binding.tvCurrentDate.text = sdf.format(cal.time)

        // Always ensure high-contrast white text on the translucent badge
        binding.tvDayType.setTextColor(Color.WHITE)

        when (dayOfWeek) {
            Calendar.SUNDAY -> {
                binding.tvDayType.text = "NO SERVICE"
                binding.tvDaySubtitle.text = "No bus service operating today"
            }
            Calendar.SATURDAY -> {
                binding.tvDayType.text = "SATURDAY"
                binding.tvDaySubtitle.text = "Saturday schedule active — Routes 9–18"
            }
            else -> {
                binding.tvDayType.text = "WEEKDAY"
                binding.tvDaySubtitle.text = "Active service schedule — Routes 1–8 & Shuttle"
            }
        }
    }

    private fun setupScheduleGrid() {
        scheduleAdapter = ScheduleAdapter(
            items = scheduleItems,
            onClick = { item ->
                // Direct jump to Live Map focused on this specific route!
                val adminAct = activity as? AdminActivity
                if (adminAct != null) {
                    adminAct.navigateToLiveMap(item.route.name)
                }
            },
            onLongClick = { item ->
                // Long press to edit driver assignment
                showEditDriverDialog(item)
            }
        )
        binding.rvScheduleGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvScheduleGrid.adapter = scheduleAdapter
    }

    private fun loadScheduleData() {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isSaturday = dayOfWeek == Calendar.SATURDAY

        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            scheduleItems.clear()
            val canonical = AdminSortHelper.canonicalRoutes()
            val todayRoutes = when {
                isSaturday -> routes.filter { it.scheduleType.equals("SATURDAY", true) }
                isWeekday -> routes.filter { it.scheduleType.equals("WEEKDAY", true) }
                else -> emptyList()
            }.let { AdminSortHelper.sortRoutes(it) }

            todayRoutes.forEach { route ->
                val order = canonical.indexOfFirst { it.name == route.name }.let { if (it >= 0) it + 1 else AdminSortHelper.routeOrderKey(route) }
                scheduleItems.add(
                    ScheduleItem(
                        route = route,
                        routeOrder = order,
                        driverName = "Unassigned",
                        busId = "—",
                        plateNumber = "—",
                        isActiveDay = dayOfWeek != Calendar.SUNDAY
                    )
                )
            }

            db.collection("users").whereEqualTo("role", "DRIVER").get()
                .addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    for (doc in snapshot.documents) {
                        if (doc.id.startsWith("driver_laluan") || doc.id.startsWith("driver_shuttle")) continue
                        val assignedRoute = doc.getString("assignedRoute") ?: continue
                        val idx = scheduleItems.indexOfFirst { it.route.name == assignedRoute }
                        if (idx >= 0) {
                            val item = scheduleItems[idx]
                            scheduleItems[idx] = item.copy(
                                driverName = doc.getString("name") ?: item.driverName,
                                driverUid = doc.id,
                                driverNumber = doc.getLong("driverNumber")?.toInt() ?: item.routeOrder,
                                busId = doc.getString("assignedBus").orEmpty().ifEmpty { item.busId },
                                plateNumber = doc.getString("plateNumber").orEmpty().ifEmpty { item.plateNumber }
                            )
                        }
                    }
                    scheduleAdapter.notifyDataSetChanged()
                }

            scheduleAdapter.notifyDataSetChanged()
        }
    }

    private fun listenToBusStatuses() {
        busListener = db.collection("buses").addSnapshotListener { snapshot, error ->
            if (_binding == null || error != null || snapshot == null) return@addSnapshotListener
            val busMap = mutableMapOf<String, Bus>()
            snapshot.documents.forEach { doc ->
                doc.toObject(Bus::class.java)?.let { bus ->
                    busMap[bus.id] = bus
                    busMap[bus.routeName] = bus
                }
            }
            var online = 0; var resting = 0; var offline = 0
            scheduleItems.indices.forEach { i ->
                val item = scheduleItems[i]
                val bus = busMap[item.busId] ?: busMap[item.route.name]
                scheduleItems[i] = item.copy(status = bus?.status ?: "IDLE")
                if (item.isActiveDay) when ((bus?.status ?: "IDLE").lowercase()) {
                    "working" -> online++
                    "resting" -> resting++
                    else -> offline++
                } else offline++
            }
            binding.tvStatOnlineCount.text = "$online"
            binding.tvStatRestingCount.text = "$resting"
            binding.tvStatOfflineCount.text = "$offline"
            scheduleAdapter.notifyDataSetChanged()
        }
    }

    private fun showEditDriverDialog(item: ScheduleItem) {
        if (item.driverUid.isEmpty()) {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "No driver assigned for ${item.route.name}. Sync in Manage Accounts.", 4000)
                .show()
            return
        }
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 40, 56, 24)
        }

        // Route label
        val tvRoute = android.widget.TextView(ctx).apply {
            text = "${AdminSortHelper.routeNumberLabel(item.route)} ${item.route.name}"
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 11f
            setPadding(0, 0, 0, 16)
        }

        val etName = EditText(ctx).apply {
            hint = "Driver Name"
            setText(item.driverName)
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(32, 24, 32, 24)
        }
        val etBus = EditText(ctx).apply {
            hint = "Bus ID (e.g. BUS-001)"
            setText(if (item.busId == "—") "" else item.busId)
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }

        layout.addView(tvRoute)
        layout.addView(etName)
        layout.addView(etBus)

        val routes = RouteRepository.getCachedRoutes().map { it.name }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Edit Driver Assignment")
            .setView(layout)
            .setPositiveButton("Save") { dlg, _ ->
                val newName = etName.text.toString().trim()
                val newBus = etBus.text.toString().trim()
                if (newName.isEmpty()) {
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "Driver name cannot be empty", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }
                db.collection("users").document(item.driverUid).update(mapOf(
                    "name" to newName,
                    "assignedBus" to newBus
                )).addOnSuccessListener {
                    loadScheduleData()
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "Driver assignment updated successfully", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show()
                }.addOnFailureListener {
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "Failed to save: ${it.message}", 4000)
                        .show()
                }
            }
            .setNeutralButton("Change Route") { _, _ ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Change Route for ${item.driverName}")
                    .setItems(routes.toTypedArray()) { _, w ->
                        db.collection("users").document(item.driverUid)
                            .update("assignedRoute", routes[w])
                            .addOnSuccessListener {
                                loadScheduleData()
                                com.google.android.material.snackbar.Snackbar
                                    .make(binding.root, "Route changed to: ${routes[w]}", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    .show()
                            }
                    }.show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    class ScheduleAdapter(
        private val items: List<ScheduleItem>,
        private val onClick: (ScheduleItem) -> Unit,
        private val onLongClick: (ScheduleItem) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.VH>() {
        inner class VH(val binding: ItemScheduleCardBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemScheduleCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            val b = holder.binding
            b.tvScheduleRouteName.text = item.route.name
            b.tvScheduleRouteDesc.text = "${AdminSortHelper.routeNumberLabel(item.route)} • Driver ${String.format("%02d", item.driverNumber.coerceAtLeast(item.routeOrder))}"
            b.tvScheduleDriverName.text = item.driverName.ifEmpty { "Unassigned" }
            b.tvScheduleBusInfo.text = if (item.busId == "—") "No bus assigned" else "${item.busId}  •  ${item.plateNumber}"
            b.tvScheduleStops.text = item.route.stops.joinToString(" ➔ ")

            if (!item.isActiveDay) {
                b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_gray)
                b.statusDot.setBackgroundResource(R.drawable.dot_gray)
                b.tvStatusLabel.text = "OFFLINE"
                b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_offline))
                b.llStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
            } else {
                when (item.status.lowercase()) {
                    "working" -> {
                        b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_green)
                        b.statusDot.setBackgroundResource(R.drawable.dot_green)
                        b.tvStatusLabel.text = "ACTIVE"
                        b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_moving))
                        b.llStatusPill.setBackgroundResource(R.drawable.bg_status_moving)
                    }
                    "resting" -> {
                        b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_orange)
                        b.statusDot.setBackgroundResource(R.drawable.dot_orange)
                        b.tvStatusLabel.text = "RESTING"
                        b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_resting))
                        b.llStatusPill.setBackgroundResource(R.drawable.bg_status_resting)
                    }
                    else -> {
                        b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_gray)
                        b.statusDot.setBackgroundResource(R.drawable.dot_gray)
                        b.tvStatusLabel.text = "OFFLINE"
                        b.tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.status_offline))
                        b.llStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
                    }
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onLongClick(item); true }
        }
        override fun getItemCount() = items.size
    }
}
