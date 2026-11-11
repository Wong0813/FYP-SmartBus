package com.upsi.smartbus.feature.student.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.upsi.smartbus.core.data.RealtimeDbHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.databinding.FragmentBusScheduleTabBinding
import com.upsi.smartbus.ui.adapter.LegendAdapter
import com.upsi.smartbus.ui.adapter.UnifiedBusScheduleItem
import com.upsi.smartbus.ui.adapter.UnifiedScheduleAdapter

class BusScheduleTabFragment : Fragment() {

    private var _binding: FragmentBusScheduleTabBinding? = null
    private val binding get() = _binding!!

    private var scheduleType: String = "WEEKDAY"
    private lateinit var adapter: UnifiedScheduleAdapter

    private var baseRoutes: List<Route> = emptyList()
    private val liveBusesMap = mutableMapOf<String, Bus>() // routeName/busId -> Bus
    private var currentFilterQuery: String = ""
    private var rtdbListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleType = arguments?.getString(ARG_SCHEDULE_TYPE) ?: "WEEKDAY"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBusScheduleTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupLegend()
        loadRoutes()
        listenToLiveBuses()
    }

    private fun setupRecyclerView() {
        adapter = UnifiedScheduleAdapter(emptyList()) { item ->
            DriverDetailBottomSheet.newInstance(item)
                .show(childFragmentManager, "DriverDetailBottomSheet")
        }
        binding.rvBuses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBuses.adapter = adapter
    }

    private fun setupLegend() {
        val legendAdapter = LegendAdapter(RouteData.stops)
        binding.rvLegend.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLegend.adapter = legendAdapter
    }

    private fun loadRoutes() {
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            baseRoutes = if (scheduleType == "WEEKDAY") {
                routes.filter { it.scheduleType.equals("WEEKDAY", ignoreCase = true) }
            } else {
                routes.filter { it.scheduleType.equals("SATURDAY", ignoreCase = true) }
            }
            rebuildAndFilterList()
        }
    }

    private fun listenToLiveBuses() {
        rtdbListener?.let { RealtimeDbHelper.liveBuses.removeEventListener(it) }
        rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                liveBusesMap.clear()
                for (child in snapshot.children) {
                    val busId = child.key ?: continue
                    val status = child.child("status").getValue(String::class.java) ?: "IDLE"
                    val nextStop = child.child("nextStop").getValue(String::class.java) ?: ""
                    val routeName = child.child("routeName").getValue(String::class.java) ?: ""
                    val plateNumber = child.child("plateNumber").getValue(String::class.java) ?: busId

                    val bus = Bus(
                        id = busId,
                        routeName = routeName,
                        status = status,
                        nextStop = nextStop,
                        plateNumber = plateNumber
                    )
                    if (routeName.isNotEmpty()) {
                        liveBusesMap[routeName.lowercase()] = bus
                    }
                    liveBusesMap[busId.lowercase()] = bus
                }
                rebuildAndFilterList()
            }

            override fun onCancelled(error: DatabaseError) {
                // Continue with static data
            }
        }
        RealtimeDbHelper.liveBuses.addValueEventListener(rtdbListener!!)
    }

    fun applyFilter(query: String) {
        currentFilterQuery = query
        rebuildAndFilterList()
    }

    private fun rebuildAndFilterList() {
        if (_binding == null) return

        val allItems = baseRoutes.map { route ->
            val liveBus = liveBusesMap[route.name.lowercase()]
            val isActive = liveBus != null &&
                    (liveBus.status.equals("WORKING", true) || liveBus.status.equals("RESTING", true))
            val driver = RouteData.getDriverForRoute(route.name)
            UnifiedBusScheduleItem(
                route = route,
                liveBus = liveBus,
                driver = driver,
                isActive = isActive
            )
        }

        val filtered = if (currentFilterQuery.isBlank()) {
            allItems
        } else {
            val q = currentFilterQuery.trim()
            allItems.filter { item ->
                item.route.name.contains(q, ignoreCase = true) ||
                item.route.stops.any { it.contains(q, ignoreCase = true) } ||
                item.driver.driverName.contains(q, ignoreCase = true) ||
                item.driver.plateNumber.contains(q, ignoreCase = true) ||
                item.driver.phone.contains(q, ignoreCase = true)
            }
        }

        adapter.updateList(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        rtdbListener?.let { RealtimeDbHelper.liveBuses.removeEventListener(it) }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SCHEDULE_TYPE = "schedule_type"

        fun newInstance(scheduleType: String): BusScheduleTabFragment {
            return BusScheduleTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SCHEDULE_TYPE, scheduleType)
                }
            }
        }
    }
}
