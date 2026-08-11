package com.upsi.smartbus.core.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

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
            
            // Disable network persistence so writes go directly to remote cloud server and errors fail immediately
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
            instance.firestoreSettings = settings
            
            Log.d("FirestoreHelper", "Successfully initialized Firestore without local cache")
            instance
        } catch (e: Exception) {
            Log.e("FirestoreHelper", "Fallback default Firestore initialization", e)
            FirebaseFirestore.getInstance()
        }
    }
}
