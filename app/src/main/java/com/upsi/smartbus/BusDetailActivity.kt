package com.upsi.smartbus

import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.databinding.ActivityBusDetailBinding
import com.upsi.smartbus.logic.EtaPredictor
import com.upsi.smartbus.model.Bus
import com.upsi.smartbus.model.RouteData
import java.util.Calendar

class BusDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusDetailBinding

    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }
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

    // ── Firestore ─────────────────────────────────────────────────────────────

    private fun attachFirestoreListener(busId: String) {
        busListener = db.collection("buses").document(busId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) {
                    // Firestore unavailable — keep showing the fallback
                    return@addSnapshotListener
                }

                try {
                    val bus = doc.toObject(Bus::class.java)
                    if (bus != null) populateViews(bus)
                } catch (_: Exception) {}
            }
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
        busListener?.remove()
        super.onDestroy()
    }
}
