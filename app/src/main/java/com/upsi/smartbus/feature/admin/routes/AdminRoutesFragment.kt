package com.upsi.smartbus.feature.admin.routes

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.databinding.FragmentAdminRoutesBinding
import com.upsi.smartbus.databinding.ItemAccountSectionHeaderBinding
import com.upsi.smartbus.databinding.ItemRouteCardBinding
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper

class AdminRoutesFragment : Fragment() {

    private var _binding: FragmentAdminRoutesBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private val allRoutes = mutableListOf<Route>()
    private val displayItems = mutableListOf<RouteListItem>()
    private lateinit var routeAdapter: RouteSectionAdapter
    private val selectedStops = mutableListOf<String>()
    private var tabFilter = "ALL"
    private var searchQuery = ""

    sealed class RouteListItem {
        data class Header(val title: String, val count: Int) : RouteListItem()
        data class RouteItem(val route: Route, val order: Int) : RouteListItem()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminRoutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnHeroDrawer.setOnClickListener {
            (activity as? AdminActivity)?.openDrawer()
        }
        setupStopsSpinner()
        setupScheduleTypeSpinner()
        setupAddStop()
        setupSave()
        setupSearch()
        setupTabs()
        setupToggleForm()
        setupRecyclerView()
        loadRoutesData()
    }

