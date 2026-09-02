package com.upsi.smartbus.core.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirestoreHelper {

    val db: FirebaseFirestore by lazy {
        try {
            val app = FirebaseApp.getInstance()
            // Connect directly to the standard (default) Firestore database
            val instance = FirebaseFirestore.getInstance(app)

            // Enable persistent disk cache (100 MB) for instant offline loading
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                        .build()
                )
                .build()
            instance.firestoreSettings = settings

            Log.d("FirestoreHelper", "Standard (default) Firestore initialized with 100MB disk cache")
            instance
        } catch (e: Exception) {
            Log.e("FirestoreHelper", "Fallback standard Firestore initialization", e)
            FirebaseFirestore.getInstance()
        }
    }
}

