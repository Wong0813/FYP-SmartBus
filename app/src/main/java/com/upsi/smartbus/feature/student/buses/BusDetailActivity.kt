package com.upsi.smartbus.feature.student.buses

import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.databinding.ActivityBusDetailBinding
import java.util.Calendar

class BusDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusDetailBinding

    private val db by lazy { FirestoreHelper.db }
    private val etaPredictor = EtaPredictor()

    private var busListener: ListenerRegistration? = null
    private var progressLayoutDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val busId   = intent.getStringExtra("bus_id")   ?: "bus_a"
        val busName = intent.getStringExtra("bus_name") ?: "Bus A"

        binding.btnBack.setOnClickListener { finish() }

        // Show the fallback immediately so the screen is never blank
        populateViews(getFallbackBus(busId, busName))

        // Attach real-time listener
        attachFirestoreListener(busId)
    }

    // ── Realtime Database ───────────────────────────────────────────────────

    private var rtdbListener: com.google.firebase.database.ValueEventListener? = null
    private var staticBus: Bus? = null

    private fun attachFirestoreListener(busId: String) {
        // 1. One-time load static details from Firestore
        db.collection("buses").document(busId).get().addOnSuccessListener { doc ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            staticBus = doc.toObject(Bus::class.java)?.copy(id = doc.id)
            bindRealtimeListener(busId)
        }.addOnFailureListener {
            bindRealtimeListener(busId)
        }
    }

    private fun bindRealtimeListener(busId: String) {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.busRef(busId).removeEventListener(it) }
        rtdbListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (isFinishing || isDestroyed) return

                val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                val speed = snapshot.child("speed").getValue(Double::class.java) ?: 0.0
                val status = snapshot.child("status").getValue(String::class.java) ?: "IDLE"
                val nextStop = snapshot.child("nextStop").getValue(String::class.java) ?: ""
                val dist = snapshot.child("distanceToNext").getValue(Double::class.java) ?: 0.0
                val routeName = snapshot.child("routeName").getValue(String::class.java) ?: ""
                val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) ?: System.currentTimeMillis()

                val s = staticBus
                val finalRoute = routeName.ifEmpty { s?.routeName ?: "Laluan 1" }
                val merged = Bus(
                    id = busId,
                    name = s?.name?.ifEmpty { finalRoute } ?: finalRoute,
                    routeName = finalRoute,
                    plateNumber = s?.plateNumber ?: busId,
                    licensePlate = s?.licensePlate ?: s?.plateNumber ?: busId,
                    routeStops = s?.routeStops ?: RouteData.getAllRoutes().find { it.name.equals(finalRoute, true) }?.stops ?: listOf("KAB", "PT", "SS", "KA", "DKP", "KAB"),
                    startStop = s?.startStop ?: "KAB",
                    location = GeoPoint(lat, lng),
                    speed = speed,
                    nextStop = nextStop,
                    distanceToNext = dist,
                    passengerCount = 12,
                    capacity = 40,
                    status = status,
                    photoUrl = s?.photoUrl.orEmpty(),
                    lastUpdated = lastUpdated
                )
                populateViews(merged)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        com.upsi.smartbus.core.data.RealtimeDbHelper.busRef(busId).addValueEventListener(rtdbListener!!)
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun populateViews(bus: Bus) {
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Toolbar title
        binding.tvToolbarTitle.text = bus.name

        // ── Route Info Card ──
        binding.tvBusName.text = "${bus.name} – ${bus.routeName}"
        binding.tvRouteSequence.text = bus.routeStops.joinToString(" ➔ ")
        binding.tvStartBadge.text = "Start: ${bus.startStop}"

        // ── Live Status Card ──
        binding.tvStatusBadge.text = bus.status
        when (bus.status) {
            "Working" -> {
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_moving)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "Resting" -> {
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_resting)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            else -> {
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_offline)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }

        // Next stop
        val nextStopFull = RouteData.getStopFullName(bus.nextStop)
        binding.tvNextStopDetail.text = "📍 Next Stop: ${bus.nextStop} ($nextStopFull)"

        // Speed & distance
        binding.tvSpeedDetail.text   = "🚀 Speed: ${bus.speed.toInt()} km/h"
        binding.tvDistanceDetail.text = "📏 Distance: ${bus.distanceToNext} km"

        // GPS coordinates (from Firestore GeoPoint)
        binding.tvFeedGps.text = "%.4f, %.4f".format(
            bus.location.latitude, bus.location.longitude
        )

        // Progress bar — only measure layout once to avoid repeated flickering
        if (!progressLayoutDone) {
            val totalDistance = 3.5
            val covered = totalDistance - bus.distanceToNext
            val progressPercent = (covered / totalDistance).coerceIn(0.0, 1.0)

            binding.progressFill.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        binding.progressFill.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        progressLayoutDone = true
                        val parentWidth = (binding.progressFill.parent as android.view.View).width
                        val fillWidth = (parentWidth * progressPercent).toInt()
                        binding.progressFill.layoutParams.width = fillWidth
                        binding.progressFill.requestLayout()
                        binding.ivBusMarker.translationX = fillWidth.toFloat() - 8f
                    }
                }
            )
        }

        // ── ETA Comparison Card ──
        val standardEta = if (bus.speed > 0) {
            ((bus.distanceToNext / bus.speed) * 60).toInt().coerceAtLeast(1)
        } else {
            0
        }
        val aiEta = etaPredictor.predictEta(bus.distanceToNext, hourOfDay)

        binding.tvStandardEta.text = "$standardEta min"
        binding.tvAiEtaDetail.text = "$aiEta min"

        val diff = standardEta - aiEta
        binding.tvInsight.text = when {
            diff > 0 -> "AI predicts ${diff}m faster arrival based on current traffic density and time-of-day patterns."
            diff < 0 -> "AI predicts ${-diff}m slower arrival due to peak-hour traffic congestion."
            else     -> "Standard and AI predictions align — traffic conditions are normal."
        }

        // ── Data Feeds Card ──
        binding.tvFeedWeather.text = "SUNNY"
        binding.tvFeedTraffic.text = if (bus.speed > 40) "LIGHT" else if (bus.speed > 0) "NORMAL" else "N/A"
        binding.tvFeedSpeed.text   = getString(R.string.speed_format, bus.speed.toInt())
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private fun getFallbackBus(busId: String, busName: String) = Bus(
        id = busId,
        name = busName,
        plateNumber = "WA 1234 A",
        licensePlate = "WA 1234 A",
        routeName = "Laluan 1",
        routeStops = listOf("KAB", "PT", "SS", "KA", "DKP", "KAB"),
        startStop = "KAB",
        location = GeoPoint(3.6845, 101.5234),
        speed = 40.0,
        nextStop = "KUO",
        distanceToNext = 1.2,
        passengerCount = 18,
        capacity = 40,
        status = "Working",
        lastUpdated = System.currentTimeMillis()
    )

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onDestroy() {
        val busId = intent.getStringExtra("bus_id") ?: "bus_a"
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.busRef(busId).removeEventListener(it) }
        busListener?.remove()
        super.onDestroy()
    }
}
