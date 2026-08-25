package com.upsi.smartbus.core.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirestoreHelper {
    const val DATABASE_ID = "ai-studio-ab96b68a-dd5e-4230-9625-53063f2e4521"

    val db: FirebaseFirestore by lazy {
        try {
            val app = FirebaseApp.getInstance()
            val instance = try {
                FirebaseFirestore.getInstance(app, DATABASE_ID)
            } catch (e: Exception) {
                FirebaseFirestore.getInstance(app)
            }

            // Enable persistent disk cache (100 MB) so data loads instantly on re-open
            // and works offline. This replaces the previous setPersistenceEnabled(false).
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                        .build()
                )
                .build()
            instance.firestoreSettings = settings

            Log.d("FirestoreHelper", "Firestore initialized with 100MB persistent disk cache")
            instance
        } catch (e: Exception) {
            Log.e("FirestoreHelper", "Fallback default Firestore initialization", e)
            FirebaseFirestore.getInstance()
        }
    }
}

