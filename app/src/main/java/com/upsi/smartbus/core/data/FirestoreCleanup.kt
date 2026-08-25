package com.upsi.smartbus.core.data

import android.util.Log
import com.google.firebase.firestore.FieldValue

/**
 * One-time Firestore cleanup utility.
 * Removes live GPS fields from `buses/` documents since GPS data is now
 * stored exclusively in Firebase Realtime Database `liveBuses/{busId}`.
 *
 * Fields removed from each buses/{id} document:
 *   - location     (GeoPoint — replaced by RTDB lat/lng)
 *   - speed        (Double  — replaced by RTDB speed)
 *   - nextStop     (String  — replaced by RTDB nextStop)
 *   - distanceToNext (Double — replaced by RTDB distanceToNext)
 *   - status       (String  — replaced by RTDB status; keep only static "IDLE" default)
 *   - lastUpdated  (Long    — replaced by RTDB lastUpdated)
 *   - startStop    (String  — unused legacy field)
 *   - routeStops   (List    — redundant; route stops are in routes/ collection)
 *   - passengerCount (Int   — not actively used)
 *
 * Fields KEPT in Firestore buses/{id}:
 *   - id, name, plateNumber, licensePlate, routeName, capacity, photoUrl
 */
object FirestoreCleanup {

    private val TAG = "FirestoreCleanup"

    /**
     * Call this ONCE from admin panel. Safe to run multiple times (idempotent).
     * Removes GPS/live fields from all documents in `buses/` collection.
     */
    fun cleanBusGpsFields(onDone: (success: Int, failed: Int) -> Unit) {
        val db = FirestoreHelper.db
        val fieldsToRemove = mapOf(
            "location"        to FieldValue.delete(),
            "speed"           to FieldValue.delete(),
            "nextStop"        to FieldValue.delete(),
            "distanceToNext"  to FieldValue.delete(),
            "status"          to FieldValue.delete(),
            "lastUpdated"     to FieldValue.delete(),
            "startStop"       to FieldValue.delete(),
            "routeStops"      to FieldValue.delete(),
            "passengerCount"  to FieldValue.delete()
        )

        db.collection("buses").get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                if (docs.isEmpty()) {
                    Log.w(TAG, "No bus documents found in Firestore.")
                    onDone(0, 0)
                    return@addOnSuccessListener
                }

                var successCount = 0
                var failCount = 0
                val total = docs.size

                for (doc in docs) {
                    db.collection("buses").document(doc.id)
                        .update(fieldsToRemove)
                        .addOnSuccessListener {
                            successCount++
                            Log.d(TAG, "Cleaned bus ${doc.id} ($successCount/$total)")
                            if (successCount + failCount == total) {
                                AppCache.invalidateBuses()
                                onDone(successCount, failCount)
                            }
                        }
                        .addOnFailureListener { e ->
                            failCount++
                            Log.e(TAG, "Failed to clean bus ${doc.id}: ${e.message}")
                            if (successCount + failCount == total) {
                                AppCache.invalidateBuses()
                                onDone(successCount, failCount)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch buses collection: ${e.message}")
                onDone(0, -1)
            }
    }
}
