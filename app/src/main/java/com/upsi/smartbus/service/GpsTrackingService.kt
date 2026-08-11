package com.upsi.smartbus.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * GPS Tracking Service - placeholder for future real-time GPS tracking.
 * This service would run in the background to continuously transmit driver location
 * to Firestore when the driver is on duty.
 */
class GpsTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Future: start GPS location updates and push to Firestore
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Future: stop GPS location updates
    }
}
