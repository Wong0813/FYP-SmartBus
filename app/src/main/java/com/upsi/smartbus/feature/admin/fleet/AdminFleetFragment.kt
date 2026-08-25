package com.upsi.smartbus.feature.admin.fleet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.AppCache
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.databinding.DialogEditBusBinding
import com.upsi.smartbus.databinding.FragmentAdminFleetBinding
import com.upsi.smartbus.databinding.ItemFleetCardBinding
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import java.io.ByteArrayOutputStream

class AdminFleetFragment : Fragment() {

    private var _binding: FragmentAdminFleetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private val allBuses = mutableListOf<Bus>()
    private val displayBuses = mutableListOf<Bus>()
    private val routeOptions = mutableListOf<Route>()
    private val routeNames = mutableListOf<String>()
    private lateinit var fleetAdapter: FleetAdapter
    private var fleetListener: ListenerRegistration? = null
    private var searchQuery = ""

    private var pendingBusPhotoCallback: ((String) -> Unit)? = null
    private val pickBusPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val resized = Bitmap.createScaledBitmap(bitmap, 200, 150, true)
                val outputStream = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                val photoUrl = "data:image/jpeg;base64,$base64"
                pendingBusPhotoCallback?.invoke(photoUrl)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load bus photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminFleetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnHeroDrawer.setOnClickListener {
            (activity as? AdminActivity)?.openDrawer()
        }
        setupRouteSpinner()
        setupSearch()
        setupRecyclerView()
        setupRegisterButton()
        setupSync()
        setupToggleForm()
        listenToFleet()
    }

    private fun setupSearch() {
        binding.etSearchBuses.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty().lowercase()
                binding.btnClearBusSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterBuses()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearBusSearch.setOnClickListener {
            binding.etSearchBuses.setText("")
        }
    }

    private fun filterBuses() {
        displayBuses.clear()
        if (searchQuery.isEmpty()) {
            displayBuses.addAll(allBuses)
        } else {
            displayBuses.addAll(allBuses.filter { bus ->
                bus.id.lowercase().contains(searchQuery) ||
                        bus.name.lowercase().contains(searchQuery) ||
                        bus.licensePlate.lowercase().contains(searchQuery) ||
                        bus.plateNumber.lowercase().contains(searchQuery) ||
                        bus.routeName.lowercase().contains(searchQuery)
            })
        }

        binding.tvFleetListCount.text = "${displayBuses.size}"
        val isEmpty = displayBuses.isEmpty()
        binding.llEmptyFleet.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFleet.visibility = if (isEmpty) View.GONE else View.VISIBLE
        fleetAdapter.notifyDataSetChanged()
    }

    private fun setupToggleForm() {
        binding.btnToggleFleetForm.setOnClickListener {
            val show = binding.llFleetForm.visibility != View.VISIBLE
            binding.llFleetForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.tvRegisterBusBtnText.text = if (show) "✕ Close Form" else "+ Register Bus"
        }
    }

    private fun setupSync() {
        binding.btnSyncFleet.setOnClickListener {
            binding.btnSyncFleet.isEnabled = false
            AccountSeeder.seedOfficialBuses(requireContext()) {
                if (_binding == null) return@seedOfficialBuses
                binding.btnSyncFleet.isEnabled = true
                Toast.makeText(requireContext(), "Buses synced to Firestore", Toast.LENGTH_SHORT).show()

                // After syncing, clean stale GPS fields from Firestore
                // (GPS data now lives exclusively in Realtime Database)
                com.upsi.smartbus.core.data.FirestoreCleanup.cleanBusGpsFields { success, failed ->
                    if (_binding == null) return@cleanBusGpsFields
                    android.util.Log.d("FleetSync", "GPS field cleanup: $success cleaned, $failed failed")
                }
            }
        }
    }

    private fun setupRouteSpinner() {
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            routeOptions.clear()
            routeOptions.addAll(routes)
            routeNames.clear()
            routeNames.addAll(routes.map { it.name })
            val labels = routes.mapIndexed { i, r ->
                "${AdminSortHelper.routeNumberLabel(r)} ${r.shortName} — ${r.name}"
            }
            binding.spinnerBusRoute.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, labels
            )
        }
    }

    private fun setupRecyclerView() {
        fleetAdapter = FleetAdapter(displayBuses, onEdit = { showEditBusDialog(it) }, onDelete = { deleteBus(it) })
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
        // Only store static fields in Firestore; GPS/live data is in Realtime DB
        db.collection("buses").document(busId).set(mapOf(
            "id"           to busId,
            "name"         to busName,
            "licensePlate" to plate,
            "plateNumber"  to plate,
            "routeName"    to route.name,
            "capacity"     to 40
        )).addOnSuccessListener {
            AppCache.invalidateBuses()
            Toast.makeText(requireContext(), "Bus registered", Toast.LENGTH_SHORT).show()
            binding.etBusId.text?.clear(); binding.etBusName.text?.clear(); binding.etLicensePlate.text?.clear()
            binding.llFleetForm.visibility = View.GONE
            binding.tvRegisterBusBtnText.text = "+ Register Bus"
        }
    }

    private fun showEditBusDialog(bus: Bus) {
        val ctx = requireContext()
        val dialog = android.app.Dialog(ctx)
        val b = DialogEditBusBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        b.tvEditBusDialogTitle.text = "Edit ${bus.id}"
        b.etEditBusName.setText(bus.name)
        b.etEditBusPlate.setText(bus.licensePlate.ifEmpty { bus.plateNumber })

        var currentPhotoUrl = bus.photoUrl

        fun updateThumbnail() {
            FleetAdapter.loadBusImageFromFirebase(b.ivDialogBusThumbnail, currentPhotoUrl)
        }
        updateThumbnail()

        b.btnPickBusPhoto.setOnClickListener {
            pendingBusPhotoCallback = { newPhoto ->
                currentPhotoUrl = newPhoto
                updateThumbnail()
            }
            pickBusPhotoLauncher.launch("image/*")
        }

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, routeNames)
        b.spinnerEditBusRoute.adapter = adapter
        val rIdx = routeNames.indexOf(bus.routeName)
        if (rIdx >= 0) b.spinnerEditBusRoute.setSelection(rIdx)

        b.btnDialogBusClose.setOnClickListener { dialog.dismiss() }
        b.btnDialogBusCancel.setOnClickListener { dialog.dismiss() }

        b.btnDialogBusSave.setOnClickListener {
            val newName = b.etEditBusName.text.toString().trim()
            val newPlate = b.etEditBusPlate.text.toString().trim()
            val selectedRoute = b.spinnerEditBusRoute.selectedItem?.toString() ?: bus.routeName

            if (newName.isEmpty() || newPlate.isEmpty()) {
                Toast.makeText(ctx, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            b.btnDialogBusSave.isEnabled = false
            b.btnDialogBusSave.text = "Saving..."

            // 1. Instantly update local list for 0-latency UI feedback
            val targetIdx = allBuses.indexOfFirst { it.id == bus.id }
            val updatedBus = bus.copy(
                name = newName,
                licensePlate = newPlate,
                plateNumber = newPlate,
                routeName = selectedRoute,
                photoUrl = currentPhotoUrl
            )
            if (targetIdx >= 0) {
                allBuses[targetIdx] = updatedBus
                filterBuses()
            }

            // 2. Safely dismiss dialog immediately
            dialog.dismiss()
            Toast.makeText(ctx, "Bus updated successfully", Toast.LENGTH_SHORT).show()

            // 3. Asynchronously update Firebase (resilient to quota limits)
            db.collection("buses").document(bus.id).update(mapOf(
                "name" to newName,
                "licensePlate" to newPlate,
                "plateNumber" to newPlate,
                "routeName" to selectedRoute,
                "photoUrl" to currentPhotoUrl
            ))
        }

        dialog.show()
    }

    private fun listenToFleet() {
        // Show cached data instantly if available (avoids blank on back-navigation)
        com.upsi.smartbus.core.data.AppCache.getCachedBuses()?.let { cached ->
            if (_binding == null) return
            allBuses.clear()
            allBuses.addAll(cached)
            allBuses.sortBy { com.upsi.smartbus.feature.admin.seeder.AdminSortHelper.busOrderKey(it.id) }
            val active = allBuses.count { it.status.equals("working", true) }
            binding.tvFleetTotal.text = "${allBuses.size}"
            binding.tvFleetActiveCount.text = "$active"
            binding.tvFleetDepotCount.text = "${allBuses.size - active}"
            filterBuses()
        }

        fleetListener?.remove()
        fleetListener = db.collection("buses").addSnapshotListener { snap, err ->
            if (_binding == null) return@addSnapshotListener
            allBuses.clear()
            for (doc in snap?.documents.orEmpty()) {
                doc.toObject(Bus::class.java)?.let { allBuses.add(it.copy(id = doc.id)) }
            }
            allBuses.sortBy { AdminSortHelper.busOrderKey(it.id) }

            val active = allBuses.count { it.status.equals("working", true) }
            val inDepot = allBuses.size - active

            binding.tvFleetTotal.text = "${allBuses.size}"
            binding.tvFleetActiveCount.text = "$active"
            binding.tvFleetDepotCount.text = "$inDepot"

            // Update cache for next visit
            com.upsi.smartbus.core.data.AppCache.putBuses(allBuses.toList())
            filterBuses()
        }
    }

    private fun deleteBus(bus: Bus) {
        val busRef = db.collection("buses").document(bus.id)
        busRef.get().addOnSuccessListener { snapshot ->
            busRef.delete()
            Toast.makeText(requireContext(), "${bus.id} removed", Toast.LENGTH_SHORT).show()
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

        companion object {
            fun loadBusImageFromFirebase(iv: ImageView, photoUrl: String) {
                if (photoUrl.startsWith("data:image")) {
                    try {
                        val base64Part = photoUrl.substringAfter("base64,")
                        val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            iv.setImageBitmap(bitmap)
                            iv.background = null
                            iv.setPadding(0, 0, 0, 0)
                            iv.imageTintList = null
                            iv.scaleType = ImageView.ScaleType.CENTER_CROP
                            return
                        }
                    } catch (_: Exception) {}
                }
                // Fallback default bus icon style
                iv.setImageResource(R.drawable.ic_bus_white)
                iv.setBackgroundColor(Color.parseColor("#F1F5F9"))
                val p = (iv.resources.displayMetrics.density * 8).toInt()
                iv.setPadding(p, p, p, p)
                iv.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C0181A"))
                iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }

        inner class VH(val binding: ItemFleetCardBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(ItemFleetCardBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val bus = buses[pos]
            val ctx = h.itemView.context
            h.binding.tvBusName.text = "${bus.id} \u2022 ${bus.name}"
            h.binding.tvBusPlate.text = bus.licensePlate.ifEmpty { bus.plateNumber }
            h.binding.tvBusRoute.text = bus.routeName.ifEmpty { "Unassigned" }

            // Load pure photo strictly from Firebase
            loadBusImageFromFirebase(h.binding.ivBusThumbnail, bus.photoUrl)

            when (bus.status.lowercase()) {
                "working" -> {
                    h.binding.statusDot.setBackgroundResource(R.drawable.dot_green)
                    h.binding.tvBusStatus.text = "ACTIVE"
                    h.binding.tvBusStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_moving))
                    h.binding.llStatusPill.setBackgroundResource(R.drawable.bg_status_moving)
                }
                "resting" -> {
                    h.binding.statusDot.setBackgroundResource(R.drawable.dot_orange)
                    h.binding.tvBusStatus.text = "RESTING"
                    h.binding.tvBusStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_resting))
                    h.binding.llStatusPill.setBackgroundResource(R.drawable.bg_status_resting)
                }
                else -> {
                    h.binding.statusDot.setBackgroundResource(R.drawable.dot_gray)
                    h.binding.tvBusStatus.text = "OFFLINE"
                    h.binding.tvBusStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_offline))
                    h.binding.llStatusPill.setBackgroundResource(R.drawable.bg_status_offline)
                }
            }

            h.itemView.setOnClickListener { onEdit(bus) }
            h.binding.btnEditBus.setOnClickListener { onEdit(bus) }
            h.binding.btnDeleteBus.setOnClickListener { onDelete(bus) }
        }
        override fun getItemCount() = buses.size
    }
}
