package com.upsi.smartbus.feature.admin.routes

import android.os.Bundle
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
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.databinding.FragmentAdminRoutesBinding
import com.upsi.smartbus.databinding.ItemAccountSectionHeaderBinding
import com.upsi.smartbus.databinding.ItemRouteCardBinding
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import com.upsi.smartbus.feature.admin.seeder.AdminUiHelper

class AdminRoutesFragment : Fragment() {

    private var _binding: FragmentAdminRoutesBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private val allRoutes = mutableListOf<Route>()
    private val displayItems = mutableListOf<RouteListItem>()
    private lateinit var routeAdapter: RouteSectionAdapter
    private var routesListener: ListenerRegistration? = null
    private val selectedStops = mutableListOf<String>()
    private var tabFilter = "ALL"

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
        setupStopsSpinner()
        setupAddStop()
        setupSave()
        setupTabs()
        setupSync()
        setupToggleForm()
        setupRecyclerView()
        RouteRepository.loadRoutes { listenToRoutes() }
    }

    private fun setupToggleForm() {
        binding.btnToggleRouteForm.setOnClickListener {
            val show = binding.llRouteForm.visibility != View.VISIBLE
            binding.llRouteForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnToggleRouteForm.text = if (show) "✕ Close" else "+ New Route"
        }
    }

    private fun setupSync() {
        binding.btnSyncRoutes.setOnClickListener {
            binding.btnSyncRoutes.isEnabled = false
            AccountSeeder.seedOfficialRoutes {
                if (_binding == null) return@seedOfficialRoutes
                RouteRepository.invalidateCache()
                RouteRepository.loadRoutes {
                    binding.btnSyncRoutes.isEnabled = true
                    Toast.makeText(requireContext(), "20 Laluan synced to Firestore", Toast.LENGTH_LONG).show()
                }
            }
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
                rebuildList()
            }
        }
    }

    private fun updateTabStyles() {
        val selectedBg = R.drawable.bg_filter_chip_selected
        val normalBg = R.drawable.bg_filter_chip
        val selColor = ContextCompat.getColor(requireContext(), R.color.crimson_primary)
        val normColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val mapping = listOf(
            binding.tabAllRoutes to "ALL",
            binding.tabWeekday to "WEEKDAY",
            binding.tabSaturday to "SATURDAY"
        )
        mapping.forEach { (view, f) ->
            val on = tabFilter == f
            view.setBackgroundResource(if (on) selectedBg else normalBg)
            view.setTextColor(if (on) selColor else normColor)
            view.typeface = if (on) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
    }

    private fun setupStopsSpinner() {
        binding.spinnerStops.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            RouteData.stops.map { "${it.abbreviation} - ${it.fullName}" }
        )
    }

    private fun setupAddStop() {
        binding.btnAddStop.setOnClickListener {
            val idx = binding.spinnerStops.selectedItemPosition
            if (idx in RouteData.stops.indices) {
                selectedStops.add(RouteData.stops[idx].abbreviation)
                refreshChips()
            }
        }
    }

    private fun refreshChips() {
        binding.llStopChips.removeAllViews()
        selectedStops.forEachIndexed { i, abbr ->
            if (i > 0) binding.llStopChips.addView(TextView(requireContext()).apply {
                text = " ➔ "; textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })
            binding.llStopChips.addView(TextView(requireContext()).apply {
                text = abbr; textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.crimson_primary))
                setBackgroundResource(R.drawable.bg_route_chip)
                setPadding(24, 8, 24, 8)
                setOnClickListener { selectedStops.removeAt(i); refreshChips() }
            })
        }
    }

    private fun setupSave() {
        binding.btnSaveRoute.setOnClickListener {
            val name = binding.etRouteName.text.toString().trim()
            if (name.isEmpty() || selectedStops.size < 2) {
                Toast.makeText(requireContext(), "Name + at least 2 stops required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val id = AccountSeeder.routeDocId(name) + "_${System.currentTimeMillis()}"
            db.collection("routes").document(id).set(mapOf(
                "id" to id, "name" to name, "shortName" to "L${allRoutes.size + 1}",
                "stops" to selectedStops.toList(), "scheduleType" to "WEEKDAY",
                "routeOrder" to allRoutes.size + 1
            )).addOnSuccessListener {
                RouteRepository.invalidateCache()
                binding.etRouteName.text?.clear()
                selectedStops.clear(); refreshChips()
                Toast.makeText(requireContext(), "Route saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteSectionAdapter(displayItems,
            onEdit = { showEditDialog(it) },
            onDelete = { deleteRoute(it) })
        binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutes.adapter = routeAdapter
    }

    private fun listenToRoutes() {
        routesListener?.remove()
        routesListener = db.collection("routes").addSnapshotListener { snap, err ->
            if (_binding == null) return@addSnapshotListener
            if (err != null) {
                allRoutes.clear()
                allRoutes.addAll(RouteRepository.getCachedRoutes())
            } else {
                val fromFs = snap?.documents?.mapNotNull {
                    it.toObject(Route::class.java)?.copy(id = it.id)
                }.orEmpty()
                allRoutes.clear()
                allRoutes.addAll(
                    if (fromFs.isEmpty()) RouteRepository.getCachedRoutes()
                    else RouteRepository.getCachedRoutes().map { cached ->
                        fromFs.find { it.name.equals(cached.name, true) } ?: cached
                    }.let { merged ->
                        val extras = fromFs.filter { fs -> merged.none { it.name.equals(fs.name, true) } }
                        AdminSortHelper.sortRoutes(merged + extras)
                    }
                )
            }
            binding.tvRouteTotal.text = "${allRoutes.size} Laluan"
            binding.tabAllRoutes.text = "All (${allRoutes.size})"
            rebuildList()
        }
    }

    private fun rebuildList() {
        displayItems.clear()
        val canonical = AdminSortHelper.canonicalRoutes()
        val filtered = when (tabFilter) {
            "WEEKDAY" -> allRoutes.filter { it.scheduleType.equals("WEEKDAY", true) }
            "SATURDAY" -> allRoutes.filter { it.scheduleType.equals("SATURDAY", true) }
            else -> allRoutes
        }.ifEmpty { when (tabFilter) {
            "WEEKDAY" -> canonical.filter { it.scheduleType == "WEEKDAY" }
            "SATURDAY" -> canonical.filter { it.scheduleType == "SATURDAY" }
            else -> canonical
        }}

        val sorted = AdminSortHelper.sortRoutes(filtered)

        if (tabFilter == "ALL") {
            listOf("WEEKDAY" to "Weekday Laluan (1–8 + Shuttle)", "SATURDAY" to "Saturday Laluan (9–18)").forEach { (type, title) ->
                val group = sorted.filter { it.scheduleType.equals(type, true) }
                if (group.isNotEmpty()) {
                    displayItems.add(RouteListItem.Header(title, group.size))
                    group.forEachIndexed { idx, route ->
                        val order = canonical.indexOfFirst { it.name == route.name }.let { if (it >= 0) it + 1 else idx + 1 }
                        displayItems.add(RouteListItem.RouteItem(route, order))
                    }
                }
            }
        } else {
            sorted.forEachIndexed { idx, route ->
                val order = AdminSortHelper.routeOrderKey(route).let { if (it < 900) it else idx + 1 }
                displayItems.add(RouteListItem.RouteItem(route, order))
            }
        }

        binding.tvEmptyRoutes.visibility =
            if (displayItems.none { it is RouteListItem.RouteItem }) View.VISIBLE else View.GONE
        routeAdapter.notifyDataSetChanged()
        AdminUiHelper.expandRecyclerView(binding.rvRoutes)
    }

    private fun showEditDialog(route: Route) {
        val editStops = route.stops.toMutableList()
        val names = RouteData.stops.map { "${it.abbreviation} — ${it.fullName}" }
        val abbrs = RouteData.stops.map { it.abbreviation }

        fun buildStopSummary() = editStops.joinToString(" → ")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Stops: ${route.name}")
            .setMessage(buildStopSummary())
            .setNeutralButton("Add Stop") { dlg, _ ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Add Stop")
                    .setItems(names.toTypedArray()) { _, which ->
                        editStops.add(abbrs[which])
                        db.collection("routes").document(route.id).update("stops", editStops)
                            .addOnSuccessListener {
                                RouteRepository.invalidateCache()
                                com.google.android.material.snackbar.Snackbar
                                    .make(binding.root, "Stop added: ${abbrs[which]}", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    .show()
                            }
                    }.show()
                dlg.dismiss()
            }
            .setPositiveButton("Remove Last") { _, _ ->
                if (editStops.isNotEmpty()) {
                    val removed = editStops.removeLast()
                    db.collection("routes").document(route.id).update("stops", editStops)
                        .addOnSuccessListener { RouteRepository.invalidateCache() }
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "Removed: $removed", 4000)
                        .setAction("UNDO") {
                            editStops.add(removed)
                            db.collection("routes").document(route.id).update("stops", editStops)
                                .addOnSuccessListener { RouteRepository.invalidateCache() }
                        }
                        .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.status_moving))
                        .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun deleteRoute(route: Route) {
        val routeRef = db.collection("routes").document(route.id)
        routeRef.get().addOnSuccessListener { snapshot ->
            val backup = snapshot.data ?: return@addOnSuccessListener
            routeRef.delete().addOnSuccessListener { RouteRepository.invalidateCache() }
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "${route.name} deleted", 5000)
                .setAction("UNDO") {
                    routeRef.set(backup).addOnSuccessListener { RouteRepository.invalidateCache() }
                }
                .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.status_moving))
                .show()
        }
    }

    override fun onDestroyView() {
        routesListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    class RouteSectionAdapter(
        private val items: List<RouteListItem>,
        private val onEdit: (Route) -> Unit,
        private val onDelete: (Route) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(p: Int) = when (items[p]) {
            is RouteListItem.Header -> 0
            is RouteListItem.RouteItem -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = when (type) {
            0 -> HeaderVH(ItemAccountSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> RouteVH(ItemRouteCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            when (val item = items[pos]) {
                is RouteListItem.Header -> {
                    val hb = (holder as HeaderVH).binding
                    hb.tvSectionTitle.text = item.title
                    hb.tvSectionCount.text = "${item.count}"

                    val accentColor = when {
                        item.title.contains("Weekday", ignoreCase = true) ->
                            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.status_moving)
                        item.title.contains("Saturday", ignoreCase = true) ->
                            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.status_resting)
                        else ->
                            androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.crimson_primary)
                    }
                    hb.viewSectionAccent.setBackgroundColor(accentColor)
                    hb.tvSectionTitle.setTextColor(accentColor)
                }
                is RouteListItem.RouteItem -> {
                    val b = (holder as RouteVH).binding
                    val r = item.route
                    b.tvShortName.text = r.shortName
                    b.tvRouteOrder.visibility = View.VISIBLE
                    b.tvRouteOrder.text = AdminSortHelper.routeNumberLabel(r)
                    b.tvRouteName.text = "${AdminSortHelper.routeNumberLabel(r)} ${r.name}"
                    b.tvStopChain.text = r.stops.joinToString(" → ")
                    b.btnEditRoute.setOnClickListener { onEdit(r) }
                    holder.itemView.setOnLongClickListener { onDelete(r); true }
                }
            }
        }

        override fun getItemCount() = items.size
        class HeaderVH(val binding: ItemAccountSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        class RouteVH(val binding: ItemRouteCardBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
