package com.upsi.smartbus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.databinding.FragmentAdminFleetBinding
import com.upsi.smartbus.databinding.ItemFleetCardBinding
import com.upsi.smartbus.model.Bus
import com.upsi.smartbus.model.RouteData

class AdminFleetFragment : Fragment() {

    private var _binding: FragmentAdminFleetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }
    private val busList = mutableListOf<Bus>()
    private lateinit var fleetAdapter: FleetAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminFleetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRouteSpinner()
        setupRecyclerView()
        setupRegisterButton()
        loadFleet()
    }

    private fun setupRouteSpinner() {
        val routes = RouteData.weekdayRoutes.map { "${it.shortName} - ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, routes)
        binding.spinnerBusRoute.adapter = adapter
    }

    private fun setupRecyclerView() {
        fleetAdapter = FleetAdapter(busList) { bus -> deleteBus(bus) }
        binding.rvFleet.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFleet.adapter = fleetAdapter
    }

    private fun setupRegisterButton() {
        binding.btnRegisterBus.setOnClickListener {
            val busId = binding.etBusId.text.toString().trim()
            val busName = binding.etBusName.text.toString().trim()
            val licensePlate = binding.etLicensePlate.text.toString().trim()
            val selectedRoute = binding.spinnerBusRoute.selectedItem?.toString() ?: ""

            if (busId.isEmpty() || busName.isEmpty() || licensePlate.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val busData = hashMapOf(
                "id" to busId,
                "name" to busName,
                "licensePlate" to licensePlate,
                "plateNumber" to licensePlate,
                "routeName" to selectedRoute,
                "status" to "IDLE",
                "speed" to 0.0,
                "nextStop" to "",
                "lastUpdated" to System.currentTimeMillis()
            )

            db.collection("buses").document(busId).set(busData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Bus registered successfully", Toast.LENGTH_SHORT).show()
                    clearForm()
                    loadFleet()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun clearForm() {
        binding.etBusId.text?.clear()
        binding.etBusName.text?.clear()
        binding.etLicensePlate.text?.clear()
        binding.spinnerBusRoute.setSelection(0)
    }

    private fun loadFleet() {
        db.collection("buses").get()
            .addOnSuccessListener { snapshot ->
                busList.clear()
                for (doc in snapshot.documents) {
                    val bus = doc.toObject(Bus::class.java)
                    if (bus != null) busList.add(bus)
                }
                fleetAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                // Demo data fallback
                busList.clear()
                busList.addAll(listOf(
                    Bus("B001", "Bus A", "WKN 3344", "WKN 3344", "Laluan 1", listOf("KAB", "PT", "SS", "KA", "DKP", "KAB"), "KAB", status = "Working"),
                    Bus("B002", "Bus B", "JHR 5566", "JHR 5566", "Laluan 2", listOf("KUO", "PP", "FK", "FPE", "KUO"), "KUO", status = "Resting"),
                    Bus("B003", "Bus C", "PRK 7788", "PRK 7788", "Laluan 3", listOf("KAB", "KUO", "DKP", "SS", "KAB"), "KAB", status = "IDLE")
                ))
                fleetAdapter.notifyDataSetChanged()
            }
    }

    private fun deleteBus(bus: Bus) {
        if (bus.id.isEmpty()) return
        db.collection("buses").document(bus.id).delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Bus removed", Toast.LENGTH_SHORT).show()
                loadFleet()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Inner adapter for fleet list
    class FleetAdapter(
        private val buses: List<Bus>,
        private val onDelete: (Bus) -> Unit
    ) : RecyclerView.Adapter<FleetAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemFleetCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFleetCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bus = buses[position]
            holder.binding.tvBusName.text = bus.name
            holder.binding.tvBusPlate.text = bus.licensePlate.ifEmpty { bus.plateNumber }
            holder.binding.tvBusRoute.text = bus.routeName

            // Status dot color
            val dotRes = when (bus.status.lowercase()) {
                "working" -> R.drawable.dot_green
                "resting" -> R.drawable.dot_orange
                else -> R.drawable.dot_gray
            }
            holder.binding.statusDot.setBackgroundResource(dotRes)

            holder.binding.btnDeleteBus.setOnClickListener { onDelete(bus) }
        }

        override fun getItemCount() = buses.size
    }
}
