package com.upsi.smartbus.core.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object RealtimeDbHelper {

    // Initialize with standard or configured instance URL
    val db: DatabaseReference by lazy {
        try {
            FirebaseDatabase.getInstance("https://bus-tracking-app-30029-default-rtdb.asia-southeast1.firebasedatabase.app").reference
        } catch (_: Exception) {
            FirebaseDatabase.getInstance().reference
        }
    }

    val liveBuses: DatabaseReference
        get() = db.child("liveBuses")

    fun busRef(busId: String): DatabaseReference {
        val safeKey = busId.replace("/", "_").replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
        return liveBuses.child(safeKey)
    }
}
