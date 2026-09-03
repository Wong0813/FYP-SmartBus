package com.upsi.smartbus.core.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.RealtimeDbHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.BusEtaCalculator
import com.upsi.smartbus.core.util.BusSpeedCalculator
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.feature.driver.DriverActivity
import kotlin.math.roundToInt

/**
 * Real-Time GPS Foreground Tracking Service.
 * Continuously collects high-accuracy GPS coordinates from hardware sensors,
 * computes live speed, target stop, and syncs updates to Firebase Realtime Database.
 */
class GpsTrackingService : Service() {

    companion object {
        const val TAG = "SmartBusGpsService"
        const val CHANNEL_ID = "smartbus_gps_tracking_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_TRACKING = "com.upsi.smartbus.ACTION_START_GPS"
        const val ACTION_STOP_TRACKING = "com.upsi.smartbus.ACTION_STOP_GPS"
        const val ACTION_UPDATE_STATUS = "com.upsi.smartbus.ACTION_UPDATE_STATUS"
        const val BROADCAST_GPS_UPDATE = "com.upsi.smartbus.BROADCAST_GPS_UPDATE"

        const val EXTRA_BUS_ID = "extra_bus_id"
        const val EXTRA_ROUTE_NAME = "extra_route_name"
        const val EXTRA_DRIVER_NAME = "extra_driver_name"
        const val EXTRA_STATUS = "extra_status"

        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_BEARING = "extra_bearing"
        const val EXTRA_NEXT_STOP = "extra_next_stop"
        const val EXTRA_DIST_KM = "extra_dist_km"

        var isServiceRunning = false
            private set

        fun start(context: Context, busId: String, routeName: String, driverName: String, status: String = "WORKING") {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_BUS_ID, busId)
                putExtra(EXTRA_ROUTE_NAME, routeName)
                putExtra(EXTRA_DRIVER_NAME, driverName)
                putExtra(EXTRA_STATUS, status)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateStatus(context: Context, status: String) {
            if (!isServiceRunning) return
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS, status)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.stopService(intent)
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var busId = "BUS-001"
    private var routeName = "Laluan 1"
    private var driverName = "UPSI Driver"
    private var currentStatus = "WORKING"
    private var routeStops = listOf("KAB", "PT", "SS", "KA", "DKP", "KAB")

    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TRACKING) {
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_UPDATE_STATUS) {
            val newStatus = intent.getStringExtra(EXTRA_STATUS)?.ifEmpty { "WORKING" } ?: "WORKING"
            currentStatus = newStatus
            Log.d(TAG, "GPS Service status updated to $currentStatus")
            return START_STICKY
        }

        busId = intent?.getStringExtra(EXTRA_BUS_ID)?.ifEmpty { "BUS-001" } ?: "BUS-001"
        routeName = intent?.getStringExtra(EXTRA_ROUTE_NAME)?.ifEmpty { "Laluan 1" } ?: "Laluan 1"
        driverName = intent?.getStringExtra(EXTRA_DRIVER_NAME)?.ifEmpty { "UPSI Driver" } ?: "UPSI Driver"
        currentStatus = intent?.getStringExtra(EXTRA_STATUS)?.ifEmpty { "WORKING" } ?: "WORKING"

        val routeObj = RouteRepository.findRoute(routeName)
        if (routeObj != null && routeObj.stops.isNotEmpty()) {
            routeStops = routeObj.stops
        }

        val initialNotif = buildForegroundNotification("Acquiring GPS Signal...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        startLocationUpdates()
        isServiceRunning = true

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processLocationUpdate(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "GPS location updates requested successfully for bus $busId ($routeName)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
        }
    }

    private fun processLocationUpdate(location: Location) {
        val now = System.currentTimeMillis()
        val lat = location.latitude
        val lon = location.longitude

        // Speed calculation: Hardware GPS speed or GPS displacement speed
        val calculatedSpeed = if (location.hasSpeed() && location.speed > 0) {
            (location.speed * 3.6).roundToInt().toDouble()
        } else if (lastTimeMs > 0 && (lastLat != 0.0 || lastLon != 0.0)) {
            BusSpeedCalculator.calculateGpsSpeedKmh(lastLat, lastLon, lastTimeMs, lat, lon, now)
        } else {
            0.0
        }

        lastLat = lat
        lastLon = lon
        lastTimeMs = now

        // Find closest forward stop along the route
        val (nextStopAbbr, distKm) = findNextStopAlongRoute(lat, lon)

        // Sync to Firebase Realtime Database
        val liveData = hashMapOf(
            "latitude" to lat,
            "longitude" to lon,
            "speed" to calculatedSpeed,
            "status" to currentStatus,
            "nextStop" to nextStopAbbr,
            "distanceToNext" to distKm,
            "driverName" to driverName,
            "routeName" to routeName,
            "lastUpdated" to now
        )
        RealtimeDbHelper.busRef(busId).setValue(liveData)

        // Update notification
        val speedStr = BusSpeedCalculator.formatSpeed(calculatedSpeed)
        val distStr = BusEtaCalculator.formatDistance(distKm)
        val notifText = "Speed: $speedStr • Next: $nextStopAbbr ($distStr)"
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(NOTIFICATION_ID, buildForegroundNotification(notifText))

        // Broadcast locally to DriverControlFragment
        val broadcastIntent = Intent(BROADCAST_GPS_UPDATE).apply {
            putExtra(EXTRA_LAT, lat)
            putExtra(EXTRA_LON, lon)
            putExtra(EXTRA_SPEED, calculatedSpeed)
            putExtra(EXTRA_BEARING, location.bearing)
            putExtra(EXTRA_NEXT_STOP, nextStopAbbr)
            putExtra(EXTRA_DIST_KM, distKm)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun findNextStopAlongRoute(lat: Double, lon: Double): Pair<String, Double> {
        if (routeStops.isEmpty()) return Pair("PT", 0.0)

        var closestStop = routeStops.first()
        var minDistance = Double.MAX_VALUE

        for (abbr in routeStops) {
            val stop = RouteData.getStopByAbbreviation(abbr) ?: continue
            val dist = EtaPredictor.calculateDistanceKm(lat, lon, stop.latitude, stop.longitude)
            if (dist < minDistance) {
                minDistance = dist
                closestStop = abbr
            }
        }

        val roundedDist = (minDistance * 10.0).roundToInt() / 10.0
        return Pair(closestStop, roundedDist)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        isServiceRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SmartBus GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live GPS broadcasting for UPSI SmartBus"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val launchIntent = Intent(this, DriverActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartBus GPS Active • $routeName")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_bus_white)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
