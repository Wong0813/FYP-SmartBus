package com.upsi.smartbus.feature

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.upsi.smartbus.feature.auth.LoginActivity
import com.upsi.smartbus.feature.driver.DriverActivity
import com.upsi.smartbus.feature.student.StudentActivity
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.feature.admin.seeder.AdminDataSeeder

class MainActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = currentUser.uid
        val email = currentUser.email?.lowercase() ?: ""

        // 1. First try lookup by Auth UID directly
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val role = doc.getString("role")
                    handleRoleRedirection(role, email)
                } else {
                    // 2. If not found by UID, search by email in users collection
                    lookupRoleByEmail(email)
                }
            }
            .addOnFailureListener {
                lookupRoleByEmail(email)
            }
    }

    private fun lookupRoleByEmail(email: String) {
        if (email.isNotEmpty()) {
            db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) {
                        val doc = snap.documents.first()
                        val role = doc.getString("role")
                        handleRoleRedirection(role, email)
                    } else {
                        handleRoleRedirection(null, email)
                    }
                }
                .addOnFailureListener {
                    handleRoleRedirection(null, email)
                }
        } else {
            handleRoleRedirection(null, email)
        }
    }

    private fun handleRoleRedirection(roleStr: String?, email: String) {
        val resolvedRole = when {
            roleStr?.equals("ADMIN", true) == true || email.startsWith("admin") -> "ADMIN"
            roleStr?.equals("DRIVER", true) == true || email.startsWith("driver") -> "DRIVER"
            else -> "STUDENT"
        }

        val target = when (resolvedRole) {
            "ADMIN"   -> AdminActivity::class.java
            "DRIVER"  -> DriverActivity::class.java
            else      -> StudentActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
