package com.upsi.smartbus.feature.admin.dashboard

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
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import com.upsi.smartbus.feature.admin.seeder.AdminUiHelper
import java.util.Calendar

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
        var status: String = "IDLE",
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
        loadScheduleData()
        listenToBusStatuses()
    }

    private fun setupDateHeader() {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val malayDays = arrayOf("", "Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu")
        val malayMonths = arrayOf(
            "Januari", "Februari", "Mac", "April", "Mei", "Jun",
            "Julai", "Ogos", "September", "Oktober", "November", "Disember"
        )
        binding.tvCurrentDate.text = "${malayDays[dayOfWeek]}, ${cal.get(Calendar.DAY_OF_MONTH)} ${malayMonths[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
        when (dayOfWeek) {
            Calendar.SUNDAY -> {
                binding.tvDayType.text = "TIADA PERKHIDMATAN"
                binding.tvDayType.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_offline))
            }
            Calendar.SATURDAY -> {
                binding.tvDayType.text = "SABTU"
                binding.tvDayType.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_resting))
            }
            else -> {
                binding.tvDayType.text = "HARI BEKERJA"
                binding.tvDayType.setTextColor(ContextCompat.getColor(requireContext(), R.color.crimson_primary))
            }
        }
    }

    private fun setupScheduleGrid() {
        scheduleAdapter = ScheduleAdapter(scheduleItems) { showEditDriverDialog(it) }
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
                    AdminUiHelper.expandRecyclerView(binding.rvScheduleGrid)
                }

            scheduleAdapter.notifyDataSetChanged()
            AdminUiHelper.expandRecyclerView(binding.rvScheduleGrid)
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
            Toast.makeText(requireContext(), "No driver for ${item.route.name}. Sync in Manage Accounts.", Toast.LENGTH_LONG).show()
            return
        }
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val etName = EditText(ctx).apply { hint = "Driver Name"; setText(item.driverName) }
        val etBus = EditText(ctx).apply { hint = "Bus ID"; setText(item.busId) }
        layout.addView(etName); layout.addView(etBus)
        val routes = RouteRepository.getCachedRoutes().map { it.name }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("${AdminSortHelper.routeNumberLabel(item.route)} ${item.route.name}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                db.collection("users").document(item.driverUid).update(mapOf(
                    "name" to etName.text.toString().trim(),
                    "assignedBus" to etBus.text.toString().trim()
                )).addOnSuccessListener { loadScheduleData() }
            }
            .setNeutralButton("Change Route") { _, _ ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Reassign")
                    .setItems(routes.toTypedArray()) { _, w ->
                        db.collection("users").document(item.driverUid)
                            .update("assignedRoute", routes[w])
                            .addOnSuccessListener { loadScheduleData() }
                    }.show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDestroyView() {
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    class ScheduleAdapter(
        private val items: List<ScheduleItem>,
        private val onEdit: (ScheduleItem) -> Unit
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
            b.tvScheduleDriverName.text = "🚌 ${item.driverName}"
            b.tvScheduleBusInfo.text = "${item.busId} • ${item.plateNumber}"
            b.tvScheduleStops.text = item.route.stops.joinToString(" ➔ ")
            if (!item.isActiveDay) {
                holder.itemView.setBackgroundResource(R.drawable.bg_schedule_card_inactive)
                b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_gray)
                b.statusDot.setBackgroundResource(R.drawable.dot_gray)
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_schedule_card_active)
                when (item.status.lowercase()) {
                    "working" -> { b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_green); b.statusDot.setBackgroundResource(R.drawable.dot_green) }
                    "resting" -> { b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_orange); b.statusDot.setBackgroundResource(R.drawable.dot_orange) }
                    else -> { b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_gray); b.statusDot.setBackgroundResource(R.drawable.dot_gray) }
                }
            }
            holder.itemView.setOnClickListener { onEdit(item) }
        }
        override fun getItemCount() = items.size
    }
}
