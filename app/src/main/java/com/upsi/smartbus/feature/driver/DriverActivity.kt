package com.upsi.smartbus.feature.driver

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.feature.auth.LoginActivity
import com.upsi.smartbus.feature.profile.ProfileFragment
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.databinding.ActivityDriverBinding
import com.upsi.smartbus.feature.driver.control.DriverControlFragment

class DriverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RouteRepository.loadRoutes { }
        setupDrawer()
        loadDrawerHeader()

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(DriverControlFragment())
            binding.navView.setCheckedItem(R.id.nav_control_desk)
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun setupDrawer() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_control_desk -> {
                    loadFragment(DriverControlFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                R.id.nav_sign_out -> {
                    signOut()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val tvName = headerView.findViewById<TextView>(R.id.tvDrawerName)
        val tvRole = headerView.findViewById<TextView>(R.id.tvDrawerRole)
        val ivAvatar = headerView.findViewById<android.widget.ImageView>(R.id.ivDrawerAvatar)

        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                tvName.text = doc.getString("name") ?: "Driver"
                tvRole.text = doc.getString("role") ?: "DRIVER"
                val photoUrl = doc.getString("photoUrl").orEmpty()
                loadAvatar(ivAvatar, photoUrl)
            }
    }

    private fun loadAvatar(iv: android.widget.ImageView?, photoUrl: String) {
        if (iv == null) return
        if (photoUrl.startsWith("data:image")) {
            try {
                val base64Part = photoUrl.substringAfter("base64,")
                val decodedBytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    iv.setImageBitmap(bitmap)
                    iv.setPadding(0, 0, 0, 0)
                    iv.imageTintList = null
                    iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    return
                }
            } catch (_: Exception) {}
        }
        iv.setImageResource(R.drawable.ic_nav_profile)
        val p = (iv.resources.displayMetrics.density * 12).toInt()
        iv.setPadding(p, p, p, p)
        iv.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#64748B"))
        iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment)
            .commit()
    }

    private fun signOut() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
