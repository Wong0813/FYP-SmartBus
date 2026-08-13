package com.upsi.smartbus.feature.admin.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.databinding.FragmentAdminLiveMapBinding

class AdminLiveMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentAdminLiveMapBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private var map: GoogleMap? = null
    private val busMarkers = mutableMapOf<String, Marker>()
    private val busList = mutableListOf<Bus>()
    private var busListener: ListenerRegistration? = null

    // UPSI campus center
    private val upsiCenter = LatLng(3.5430, 101.8042)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminLiveMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.adminMapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnRefreshMap.setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15f))
            reloadBuses()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            moveCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15f))

            // UPSI campus boundary circle
            addCircle(
                CircleOptions()
                    .center(upsiCenter)
                    .radius(1500.0)
                    .strokeColor(Color.parseColor("#22990000"))
                    .fillColor(Color.parseColor("#08990000"))
                    .strokeWidth(2f)
            )
        }
        listenToBuses()
    }

    private fun listenToBuses() {
        busListener = db.collection("buses").addSnapshotListener { snapshot, error ->
            if (_binding == null || map == null) return@addSnapshotListener
            if (error != null || snapshot == null) return@addSnapshotListener

            busList.clear()
            val seenIds = mutableSetOf<String>()

            for (doc in snapshot.documents) {
                val bus = doc.toObject(Bus::class.java) ?: continue
                busList.add(bus)
                seenIds.add(bus.id)

                val lat = bus.location.latitude
                val lng = bus.location.longitude
                if (lat == 0.0 && lng == 0.0) continue

                val pos = LatLng(lat, lng)
                val color = when (bus.status.lowercase()) {
                    "working" -> BitmapDescriptorFactory.HUE_GREEN
                    "resting" -> BitmapDescriptorFactory.HUE_ORANGE
                    else -> BitmapDescriptorFactory.HUE_AZURE
                }

                val existing = busMarkers[bus.id]
                if (existing != null) {
                    existing.position = pos
                    existing.title = "${bus.routeName} — ${bus.status.uppercase()}"
                } else {
                    val marker = map?.addMarker(
                        MarkerOptions()
                            .position(pos)
                            .title("${bus.routeName} — ${bus.status.uppercase()}")
                            .snippet("${bus.id} | Pemandu aktif")
                            .icon(BitmapDescriptorFactory.defaultMarker(color))
                    )
                    if (marker != null) busMarkers[bus.id] = marker
                }
            }

            // Remove markers for buses no longer in snapshot
            val toRemove = busMarkers.keys.filter { it !in seenIds }
            toRemove.forEach { id ->
                busMarkers.remove(id)?.remove()
            }

            updateStatusPanel()
        }
    }

    private fun reloadBuses() {
        busMarkers.values.forEach { it.remove() }
        busMarkers.clear()
        busListener?.remove()
        listenToBuses()
    }

    private fun updateStatusPanel() {
        val ctx = requireContext()
        var active = 0; var resting = 0; var offline = 0
        busList.forEach {
            when (it.status.lowercase()) {
                "working" -> active++
                "resting" -> resting++
                else -> offline++
            }
        }

        binding.tvLiveBusCount.text = "$active Bas Aktif"
        binding.tvMapStatActive.text = "$active Aktif"
        binding.tvMapStatResting.text = "$resting Rehat"
        binding.tvMapStatOffline.text = "$offline Offline"

        // Build horizontal bus chips
        binding.llBusStatusRow.removeAllViews()
        val isEmpty = busList.isEmpty()
        binding.tvMapEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.llBusStatusRow.visibility = if (isEmpty) View.GONE else View.VISIBLE

        busList.sortedWith(compareBy({ it.status }, { it.routeName })).forEach { bus ->
            val chip = TextView(ctx).apply {
                val (bgColor, textColor) = when (bus.status.lowercase()) {
                    "working" -> R.drawable.bg_status_moving to R.color.status_moving
                    "resting" -> R.drawable.bg_status_resting to R.color.status_resting
                    else -> R.drawable.bg_status_offline to R.color.status_offline
                }
                setBackgroundResource(bgColor)
                setTextColor(ContextCompat.getColor(ctx, textColor))
                text = bus.routeName.take(12).ifEmpty { bus.id }
                textSize = 11f
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8 }
                setOnClickListener {
                    val lat = bus.location.latitude
                    val lng = bus.location.longitude
                    if (lat != 0.0 || lng != 0.0) {
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f)
                        )
                        busMarkers[bus.id]?.showInfoWindow()
                    }
                }
            }
            binding.llBusStatusRow.addView(chip)
        }
    }

    override fun onDestroyView() {
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
