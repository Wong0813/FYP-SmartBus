package com.upsi.smartbus.feature.admin

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.upsi.smartbus.feature.auth.LoginActivity
import com.upsi.smartbus.feature.profile.ProfileFragment
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.databinding.ActivityAdminBinding
import com.upsi.smartbus.feature.admin.accounts.AdminAccountsFragment
import com.upsi.smartbus.feature.admin.dashboard.AdminDashboardFragment
import com.upsi.smartbus.feature.admin.fleet.AdminFleetFragment
import com.upsi.smartbus.feature.admin.map.AdminLiveMapFragment
import com.upsi.smartbus.feature.admin.routes.AdminRoutesFragment
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminDataSeeder

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verifyAdminAccess()
        AdminDataSeeder.seedIfNeeded(this) {
            RouteRepository.loadRoutes { }
            AccountSeeder.seedAccountsIfNeeded(this)
        }
        setupDrawer()
        loadDrawerHeader()

        if (savedInstanceState == null) {
            loadFragment(AdminDashboardFragment(), getString(R.string.nav_dashboard))
            binding.navView.setCheckedItem(R.id.nav_dashboard)
        }
    }

    private fun verifyAdminAccess() {
        val uid = auth.currentUser?.uid ?: run {
            redirectToLogin()
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.uppercase()
                if (role != "ADMIN") {
                    Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show()
                    redirectToLogin()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to verify admin role", Toast.LENGTH_SHORT).show()
            }
    }

    private fun redirectToLogin() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setupDrawer() {
        binding.btnDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(AdminDashboardFragment(), getString(R.string.nav_dashboard))
                    true
                }
                R.id.nav_live_map -> {
                    loadFragment(AdminLiveMapFragment(), "Live Map")
                    true
                }
                R.id.nav_manage_accounts -> {
                    loadFragment(AdminAccountsFragment(), getString(R.string.nav_manage_accounts))
                    true
                }
                R.id.nav_bus_fleet -> {
                    loadFragment(AdminFleetFragment(), getString(R.string.nav_bus_fleet))
                    true
                }
                R.id.nav_route_config -> {
                    loadFragment(AdminRoutesFragment(), getString(R.string.nav_route_config))
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
                tvName.text = doc.getString("name") ?: "Admin"
                tvRole.text = doc.getString("role") ?: "ADMIN"
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
