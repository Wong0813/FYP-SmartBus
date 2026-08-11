package com.upsi.smartbus

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        loadDrawerHeader()

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(AdminDashboardFragment(), getString(R.string.nav_dashboard))
            binding.navView.setCheckedItem(R.id.nav_dashboard)
        }
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