    private fun setupScheduleTypeSpinner() {
        val types = listOf("Weekday (Mon-Fri)", "Weekend / Saturday")
        binding.spinnerScheduleType.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, types
        )
    }

    private fun setupSearch() {
        binding.etSearchRoutes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty().lowercase()
                binding.btnClearRouteSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                rebuildList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearRouteSearch.setOnClickListener {
            binding.etSearchRoutes.setText("")
        }
    }

    private fun setupToggleForm() {
        binding.btnToggleRouteForm.setOnClickListener {
            val show = binding.llRouteForm.visibility != View.VISIBLE
            binding.llRouteForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.tvNewRouteBtnText.text = if (show) "✕ Close Form" else "+ New Route"
        }
    }


    private fun setupTabs() {
        val tabs = listOf(
            binding.tabAllRoutes to "ALL",
            binding.tabWeekday to "WEEKDAY",
            binding.tabSaturday to "SATURDAY"
        )
        tabs.forEach { (view, filter) ->
            view.setOnClickListener {
                tabFilter = filter
                updateTabStyles()
                if (filter == "SATURDAY") {
                    binding.spinnerScheduleType.setSelection(1)
                } else if (filter == "WEEKDAY") {
                    binding.spinnerScheduleType.setSelection(0)
                }
                rebuildList()
            }
        }
    }

    private fun updateTabStyles() {
        val tabs = listOf(
            binding.tabAllRoutes to "ALL",
            binding.tabWeekday to "WEEKDAY",
            binding.tabSaturday to "SATURDAY"
        )
        tabs.forEach { (view, filter) ->
            val selected = tabFilter == filter
            if (selected) {
                view.setBackgroundResource(R.drawable.bg_filter_tab_active)
                view.setTextColor(Color.WHITE)
                view.setTypeface(null, Typeface.BOLD)
            } else {
                view.setBackgroundResource(R.drawable.bg_filter_tab_inactive)
                view.setTextColor(Color.parseColor("#6B7280"))
                view.setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun setupStopsSpinner() {
        val allStops = RouteData.stops.map { "${it.abbreviation} - ${it.fullName}" }
        binding.spinnerStops.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, allStops
        )
    }

    private fun setupAddStop() {
        binding.btnAddStop.setOnClickListener {
            val stop = RouteData.stops[binding.spinnerStops.selectedItemPosition].abbreviation
            selectedStops.add(stop)
            renderStopChips()
        }
    }

    private fun renderStopChips() {
        binding.llStopChips.removeAllViews()
        val ctx = requireContext()
        selectedStops.forEachIndexed { i, stop ->
            val tv = TextView(ctx).apply {
                text = "${i + 1}. $stop \u2715"
                setBackgroundResource(R.drawable.bg_role_badge)
                setPadding(24, 12, 24, 12)
                setTextColor(ContextCompat.getColor(ctx, R.color.crimson_primary))
                textSize = 12f
                setOnClickListener {
                    selectedStops.removeAt(i)
                    renderStopChips()
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 16
                layoutParams = lp
            }
            binding.llStopChips.addView(tv)
        }
    }

    private fun setupSave() {
        binding.btnSaveRoute.setOnClickListener {
            val name = binding.etRouteName.text.toString().trim()
            if (name.isEmpty() || selectedStops.isEmpty()) {
                Toast.makeText(requireContext(), "Enter name and at least 1 stop", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val short = name.split(" ").take(2).joinToString("") { it.take(1) }
            val scheduleType = if (binding.spinnerScheduleType.selectedItemPosition == 1) "SATURDAY" else "WEEKDAY"
            val route = Route(name = name, shortName = short, stops = selectedStops.toList(), scheduleType = scheduleType)
            db.collection("routes").add(route).addOnSuccessListener {
                Toast.makeText(requireContext(), "Route created", Toast.LENGTH_SHORT).show()
                binding.etRouteName.text?.clear()
                selectedStops.clear()
                renderStopChips()
                binding.llRouteForm.visibility = View.GONE
                binding.tvNewRouteBtnText.text = "+ New Route"
                RouteRepository.invalidateCache()
                loadRoutesData()
            }
        }
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteSectionAdapter(
            displayItems,
            onEdit = { showEdit(it) },
            onDelete = { deleteRoute(it) }
        )
        binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutes.adapter = routeAdapter
    }

    private fun loadRoutesData() {
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            allRoutes.clear()
            allRoutes.addAll(routes)

            val weekday = allRoutes.count { it.scheduleType.equals("WEEKDAY", true) }
            val saturday = allRoutes.count { it.scheduleType.equals("SATURDAY", true) }
            val shuttle = allRoutes.count { it.name.contains("Shuttle", true) }

            binding.tvRouteTotal.text = "${allRoutes.size}"
            binding.tvRouteWeekdayCount.text = "$weekday"
            binding.tvRouteSaturdayCount.text = "$saturday"
            binding.tvRouteShuttleCount.text = "$shuttle"

            rebuildList()
        }
    }

    private fun rebuildList() {
        displayItems.clear()
        val sorted = AdminSortHelper.sortRoutes(allRoutes)
        val canonical = AdminSortHelper.canonicalRoutes()

        fun matchesSearch(r: Route): Boolean {
            if (searchQuery.isEmpty()) return true
            return r.name.lowercase().contains(searchQuery) ||
                    r.shortName.lowercase().contains(searchQuery) ||
                    r.scheduleType.lowercase().contains(searchQuery) ||
                    r.stops.any { it.lowercase().contains(searchQuery) }
        }

        val filtered = sorted.filter { matchesSearch(it) }

        val weekdayRoutes = filtered.filter { it.scheduleType.equals("WEEKDAY", true) }
        val saturdayRoutes = filtered.filter { it.scheduleType.equals("SATURDAY", true) }

        when (tabFilter) {
            "WEEKDAY" -> {
                if (weekdayRoutes.isNotEmpty()) {
                    displayItems.add(RouteListItem.Header("WEEKDAY ROUTES (LALUAN 1–8)", weekdayRoutes.size))
                    weekdayRoutes.forEach { r ->
                        val order = canonical.indexOfFirst { it.name == r.name }.let { if (it >= 0) it + 1 else AdminSortHelper.routeOrderKey(r) }
                        displayItems.add(RouteListItem.RouteItem(r, order))
                    }
                }
            }
            "SATURDAY" -> {
                if (saturdayRoutes.isNotEmpty()) {
                    displayItems.add(RouteListItem.Header("WEEKEND ROUTES (LALUAN 9–18)", saturdayRoutes.size))
                    saturdayRoutes.forEach { r ->
                        val order = canonical.indexOfFirst { it.name == r.name }.let { if (it >= 0) it + 1 else AdminSortHelper.routeOrderKey(r) }
                        displayItems.add(RouteListItem.RouteItem(r, order))
                    }
                }
            }
            else -> {
                if (weekdayRoutes.isNotEmpty()) {
                    displayItems.add(RouteListItem.Header("WEEKDAY ROUTES", weekdayRoutes.size))
                    weekdayRoutes.forEach { r ->
                        val order = canonical.indexOfFirst { it.name == r.name }.let { if (it >= 0) it + 1 else AdminSortHelper.routeOrderKey(r) }
                        displayItems.add(RouteListItem.RouteItem(r, order))
                    }
                }
                if (saturdayRoutes.isNotEmpty()) {
                    displayItems.add(RouteListItem.Header("WEEKEND ROUTES", saturdayRoutes.size))
                    saturdayRoutes.forEach { r ->
                        val order = canonical.indexOfFirst { it.name == r.name }.let { if (it >= 0) it + 1 else AdminSortHelper.routeOrderKey(r) }
                        displayItems.add(RouteListItem.RouteItem(r, order))
                    }
                }
            }
        }

        val isEmpty = displayItems.isEmpty()
        binding.llEmptyRoutes.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvRoutes.visibility = if (isEmpty) View.GONE else View.VISIBLE

        routeAdapter.notifyDataSetChanged()
    }

    private fun showEdit(route: Route) {
        val ctx = requireContext()
        val et = android.widget.EditText(ctx).apply {
            hint = "Route Name"
            setText(route.name)
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(32, 24, 32, 24)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Edit Route")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isNotEmpty()) {
                    // Immediately update local list
                    val idx = allRoutes.indexOfFirst { it.id == route.id }
                    if (idx >= 0) {
                        allRoutes[idx] = allRoutes[idx].copy(name = newName)
                    }
                    RouteRepository.invalidateCache()
                    rebuildList()
                    Toast.makeText(ctx, "Route updated", Toast.LENGTH_SHORT).show()
                    // Persist to Firestore in background
                    db.collection("routes").document(route.id).update("name", newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRoute(route: Route) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${route.name}")
            .setMessage("Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                // Immediately update local list
                allRoutes.removeAll { it.id == route.id }
                RouteRepository.invalidateCache()
                rebuildList()
                // Update stats
                val weekday = allRoutes.count { it.scheduleType.equals("WEEKDAY", true) }
                val saturday = allRoutes.count { it.scheduleType.equals("SATURDAY", true) }
                val shuttle = allRoutes.count { it.name.contains("Shuttle", true) }
                binding.tvRouteTotal.text = "${allRoutes.size}"
                binding.tvRouteWeekdayCount.text = "$weekday"
                binding.tvRouteSaturdayCount.text = "$saturday"
                binding.tvRouteShuttleCount.text = "$shuttle"
                Toast.makeText(requireContext(), "${route.name} deleted", Toast.LENGTH_SHORT).show()
                // Persist to Firestore in background
                db.collection("routes").document(route.id).delete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    class RouteSectionAdapter(
        private val items: List<RouteListItem>,
        private val onEdit: (Route) -> Unit,
        private val onDelete: (Route) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_ROUTE = 1
        }

        inner class HeaderVH(val binding: ItemAccountSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        inner class RouteVH(val binding: ItemRouteCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(pos: Int) =
            if (items[pos] is RouteListItem.Header) TYPE_HEADER else TYPE_ROUTE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(ItemAccountSectionHeaderBinding.inflate(inf, parent, false))
            } else {
                RouteVH(ItemRouteCardBinding.inflate(inf, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            when (val item = items[pos]) {
                is RouteListItem.Header -> {
                    val hb = (holder as HeaderVH).binding
                    hb.tvSectionTitle.text = item.title
                    hb.tvSectionCount.text = "${item.count}"
                }
                is RouteListItem.RouteItem -> {
                    val b = (holder as RouteVH).binding
                    val r = item.route
                    b.tvRouteName.text = r.name
                    b.tvStopChain.text = r.stops.joinToString(" → ")
                    b.btnEditRoute.setOnClickListener { onEdit(r) }
                    b.btnDeleteRoute.setOnClickListener { onDelete(r) }
                }
            }
        }

        override fun getItemCount() = items.size
    }
}

