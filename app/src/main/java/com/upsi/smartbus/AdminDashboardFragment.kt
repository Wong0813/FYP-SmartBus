package com.upsi.smartbus

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
import com.upsi.smartbus.databinding.FragmentAdminDashboardBinding
import com.upsi.smartbus.databinding.ItemScheduleCardBinding
import com.upsi.smartbus.model.Bus
import com.upsi.smartbus.model.Route
import com.upsi.smartbus.model.RouteData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }

    data class ScheduleItem(
        val route: Route,
        val driverName: String,
        val busId: String,
        val plateNumber: String,
        var status: String = "IDLE",
        val isActiveDay: Boolean = true
    )

    private val scheduleItems = mutableListOf<ScheduleItem>()
    private lateinit var scheduleAdapter: ScheduleAdapter
    private var busListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        // Format: "Jumaat, 8 Ogos 2026"
        val malayDays = arrayOf("", "Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu")
        val malayMonths = arrayOf(
            "Januari", "Februari", "Mac", "April", "Mei", "Jun",
            "Julai", "Ogos", "September", "Oktober", "November", "Disember"
        )
        val dayName = malayDays.getOrElse(dayOfWeek) { "" }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = malayMonths.getOrElse(cal.get(Calendar.MONTH)) { "" }
        val year = cal.get(Calendar.YEAR)

        binding.tvCurrentDate.text = "📅 $dayName, $day $month $year"

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
        scheduleAdapter = ScheduleAdapter(scheduleItems) { item ->
            showEditDriverDialog(item)
        }
        binding.rvScheduleGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvScheduleGrid.adapter = scheduleAdapter
    }

    private fun loadScheduleData() {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        scheduleItems.clear()

        val isWeekday = dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
        val isSaturday = dayOfWeek == Calendar.SATURDAY
        val isSunday = dayOfWeek == Calendar.SUNDAY

        // Weekday routes
        for (route in RouteData.weekdayRoutes) {
            val driver = RouteData.getDriverForRoute(route.name)
            scheduleItems.add(
                ScheduleItem(
                    route = route,
                    driverName = driver.driverName,
                    busId = driver.busId,
                    plateNumber = driver.plateNumber,
                    status = "IDLE",
                    isActiveDay = isWeekday
                )
            )
        }

        // Saturday routes
        for (route in RouteData.saturdayRoutes) {
            val driver = RouteData.getDriverForRoute(route.name)
            scheduleItems.add(
                ScheduleItem(
                    route = route,
                    driverName = driver.driverName,
                    busId = driver.busId,
                    plateNumber = driver.plateNumber,
                    status = "IDLE",
                    isActiveDay = isSaturday
                )
            )
        }

        // Also load driver assignments from Firestore to get up-to-date names
        db.collection("users").whereEqualTo("role", "DRIVER").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                for (doc in snapshot.documents) {
                    val assignedRoute = doc.getString("assignedRoute") ?: continue
                    val driverName = doc.getString("name") ?: continue
                    val busId = doc.getString("assignedBus") ?: ""
                    val plateNumber = doc.getString("plateNumber") ?: ""

                    val item = scheduleItems.find { it.route.name == assignedRoute }
                    if (item != null) {
                        val index = scheduleItems.indexOf(item)
                        scheduleItems[index] = item.copy(
                            driverName = driverName,
                            busId = busId.ifEmpty { item.busId },
                            plateNumber = plateNumber.ifEmpty { item.plateNumber }
                        )
                    }
                }
                scheduleAdapter.notifyDataSetChanged()
            }

        scheduleAdapter.notifyDataSetChanged()
    }

    private fun listenToBusStatuses() {
        busListener = db.collection("buses")
            .addSnapshotListener { snapshot, error ->
                if (_binding == null || error != null || snapshot == null) return@addSnapshotListener

                val busMap = mutableMapOf<String, Bus>()
                for (doc in snapshot.documents) {
                    val bus = doc.toObject(Bus::class.java) ?: continue
                    busMap[bus.id] = bus
                    // Also try matching by routeName
                    busMap[bus.routeName] = bus
                }

                var onlineCount = 0
                var restingCount = 0
                var offlineCount = 0

                for (i in scheduleItems.indices) {
                    val item = scheduleItems[i]
                    val bus = busMap[item.busId] ?: busMap[item.route.name]
                    val newStatus = bus?.status ?: "IDLE"
                    scheduleItems[i] = item.copy(status = newStatus)

                    if (item.isActiveDay) {
                        when (newStatus.lowercase()) {
                            "working" -> onlineCount++
                            "resting" -> restingCount++
                            else -> offlineCount++
                        }
                    } else {
                        offlineCount++
                    }
                }

                binding.tvStatOnlineCount.text = "$onlineCount"
                binding.tvStatRestingCount.text = "$restingCount"
                binding.tvStatOfflineCount.text = "$offlineCount"

                scheduleAdapter.notifyDataSetChanged()
            }
    }

    private fun showEditDriverDialog(item: ScheduleItem) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etName = EditText(ctx).apply {
            hint = "Driver Name"
            setText(item.driverName)
        }
        layout.addView(etName)

        val routesList = RouteData.getAllRoutes().map { it.name }
        val routeItems: Array<CharSequence> = routesList.map { it as CharSequence }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("✏️ Edit ${item.route.name}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateDriverInFirestore(item, newName)
                }
            }
            .setNeutralButton("Change Route") { _, _ ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Reassign Driver")
                    .setItems(routeItems) { _, which ->
                        reassignDriverRoute(item, routesList[which])
                    }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDriverInFirestore(item: ScheduleItem, newName: String) {
        val docId = "driver_${item.route.name.replace(" ", "_").lowercase()}"
        db.collection("users").document(docId)
            .update("name", newName)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Updated: $newName", Toast.LENGTH_SHORT).show()
                loadScheduleData()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update", Toast.LENGTH_SHORT).show()
            }
    }

    private fun reassignDriverRoute(item: ScheduleItem, newRoute: String) {
        val docId = "driver_${item.route.name.replace(" ", "_").lowercase()}"
        db.collection("users").document(docId)
            .update("assignedRoute", newRoute)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Reassigned to $newRoute", Toast.LENGTH_SHORT).show()
                loadScheduleData()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        busListener?.remove()
        _binding = null
    }

    // ════════════════════════════════════════════════════════════════════
    // SCHEDULE GRID ADAPTER
    // ════════════════════════════════════════════════════════════════════

    class ScheduleAdapter(
        private val items: List<ScheduleItem>,
        private val onEdit: (ScheduleItem) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.VH>() {

        inner class VH(val binding: ItemScheduleCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemScheduleCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            val b = holder.binding

            // Route info
            b.tvScheduleRouteName.text = item.route.name
            b.tvScheduleRouteDesc.text = item.route.shortName
            b.tvScheduleDriverName.text = "🚌 ${item.driverName}"
            b.tvScheduleBusInfo.text = "${item.busId} • ${item.plateNumber}"
            b.tvScheduleStops.text = item.route.stops.joinToString(" ➔ ")

            if (!item.isActiveDay) {
                // Inactive day — gray everything
                holder.itemView.setBackgroundResource(R.drawable.bg_schedule_card_inactive)
                b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_gray)
                b.statusDot.setBackgroundResource(R.drawable.dot_gray)
                b.tvScheduleRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.status_offline))
                b.tvScheduleDriverName.setTextColor(ContextCompat.getColor(ctx, R.color.status_offline))
            } else {
                // Active day — color by status
                holder.itemView.setBackgroundResource(R.drawable.bg_schedule_card_active)
                b.tvScheduleRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                b.tvScheduleDriverName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))

                when (item.status.lowercase()) {
                    "working" -> {
                        b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_green)
                        b.statusDot.setBackgroundResource(R.drawable.dot_green)
                    }
                    "resting" -> {
                        b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_orange)
                        b.statusDot.setBackgroundResource(R.drawable.dot_orange)
                    }
                    else -> {
                        b.statusBar.setBackgroundResource(R.drawable.bg_status_bar_gray)
                        b.statusDot.setBackgroundResource(R.drawable.dot_gray)
                    }
                }
            }

            holder.itemView.setOnClickListener { onEdit(item) }
        }

        override fun getItemCount() = items.size
    }
}
