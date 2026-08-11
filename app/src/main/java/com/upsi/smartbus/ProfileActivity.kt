package com.upsi.smartbus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.model.UserProfile

/**
 * ProfileActivity - standalone profile screen.
 * The main profile is now handled by ProfileFragment inside drawer activities.
 * This activity serves as a fallback/direct-launch option.
 */
class ProfileActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the ProfileFragment directly
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ProfileFragment())
                .commit()
        }
    }
}
