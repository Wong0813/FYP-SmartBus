package com.upsi.smartbus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.upsi.smartbus.data.FirestoreHelper

class MainActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    handleRoleRedirection(doc.getString("role"))
                } else {
                    handleRoleRedirection(null)
                }
            }
            .addOnFailureListener {
                handleRoleRedirection(null)
            }
    }

    private fun handleRoleRedirection(roleStr: String?) {
        val target = when (roleStr?.uppercase()) {
            "STUDENT" -> StudentActivity::class.java
            "DRIVER"  -> DriverActivity::class.java
            "ADMIN"   -> AdminActivity::class.java
            else      -> StudentActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
