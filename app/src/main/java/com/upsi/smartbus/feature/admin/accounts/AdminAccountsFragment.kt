package com.upsi.smartbus.feature.admin.accounts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.UserProfile
import com.upsi.smartbus.core.model.UserRole
import com.upsi.smartbus.databinding.FragmentAdminAccountsBinding
import com.upsi.smartbus.feature.admin.AdminActivity
import com.upsi.smartbus.ui.adapter.AccountListAdapter
import com.upsi.smartbus.ui.adapter.AccountListItem
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import java.io.ByteArrayOutputStream

class AdminAccountsFragment : Fragment() {

    private var _binding: FragmentAdminAccountsBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private val allUsers = mutableListOf<UserProfile>()
    private val displayItems = mutableListOf<AccountListItem>()
    private lateinit var accountAdapter: AccountListAdapter
    private var usersListener: ListenerRegistration? = null
    private var selectedRoleFilter = "ALL"
    private var searchQuery = ""
    private val routeNames = mutableListOf<String>()

    private var pendingPhotoCallback: ((String) -> Unit)? = null
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val resized = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                val outputStream = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                val photoUrl = "data:image/jpeg;base64,$base64"
                pendingPhotoCallback?.invoke(photoUrl)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnHeroDrawer.setOnClickListener {
            (activity as? AdminActivity)?.openDrawer()
        }
        setupSearch()
        setupFilterTabs()
        setupActionButtons()
        setupRoleSpinner()
        setupCreateAccount()
        setupRecyclerView()
        loadRoutes()
        listenToUsers()
    }

    private fun loadRoutes() {
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            routeNames.clear()
            routeNames.addAll(routes.map { it.name })
            val adapter = ArrayAdapter(requireContext(), com.upsi.smartbus.R.layout.item_custom_spinner_selected, routeNames).apply {
                setDropDownViewResource(com.upsi.smartbus.R.layout.item_custom_spinner_dropdown)
            }
            binding.spinnerDriverRoute.adapter = adapter
        }
    }

    private fun setupSearch() {
        binding.etSearchUsers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty().lowercase()
                binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                rebuildDisplayList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchUsers.setText("")
        }
    }

    private fun setupFilterTabs() {
        val tabs = listOf(
            binding.filterAll to "ALL",
            binding.filterAdmin to "ADMIN",
            binding.filterDriver to "DRIVER",
            binding.filterStudent to "STUDENT"
        )

        tabs.forEach { (tabView, role) ->
            tabView.setOnClickListener {
                selectedRoleFilter = role
                updateTabStyles()
                rebuildDisplayList()
            }
        }
    }

    private fun updateTabStyles() {
        val tabs = listOf(
            binding.filterAll to "ALL",
            binding.filterAdmin to "ADMIN",
            binding.filterDriver to "DRIVER",
            binding.filterStudent to "STUDENT"
        )

        tabs.forEach { (view, role) ->
            val isSelected = selectedRoleFilter == role
            if (isSelected) {
                view.setBackgroundResource(R.drawable.bg_filter_tab_active)
                view.setTextColor(Color.WHITE)
            } else {
                view.setBackgroundResource(R.drawable.bg_filter_tab_inactive)
                view.setTextColor(Color.parseColor("#6B7280"))
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnToggleCreate.setOnClickListener {
            val show = binding.llCreateForm.visibility != View.VISIBLE
            binding.llCreateForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.tvNewAccountBtnText.text = if (show) "✕ Close Form" else "+ New Account"
        }
    }

    private fun setupRoleSpinner() {
        val roles = listOf("Student", "Driver", "Admin")
        val adapter = ArrayAdapter(requireContext(), com.upsi.smartbus.R.layout.item_custom_spinner_selected, roles).apply {
            setDropDownViewResource(com.upsi.smartbus.R.layout.item_custom_spinner_dropdown)
        }
        binding.spinnerRole.adapter = adapter

        binding.spinnerRole.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // Student
                        binding.llStudentFields.visibility = View.VISIBLE
                        binding.llDriverFields.visibility = View.GONE
                        binding.etStaffNo.hint = "Matric No (e.g. D20211099999)"
                    }
                    1 -> { // Driver
                        binding.llStudentFields.visibility = View.GONE
                        binding.llDriverFields.visibility = View.VISIBLE
                        binding.etStaffNo.hint = "Driver ID / Staff No (e.g. DRV-01)"
                    }
                    2 -> { // Admin
                        binding.llStudentFields.visibility = View.GONE
                        binding.llDriverFields.visibility = View.GONE
                        binding.etStaffNo.hint = "Staff No (e.g. ADM-01)"
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        accountAdapter = AccountListAdapter(
            displayItems,
            onDelete = { deleteUser(it) },
            onEdit = { showEditUserDialog(it) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = accountAdapter
    }

    private fun listenToUsers() {
        // Show cached real data first if present
        com.upsi.smartbus.core.data.AppCache.getCachedUsers()?.let { cached ->
            allUsers.clear()
            allUsers.addAll(cached)
            updateHeroStats()
            rebuildDisplayList()
        }

        // Fetch real collection from Firestore
        usersListener = null
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null || snapshot == null) return@addOnSuccessListener
                if (!snapshot.isEmpty) {
                    allUsers.clear()
                    val userList = mutableListOf<UserProfile>()
                    for (doc in snapshot.documents) {
                        doc.toObject(UserProfile::class.java)?.let { user ->
                            userList.add(user.copy(uid = doc.id))
                        }
                    }

                    // Group by lowercased email to deduplicate (e.g. admin_01 vs real Auth UID)
                    val grouped = userList.groupBy { it.email.lowercase().trim() }
                    val duplicatesToDelete = mutableListOf<String>()

                    for ((email, docs) in grouped) {
                        if (email.isEmpty()) {
                            allUsers.addAll(docs)
                            continue
                        }
                        if (docs.size == 1) {
                            allUsers.add(docs.first())
                        } else {
                            // Multiple documents for the same email:
                            // Prefer the one with a real Auth UID (not starting with admin_ / driver_ / student_demo_)
                            // or the one with the most complete profile details.
                            val isPlaceholder = { u: UserProfile ->
                                u.uid.startsWith("admin_") || u.uid.startsWith("driver_") || u.uid.startsWith("student_demo_")
                            }
                            val bestDoc = docs.firstOrNull { !isPlaceholder(it) } ?: docs.first()
                            allUsers.add(bestDoc)

                            // Mark the stale placeholder documents for deletion to permanently clean up Firestore
                            for (stale in docs) {
                                if (stale.uid != bestDoc.uid && isPlaceholder(stale)) {
                                    duplicatesToDelete.add(stale.uid)
                                }
                            }
                        }
                    }

                    // Clean up stale placeholder documents from Firestore in batch
                    if (duplicatesToDelete.isNotEmpty()) {
                        val batch = db.batch()
                        for (staleId in duplicatesToDelete) {
                            batch.delete(db.collection("users").document(staleId))
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                android.util.Log.d("AdminAccounts", "Cleaned up ${duplicatesToDelete.size} duplicate documents from Firestore")
                            }
                            .addOnFailureListener { err ->
                                android.util.Log.w("AdminAccounts", "Failed to delete stale duplicate docs", err)
                            }
                    }

                    com.upsi.smartbus.core.data.AppCache.putUsers(allUsers.toList())
                    updateHeroStats()
                    rebuildDisplayList()
                } else if (allUsers.isEmpty()) {
                    loadFallbackAccounts()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminAccounts", "Failed to fetch users from Firestore", e)
                if (_binding != null) {
                    if (allUsers.isEmpty()) {
                        loadFallbackAccounts()
                    }
                    Toast.makeText(requireContext(), "Firestore Notice: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun loadFallbackAccounts() {
        if (allUsers.isNotEmpty()) return
        val list = mutableListOf<UserProfile>()
        list.add(
            UserProfile(
                uid = "admin_01",
                name = "UPSI Bus Admin",
                email = "admin@upsi.edu.my",
                role = "ADMIN",
                staffNo = "ADM-01",
                accountType = "OFFICIAL"
            )
        )
        AccountSeeder.alignedEntries().forEach { entry ->
            list.add(
                UserProfile(
                    uid = AccountSeeder.driverDocId(entry.number),
                    name = entry.driver.driverName,
                    email = "driver${entry.number}@upsi.edu.my",
                    role = "DRIVER",
                    staffNo = "DRV-${String.format(java.util.Locale.US, "%02d", entry.number)}",
                    assignedRoute = entry.route.name,
                    assignedBus = entry.driver.busId,
                    plateNumber = entry.driver.plateNumber,
                    accountType = "OFFICIAL"
                )
            )
        }
        list.add(UserProfile(uid = "student_01", name = "Ahmad Bin Ali", email = "student1@upsi.edu.my", role = "STUDENT", staffNo = "STU-001", faculty = "FCI", program = "Software Engineering", accountType = "OFFICIAL"))
        list.add(UserProfile(uid = "student_02", name = "Siti Nurhaliza", email = "student2@upsi.edu.my", role = "STUDENT", staffNo = "STU-002", faculty = "FPM", program = "Pendidikan", accountType = "OFFICIAL"))
        list.add(UserProfile(uid = "student_03", name = "Muhammad Hafiz", email = "student3@upsi.edu.my", role = "STUDENT", staffNo = "STU-003", faculty = "FSS", program = "Pengurusan", accountType = "OFFICIAL"))

        allUsers.clear()
        allUsers.addAll(list)
        com.upsi.smartbus.core.data.AppCache.putUsers(allUsers.toList())
        updateHeroStats()
        rebuildDisplayList()
    }

    private fun updateHeroStats() {
        val total = allUsers.size
        val admins = allUsers.count { it.role.equals("ADMIN", true) }
        val drivers = allUsers.count { it.role.equals("DRIVER", true) }
        val students = allUsers.count { it.role.equals("STUDENT", true) }

        binding.tvTotalAccounts.text = "$total"
        binding.tvCountAdmin.text = "$admins"
        binding.tvCountDriver.text = "$drivers"
        binding.tvCountStudent.text = "$students"
    }

    private fun rebuildDisplayList() {
        displayItems.clear()
        val filtered = allUsers.filter { user ->
            val matchesRole = when (selectedRoleFilter) {
                "ADMIN" -> user.role.equals("ADMIN", true)
                "DRIVER" -> user.role.equals("DRIVER", true)
                "STUDENT" -> user.role.equals("STUDENT", true)
                else -> true
            }
            val matchesSearch = if (searchQuery.isEmpty()) true else {
                user.name.lowercase().contains(searchQuery) ||
                        user.email.lowercase().contains(searchQuery) ||
                        user.staffNo.lowercase().contains(searchQuery) ||
                        user.assignedRoute.lowercase().contains(searchQuery) ||
                        user.assignedBus.lowercase().contains(searchQuery) ||
                        user.faculty.lowercase().contains(searchQuery)
            }
            matchesRole && matchesSearch
        }

        val admins = filtered.filter { it.role.equals("ADMIN", true) }
        val drivers = AdminSortHelper.sortDrivers(filtered.filter { it.role.equals("DRIVER", true) })
        val students = filtered.filter { it.role.equals("STUDENT", true) }

        if (selectedRoleFilter == "ALL") {
            if (admins.isNotEmpty()) {
                displayItems.add(AccountListItem.Header("ADMINISTRATORS", admins.size))
                admins.forEach { displayItems.add(AccountListItem.User(it)) }
            }
            if (drivers.isNotEmpty()) {
                displayItems.add(AccountListItem.Header("DRIVERS", drivers.size))
                drivers.forEach { displayItems.add(AccountListItem.User(it)) }
            }
            if (students.isNotEmpty()) {
                displayItems.add(AccountListItem.Header("STUDENTS", students.size))
                students.forEach { displayItems.add(AccountListItem.User(it)) }
            }
        } else {
            val list = when (selectedRoleFilter) {
                "ADMIN" -> admins
                "DRIVER" -> drivers
                else -> students
            }
            if (list.isNotEmpty()) {
                val headerTitle = when (selectedRoleFilter) {
                    "ADMIN" -> "ADMINISTRATORS"
                    "DRIVER" -> "DRIVERS"
                    else -> "STUDENTS"
                }
                displayItems.add(AccountListItem.Header(headerTitle, list.size))
                list.forEach { displayItems.add(AccountListItem.User(it)) }
            }
        }

        val isEmpty = displayItems.isEmpty()
        binding.llEmptyUsers.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvUsers.visibility = if (isEmpty) View.GONE else View.VISIBLE

        accountAdapter.notifyDataSetChanged()
    }

    private fun setupCreateAccount() {
        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etAdminUsername.text.toString().trim().lowercase()
            val staffNo = binding.etStaffNo.text.toString().trim()
            val pass = binding.etAdminPassword.text.toString().trim()
            val role = when (binding.spinnerRole.selectedItemPosition) {
                1 -> UserRole.DRIVER
                2 -> UserRole.ADMIN
                else -> UserRole.STUDENT
            }

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = "user_${System.currentTimeMillis()}"
            val user = UserProfile(
                uid = uid,
                name = name,
                email = if (email.contains("@")) email else "$email@upsi.edu.my",
                role = role.name,
                staffNo = staffNo,
                faculty = if (role == UserRole.STUDENT) binding.etFaculty.text.toString().trim() else "",
                program = if (role == UserRole.STUDENT) binding.etProgram.text.toString().trim() else "",
                assignedBus = if (role == UserRole.DRIVER) binding.etDriverBus.text.toString().trim() else "",
                assignedRoute = if (role == UserRole.DRIVER) binding.spinnerDriverRoute.selectedItem?.toString().orEmpty() else "",
                plateNumber = if (role == UserRole.DRIVER) binding.etDriverPlate.text.toString().trim() else "",
                accountType = "OFFICIAL"
            )

            // 1. Immediately update UI (instant feedback)
            allUsers.add(user)
            updateHeroStats()
            rebuildDisplayList()
            clearCreateForm()
            binding.llCreateForm.visibility = View.GONE
            binding.tvNewAccountBtnText.text = "+ New Account"
            binding.btnCreateAccount.isEnabled = true
            binding.btnCreateAccount.text = "Create Account"
            Toast.makeText(requireContext(), "Account created successfully", Toast.LENGTH_SHORT).show()

            // 2. Persist to Firestore & cache in background
            com.upsi.smartbus.core.data.AppCache.putUsers(allUsers.toList())
            db.collection("users").document(uid).set(user)
        }
    }


    private fun clearCreateForm() {
        binding.etName.setText("")
        binding.etAdminUsername.setText("")
        binding.etStaffNo.setText("")
        binding.etAdminPassword.setText("")
        binding.etFaculty.setText("")
        binding.etProgram.setText("")
        binding.etDriverBus.setText("")
        binding.etDriverPlate.setText("")
    }

    private fun deleteUser(user: UserProfile) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete ${user.name} (${user.email})?")
            .setPositiveButton("Delete") { _, _ ->
                // Immediately update UI
                allUsers.removeAll { it.uid == user.uid }
                com.upsi.smartbus.core.data.AppCache.putUsers(allUsers.toList())
                updateHeroStats()
                rebuildDisplayList()
                Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show()
                // Persist to Firestore in background
                db.collection("users").document(user.uid).delete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditUserDialog(user: UserProfile) {
        val helper = AdminAccountDialogHelper(
            context = requireContext(),
            layoutInflater = layoutInflater,
            routeNames = routeNames,
            onPickPhotoRequested = { onPhotoCallback ->
                pendingPhotoCallback = onPhotoCallback
                pickImageLauncher.launch("image/*")
            }
        )
        helper.showEditUserDialog(
            user = user,
            screenWidthPixels = resources.displayMetrics.widthPixels
        ) { updatedUser, updates ->
            val targetIdx = allUsers.indexOfFirst { it.uid == user.uid }
            if (targetIdx >= 0) {
                allUsers[targetIdx] = updatedUser
                rebuildDisplayList()
            }
            db.collection("users").document(user.uid).update(updates)
        }
    }

    override fun onDestroyView() {
        usersListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
