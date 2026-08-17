package com.upsi.smartbus.feature.admin.fleet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.databinding.FragmentAdminFleetBinding
import com.upsi.smartbus.databinding.ItemFleetCardBinding
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import com.upsi.smartbus.feature.admin.seeder.AdminUiHelper

class AdminFleetFragment : Fragment() {

    private var _binding: FragmentAdminFleetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private val busList = mutableListOf<Bus>()
    private val routeOptions = mutableListOf<Route>()
    private lateinit var fleetAdapter: FleetAdapter
    private var fleetListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminFleetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRouteSpinner()
        setupRecyclerView()
        setupRegisterButton()
        setupSync()
        setupToggleForm()
        listenToFleet()
    }

    private fun setupToggleForm() {
        binding.btnToggleFleetForm.setOnClickListener {
            val show = binding.llFleetForm.visibility != View.VISIBLE
            binding.llFleetForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnToggleFleetForm.text = if (show) "Close" else "+ Register Bus"
        }
    }

    private fun setupSync() {
        binding.btnSyncFleet.setOnClickListener {
            binding.btnSyncFleet.isEnabled = false
            binding.btnSyncFleet.text = "Syncing..."
            AccountSeeder.seedOfficialBuses {
                if (_binding == null) return@seedOfficialBuses
                binding.btnSyncFleet.isEnabled = true
                binding.btnSyncFleet.text = "Sync Fleet"
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, "20 buses synced to Firestore", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun setupRouteSpinner() {
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            routeOptions.clear()
            routeOptions.addAll(routes)
            val labels = routes.mapIndexed { i, r ->
                "${AdminSortHelper.routeNumberLabel(r)} ${r.shortName} — ${r.name}"
            }
            binding.spinnerBusRoute.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, labels
            )
        }
    }

    private fun setupRecyclerView() {
        fleetAdapter = FleetAdapter(busList, onEdit = { showEdit(it) }, onDelete = { deleteBus(it) })
        binding.rvFleet.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFleet.adapter = fleetAdapter
    }

    private fun setupRegisterButton() {
        binding.btnRegisterBus.setOnClickListener {
            val busId = binding.etBusId.text.toString().trim()
            val busName = binding.etBusName.text.toString().trim()
            val plate = binding.etLicensePlate.text.toString().trim()
            if (busId.isEmpty() || busName.isEmpty() || plate.isEmpty()) {
                Toast.makeText(requireContext(), "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (routeOptions.isEmpty()) return@setOnClickListener
            registerBus(busId, busName, plate, routeOptions[binding.spinnerBusRoute.selectedItemPosition])
        }
    }

    private fun registerBus(busId: String, busName: String, plate: String, route: Route) {
        db.collection("buses").document(busId).set(mapOf(
            "id" to busId, "name" to busName, "licensePlate" to plate, "plateNumber" to plate,
            "routeName" to route.name, "routeStops" to route.stops,
            "startStop" to route.stops.firstOrNull().orEmpty(), "status" to "IDLE",
            "speed" to 0.0, "nextStop" to route.stops.getOrElse(1) { "" },
            "lastUpdated" to System.currentTimeMillis()
        )).addOnSuccessListener {
            Toast.makeText(requireContext(), "Bus registered", Toast.LENGTH_SHORT).show()
            binding.etBusId.text?.clear(); binding.etBusName.text?.clear(); binding.etLicensePlate.text?.clear()
        }
    }

    private fun showEdit(bus: Bus) {
        val ctx = requireContext()
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etName = EditText(ctx).apply { hint = "Name"; setText(bus.name) }
        val etPlate = EditText(ctx).apply { hint = "Plate"; setText(bus.licensePlate.ifEmpty { bus.plateNumber }) }
        layout.addView(etName); layout.addView(etPlate)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Edit ${bus.id}").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                db.collection("buses").document(bus.id).update(mapOf(
                    "name" to etName.text.toString().trim(),
                    "licensePlate" to etPlate.text.toString().trim(),
                    "plateNumber" to etPlate.text.toString().trim()
                ))
            }.show()
    }

    private fun listenToFleet() {
        fleetListener?.remove()
        fleetListener = db.collection("buses").addSnapshotListener { snap, err ->
            if (_binding == null) return@addSnapshotListener
            busList.clear()
            for (doc in snap?.documents.orEmpty()) {
                doc.toObject(Bus::class.java)?.let { busList.add(it.copy(id = doc.id)) }
            }
            busList.sortBy { AdminSortHelper.busOrderKey(it.id) }
            binding.tvFleetTotal.text = "${busList.size} Buses"
            val isEmpty = busList.isEmpty()
            binding.llEmptyFleet.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvFleet.visibility = if (isEmpty) View.GONE else View.VISIBLE
            fleetAdapter.notifyDataSetChanged()
            AdminUiHelper.expandRecyclerView(binding.rvFleet)
        }
    }

    private fun deleteBus(bus: Bus) {
        val busRef = db.collection("buses").document(bus.id)
        busRef.get().addOnSuccessListener { snapshot ->
            val backup = snapshot.data ?: return@addOnSuccessListener
            busRef.delete()
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "${bus.id} removed", 5000)
                .setAction("UNDO") {
                    busRef.set(backup)
                }
                .setActionTextColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_moving)
                )
                .show()
        }
    }

    override fun onDestroyView() {
        fleetListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    class FleetAdapter(
        private val buses: List<Bus>,
        private val onEdit: (Bus) -> Unit,
        private val onDelete: (Bus) -> Unit
    ) : RecyclerView.Adapter<FleetAdapter.VH>() {
        inner class VH(val binding: ItemFleetCardBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(ItemFleetCardBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val bus = buses[pos]
            val ctx = h.itemView.context
            val order = AdminSortHelper.busOrderKey(bus.id)
            h.binding.tvBusOrder.text = "#${String.format("%02d", order.coerceAtMost(99))}"
            h.binding.tvBusName.text = "${bus.id} \u2022 ${bus.name}"
            h.binding.tvBusPlate.text = bus.licensePlate.ifEmpty { bus.plateNumber }
            h.binding.tvBusRoute.text = bus.routeName.ifEmpty { "Unassigned" }

            when (bus.status.lowercase()) {
                "working" -> {
                    h.binding.statusDot.setBackgroundResource(R.drawable.dot_green)
                    h.binding.tvBusStatus.text = "ACTIVE"
                    h.binding.tvBusStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_moving))
                    h.binding.llStatusPill.setBackgroundResource(R.drawable.bg_status_moving)
                }
                "resting" -> {
                    h.binding.statusDot.setBackgroundResource(R.drawable.dot_orange)
                    h.binding.tvBusStatus.text = "RESTING"
                    h.binding.tvBusStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_resting))
                    h.binding.llStatusPill.setBackgroundResource(R.drawable.bg_status_resting)
                }
                else -> {
                    h.binding.statusDot.setBackgroundResource(R.drawable.dot_gray)
                    h.binding.tvBusStatus.text = "OFFLINE"
                    h.binding.tvBusStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_offline))
                    h.binding.llStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
                }
            }

            h.itemView.setOnClickListener { onEdit(bus) }
            h.binding.btnDeleteBus.setOnClickListener { onDelete(bus) }
        }
        override fun getItemCount() = buses.size
    }
}
