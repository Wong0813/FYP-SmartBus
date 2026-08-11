package com.upsi.smartbus

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.databinding.ActivityStudentBinding

class StudentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        loadDrawerHeader()

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(StudentMapFragment(), getString(R.string.nav_home_map))
            binding.navView.setCheckedItem(R.id.nav_home_map)
        }
    }

    private fun setupDrawer() {
        binding.btnDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_home_map -> {
                    loadFragment(StudentMapFragment(), getString(R.string.nav_home_map))
                    true
                }
                R.id.nav_active_buses -> {
                    loadFragment(ActiveBusesFragment(), getString(R.string.nav_active_buses))
                    true
                }
                R.id.nav_route_schedules -> {
                    loadFragment(RouteSchedulesFragment(), getString(R.string.nav_route_schedules))
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment(), getString(R.string.nav_profile))
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

        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                tvName.text = doc.getString("name") ?: "Student"
                tvRole.text = doc.getString("role") ?: "STUDENT"
            }
    }

    private fun loadFragment(fragment: Fragment, title: String) {
        binding.tvToolbarTitle.text = title
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
