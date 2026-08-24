package com.upsi.smartbus.feature.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
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
import com.upsi.smartbus.feature.auth.LoginActivity
import com.upsi.smartbus.feature.profile.ProfileFragment

class AdminActivity : AppCompatActivity() {

    enum class AdminNavDestination {
        DASHBOARD, LIVE_MAP, ACCOUNTS, FLEET, ROUTES, PROFILE
    }

    private lateinit var binding: ActivityAdminBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }

    private var currentDestination: AdminNavDestination = AdminNavDestination.DASHBOARD
    private var isBackMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verifyAdminAccess()
        AdminDataSeeder.seedIfNeeded(this) {
            RouteRepository.loadRoutes { }
            AccountSeeder.seedAccountsIfNeeded(this)
        }
        setupDrawerListeners()
        loadDrawerHeader()

        if (savedInstanceState == null) {
            returnToDashboard()
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

    private fun setupDrawerListeners() {
        binding.btnDrawer.setOnClickListener {
            if (isBackMode) {
                returnToDashboard()
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        val d = binding.customDrawer

        // 1. Dashboard
        d.itemNavDashboard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            returnToDashboard()
        }

        // 2. Live Map
        d.itemNavLiveMap.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            isBackMode = false
            updateToolbarState(isBack = false, title = "Live Map")
            applyNavSelection(AdminNavDestination.LIVE_MAP)
            loadFragment(AdminLiveMapFragment(), "Live Map")
        }

        // 3. Manage Accounts
        d.itemNavAccounts.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            isBackMode = false
            updateToolbarState(isBack = false, title = getString(R.string.nav_manage_accounts))
            applyNavSelection(AdminNavDestination.ACCOUNTS)
            loadFragment(AdminAccountsFragment(), getString(R.string.nav_manage_accounts))
        }

        // 4. Bus Fleet
        d.itemNavFleet.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            isBackMode = false
            updateToolbarState(isBack = false, title = getString(R.string.nav_bus_fleet))
            applyNavSelection(AdminNavDestination.FLEET)
            loadFragment(AdminFleetFragment(), getString(R.string.nav_bus_fleet))
        }

        // 5. Route Config
        d.itemNavRoutes.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            isBackMode = false
            updateToolbarState(isBack = false, title = getString(R.string.nav_route_config))
            applyNavSelection(AdminNavDestination.ROUTES)
            loadFragment(AdminRoutesFragment(), getString(R.string.nav_route_config))
        }

        // 6. Profile
        d.itemNavProfile.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            isBackMode = false
            updateToolbarState(isBack = false, title = getString(R.string.nav_profile))
            applyNavSelection(AdminNavDestination.PROFILE)
            loadFragment(ProfileFragment(), getString(R.string.nav_profile))
        }

        // 7. Sign Out
        d.itemNavSignOut.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            signOut()
        }
    }

    private fun applyNavSelection(dest: AdminNavDestination) {
        currentDestination = dest
        val d = binding.customDrawer

        val allItems = listOf(
            Triple(d.itemNavDashboard, d.boxNavDashboard to d.iconNavDashboard, d.tvNavDashboard to d.indNavDashboard),
            Triple(d.itemNavLiveMap, d.boxNavLiveMap to d.iconNavLiveMap, d.tvNavLiveMap to d.indNavLiveMap),
            Triple(d.itemNavAccounts, d.boxNavAccounts to d.iconNavAccounts, d.tvNavAccounts to d.indNavAccounts),
            Triple(d.itemNavFleet, d.boxNavFleet to d.iconNavFleet, d.tvNavFleet to d.indNavFleet),
            Triple(d.itemNavRoutes, d.boxNavRoutes to d.iconNavRoutes, d.tvNavRoutes to d.indNavRoutes),
            Triple(d.itemNavProfile, d.boxNavProfile to d.iconNavProfile, d.tvNavProfile to d.indNavProfile)
        )

        val activeIndex = when (dest) {
            AdminNavDestination.DASHBOARD -> 0
            AdminNavDestination.LIVE_MAP -> 1
            AdminNavDestination.ACCOUNTS -> 2
            AdminNavDestination.FLEET -> 3
            AdminNavDestination.ROUTES -> 4
            AdminNavDestination.PROFILE -> 5
        }

        for ((idx, entry) in allItems.withIndex()) {
            val (rowLayout, boxIcon) = entry
            val (box, icon) = boxIcon
            val (tv, ind) = entry.third
            val isActive = idx == activeIndex

            if (isActive) {
                rowLayout.setBackgroundResource(R.drawable.bg_drawer_item_active)
                box.setBackgroundResource(R.drawable.bg_drawer_icon_active)
                icon.setColorFilter(Color.WHITE)
                tv.setTextColor(ContextCompat.getColor(this, R.color.crimson_primary))
                tv.setTypeface(null, Typeface.BOLD)
                ind.visibility = View.VISIBLE
            } else {
                rowLayout.setBackgroundColor(Color.TRANSPARENT)
                box.setBackgroundResource(R.drawable.bg_drawer_icon_inactive)
                icon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tv.setTypeface(null, Typeface.NORMAL)
                ind.visibility = View.GONE
            }
        }
    }

    private fun loadDrawerHeader() {
        val d = binding.customDrawer
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                d.tvDrawerName.text = doc.getString("name") ?: "Admin"
                d.tvDrawerRole.text = doc.getString("role")?.uppercase() ?: "ADMIN"
            }
    }

    /**
     * Navigates to Live Map from Schedule Dashboard with a dedicated back button.
     */
    fun navigateToLiveMap(targetRouteName: String? = null) {
        isBackMode = true
        applyNavSelection(AdminNavDestination.LIVE_MAP)
        updateToolbarState(isBack = true, title = "Live Map")
        val mapFrag = AdminLiveMapFragment.newInstance(targetRouteName)
        loadFragment(mapFrag, "Live Map")
    }

    /**
     * Returns to Schedule Dashboard and resets Toolbar to hamburger menu.
     */
    fun returnToDashboard() {
        isBackMode = false
        applyNavSelection(AdminNavDestination.DASHBOARD)
        updateToolbarState(isBack = false, title = getString(R.string.nav_dashboard))
        loadFragment(AdminDashboardFragment(), getString(R.string.nav_dashboard))
    }

    fun navigateToAccounts() {
        isBackMode = false
        applyNavSelection(AdminNavDestination.ACCOUNTS)
        loadFragment(AdminAccountsFragment(), getString(R.string.nav_manage_accounts))
    }

    private fun updateToolbarState(isBack: Boolean, title: String) {
        binding.tvToolbarTitle.text = title
        if (isBack) {
            binding.btnDrawer.setImageResource(R.drawable.ic_arrow_back)
            binding.btnDrawer.contentDescription = "Back to Dashboard"
        } else {
            binding.btnDrawer.setImageResource(R.drawable.ic_menu_burger)
            binding.btnDrawer.contentDescription = getString(R.string.navigation_drawer_open)
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun loadFragment(fragment: Fragment, title: String) {
        binding.tvToolbarTitle.text = title
        binding.layoutAdminToolbar.visibility = View.GONE
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
        } else if (isBackMode) {
            returnToDashboard()
        } else {
            super.onBackPressed()
        }
    }
}
