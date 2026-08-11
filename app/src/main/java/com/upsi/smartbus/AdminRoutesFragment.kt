package com.upsi.smartbus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.databinding.FragmentAdminRoutesBinding
import com.upsi.smartbus.databinding.ItemRouteCardBinding
import com.upsi.smartbus.model.Route
import com.upsi.smartbus.model.RouteData

class AdminRoutesFragment : Fragment() {

    private var _binding: FragmentAdminRoutesBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }
    private val selectedStops = mutableListOf<String>()
    private val routeList = mutableListOf<Route>()
    private lateinit var routeAdapter: RouteListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminRoutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStopsSpinner()
        setupAddStopButton()
        setupSaveButton()
        setupRecyclerView()
        loadRoutes()
    }

    private fun setupStopsSpinner() {
        val stopNames = RouteData.stops.map { "${it.abbreviation} - ${it.fullName}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, stopNames)
        binding.spinnerStops.adapter = adapter
    }

    private fun setupAddStopButton() {
        binding.btnAddStop.setOnClickListener {
            val selectedIndex = binding.spinnerStops.selectedItemPosition
            if (selectedIndex >= 0 && selectedIndex < RouteData.stops.size) {
                val stop = RouteData.stops[selectedIndex]
                selectedStops.add(stop.abbreviation)
                updateStopChips()
            }
        }
    }

    private fun updateStopChips() {
        binding.llStopChips.removeAllViews()

        selectedStops.forEachIndexed { index, stopAbbr ->
            if (index > 0) {
                val arrowView = TextView(requireContext()).apply {
                    text = " ➔ "
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
                binding.llStopChips.addView(arrowView)
            }

            val chipView = TextView(requireContext()).apply {
                text = stopAbbr
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.crimson_primary))
                setBackgroundResource(R.drawable.bg_route_chip)
                setPadding(24, 8, 24, 8)
                setOnClickListener {
                    selectedStops.removeAt(index)
                    updateStopChips()
                }
            }
            binding.llStopChips.addView(chipView)
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveRoute.setOnClickListener {
            val routeName = binding.etRouteName.text.toString().trim()

            if (routeName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a route name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedStops.size < 2) {
                Toast.makeText(requireContext(), "Please add at least 2 stops", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val routeId = "R${System.currentTimeMillis()}"
            val shortName = "L${routeList.size + 1}"

            val routeData = hashMapOf(
                "id" to routeId,
                "name" to routeName,
                "shortName" to shortName,
                "stops" to selectedStops.toList(),
                "scheduleType" to "WEEKDAY"
            )

            db.collection("routes").document(routeId).set(routeData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Route saved successfully", Toast.LENGTH_SHORT).show()
                    binding.etRouteName.text?.clear()
                    selectedStops.clear()
                    updateStopChips()
                    loadRoutes()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteListAdapter(routeList) { route -> showEditStopsDialog(route) }
        binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutes.adapter = routeAdapter
    }

    private fun showEditStopsDialog(route: Route) {
        val ctx = requireContext()
        val editStops = route.stops.toMutableList()
        val allStopNames = RouteData.stops.map { "${it.abbreviation} - ${it.fullName}" }
        val allStopAbbrs = RouteData.stops.map { it.abbreviation }
        val itemsArray: Array<CharSequence> = allStopNames.map { it as CharSequence }.toTypedArray()

        fun buildStopText(): String = editStops.joinToString(" ➔ ")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("✏️ Edit Stops: ${route.name}")
            .setMessage("Current: ${buildStopText()}\n\nSelect a stop to add:")
            .setItems(itemsArray) { _, which ->
                editStops.add(allStopAbbrs[which])

                // Show updated stops and allow saving
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Updated Stops")
                    .setMessage(editStops.joinToString(" ➔ "))
                    .setPositiveButton("Save") { _, _ ->
                        val routeId = route.id.ifEmpty { "route_${route.name.replace(" ", "_").lowercase()}" }
                        db.collection("routes").document(routeId)
                            .update("stops", editStops)
                            .addOnSuccessListener {
                                Toast.makeText(ctx, "Stops updated!", Toast.LENGTH_SHORT).show()
                                loadRoutes()
                            }
                            .addOnFailureListener {
                                // If document doesn't exist, create it
                                val routeData = hashMapOf(
                                    "id" to routeId,
                                    "name" to route.name,
                                    "shortName" to route.shortName,
                                    "stops" to editStops,
                                    "scheduleType" to route.scheduleType
                                )
                                db.collection("routes").document(routeId).set(routeData)
                                    .addOnSuccessListener {
                                        Toast.makeText(ctx, "Route saved!", Toast.LENGTH_SHORT).show()
                                        loadRoutes()
                                    }
                            }
                    }
                    .setNeutralButton("Add More") { _, _ ->
                        showEditStopsDialog(Route(route.id, route.name, route.shortName, editStops, route.scheduleType))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadRoutes() {
        // Seed default routes first
        seedDefaultRoutes()

        db.collection("routes").get()
            .addOnSuccessListener { snapshot ->
                routeList.clear()
                for (doc in snapshot.documents) {
                    val route = doc.toObject(Route::class.java)
                    if (route != null) routeList.add(route)
                }
                if (routeList.isEmpty()) {
                    routeList.addAll(RouteData.weekdayRoutes + RouteData.saturdayRoutes)
                }
                routeAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                routeList.clear()
                routeList.addAll(RouteData.weekdayRoutes + RouteData.saturdayRoutes)
                routeAdapter.notifyDataSetChanged()
            }
    }

    private fun seedDefaultRoutes() {
        val allRoutes = RouteData.weekdayRoutes + RouteData.saturdayRoutes
        for (route in allRoutes) {
            val docId = "route_${route.name.replace(" ", "_").lowercase()}"
            val routeData = hashMapOf(
                "id" to route.id,
                "name" to route.name,
                "shortName" to route.shortName,
                "stops" to route.stops,
                "scheduleType" to route.scheduleType
            )
            db.collection("routes").document(docId).set(routeData, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ════════════════════════════════════════════════════════════════════
    // ROUTE LIST ADAPTER (with edit button)
    // ════════════════════════════════════════════════════════════════════

    class RouteListAdapter(
        private val routes: List<Route>,
        private val onEdit: (Route) -> Unit
    ) : RecyclerView.Adapter<RouteListAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemRouteCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRouteCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val route = routes[position]
            holder.binding.tvShortName.text = route.shortName
            holder.binding.tvRouteName.text = route.name
            holder.binding.tvStopChain.text = route.stops.joinToString(" ➔ ")
            holder.binding.btnEditRoute.setOnClickListener { onEdit(route) }
        }

        override fun getItemCount() = routes.size
    }
}
