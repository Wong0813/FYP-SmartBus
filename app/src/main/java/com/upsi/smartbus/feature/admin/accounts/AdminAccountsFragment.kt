package com.upsi.smartbus.feature.admin.accounts

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.feature.auth.LoginActivity
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.UserProfile
import com.upsi.smartbus.databinding.FragmentAdminAccountsBinding
import com.upsi.smartbus.databinding.ItemAccountSectionHeaderBinding
import com.upsi.smartbus.databinding.ItemUserCardBinding
import com.upsi.smartbus.feature.admin.seeder.AccountSeeder
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper
import com.upsi.smartbus.feature.admin.seeder.AdminUiHelper

class AdminAccountsFragment : Fragment() {

    private var _binding: FragmentAdminAccountsBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val allUsers = mutableListOf<UserProfile>()
    private val displayItems = mutableListOf<AccountListItem>()
    private lateinit var listAdapter: AccountListAdapter
    private var usersListener: ListenerRegistration? = null
    private var roleFilter = "ALL"
    private var searchQuery = ""
    private val routeNames = mutableListOf<String>()

    sealed class AccountListItem {
        data class Header(val title: String, val count: Int, val emoji: String) : AccountListItem()
        data class User(val profile: UserProfile) : AccountListItem()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRoleSpinner()
        setupDriverRouteSpinner()
        setupRecyclerView()
        setupCreateButton()
        setupFilters()
        setupSearch()
        setupSyncButton()
        setupToggleCreate()
        listenToUsers()
    }

    private fun setupToggleCreate() {
        binding.btnToggleCreate.setOnClickListener {
            val show = binding.llCreateForm.visibility != View.VISIBLE
            binding.llCreateForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnToggleCreate.text = if (show) "✕ Close" else "+ New Account"
        }
    }

    private fun setupSyncButton() {
        binding.btnSyncOfficial.setOnClickListener {
            binding.btnSyncOfficial.isEnabled = false
            binding.tvSyncStatus.visibility = View.VISIBLE
            binding.tvSyncStatus.text = "Syncing routes + 20 drivers (Driver 01 → Laluan 1)..."

            AccountSeeder.seedOfficialRoutes {
                AccountSeeder.seedOfficialDrivers(
                    onProgress = { current, total ->
                        if (_binding == null) return@seedOfficialDrivers
                        binding.tvSyncStatus.text = "Uploading drivers $current / $total..."
                    },
                    onComplete = { success, failed ->
                        if (_binding == null) return@seedOfficialDrivers
                        AccountSeeder.seedOfficialBuses {
                            if (_binding == null) return@seedOfficialBuses
                            AccountSeeder.seedDemoStudents {
                                if (_binding == null) return@seedDemoStudents
                                binding.btnSyncOfficial.isEnabled = true
                                binding.tvSyncStatus.text =
                                    "✅ Synced $success drivers + buses to Firestore" +
                                        if (failed > 0) " ($failed failed)" else ""
                                Toast.makeText(
                                    requireContext(),
                                    "20 official drivers imported!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun setupSearch() {
        binding.etSearchUsers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty().lowercase()
                rebuildDisplayList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFilters() {
        val mapping = listOf(
            binding.filterAll to "ALL",
            binding.filterAdmin to "ADMIN",
            binding.filterDriver to "DRIVER",
            binding.filterStudent to "STUDENT"
        )
        mapping.forEach { (view, role) ->
            view.setOnClickListener {
                roleFilter = role
                updateFilterChips()
                rebuildDisplayList()
            }
        }
        binding.chipAdmin.setOnClickListener { selectFilter("ADMIN") }
        binding.chipDriver.setOnClickListener { selectFilter("DRIVER") }
        binding.chipStudent.setOnClickListener { selectFilter("STUDENT") }
    }

    private fun selectFilter(role: String) {
        roleFilter = role
        updateFilterChips()
        rebuildDisplayList()
    }

    private fun updateFilterChips() {
        val selectedBg = R.drawable.bg_filter_chip_selected
        val normalBg = R.drawable.bg_filter_chip
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.crimson_primary)
        val normalColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        val chips = listOf(
            binding.filterAll to "ALL",
            binding.filterAdmin to "ADMIN",
            binding.filterDriver to "DRIVER",
            binding.filterStudent to "STUDENT"
        )
        chips.forEach { (view, role) ->
            val selected = roleFilter == role
            view.setBackgroundResource(if (selected) selectedBg else normalBg)
            view.setTextColor(if (selected) selectedColor else normalColor)
            view.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
    }

    private fun setupRoleSpinner() {
        val roles = arrayOf("STUDENT", "DRIVER", "ADMIN")
        binding.spinnerRole.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, roles
        )
        binding.spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.llDriverFields.visibility = if (roles[position] == "DRIVER") View.VISIBLE else View.GONE
                binding.llStudentFields.visibility = if (roles[position] == "STUDENT") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDriverRouteSpinner() {
        RouteRepository.loadRoutes { routes ->
            if (_binding == null) return@loadRoutes
            routeNames.clear()
            routeNames.addAll(routes.map { it.name })
            binding.spinnerDriverRoute.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, routeNames
            )
        }
    }

    private fun setupRecyclerView() {
        listAdapter = AccountListAdapter(
            displayItems,
            onDelete = { deleteUser(it) },
            onEdit = { showEditUserDialog(it) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = listAdapter
    }

    private fun listenToUsers() {
        usersListener?.remove()
        usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                if (error != null) {
                    binding.tvEmptyUsers.visibility = View.VISIBLE
                    binding.tvEmptyUsers.text = "Failed to load: ${error.message}"
                    return@addSnapshotListener
                }
                allUsers.clear()
                for (doc in snapshot?.documents.orEmpty()) {
                    if (doc.id.startsWith("driver_laluan") ||
                        doc.id.startsWith("driver_shuttle") ||
                        doc.id.startsWith("driver_kitaran")) continue
                    val profile = doc.toObject(UserProfile::class.java) ?: continue
                    allUsers.add(profile.copy(uid = doc.id))
                }
                updateStats()
                rebuildDisplayList()
            }
    }

    private fun updateStats() {
        val admins = allUsers.count { it.role.equals("ADMIN", true) }
        val drivers = allUsers.count { it.role.equals("DRIVER", true) }
        val students = allUsers.count { it.role.equals("STUDENT", true) }
        binding.tvTotalAccounts.text = "${allUsers.size} Total Accounts"
        binding.tvCountAdmin.text = "$admins"
        binding.tvCountDriver.text = "$drivers"
        binding.tvCountStudent.text = "$students"
    }

    private fun rebuildDisplayList() {
        displayItems.clear()

        fun matchesSearch(user: UserProfile): Boolean {
            if (searchQuery.isEmpty()) return true
            return user.name.lowercase().contains(searchQuery) ||
                user.email.lowercase().contains(searchQuery) ||
                user.assignedRoute.lowercase().contains(searchQuery) ||
                user.staffNo.lowercase().contains(searchQuery) ||
                user.role.lowercase().contains(searchQuery)
        }

        val filtered = allUsers.filter { user ->
            val roleMatch = roleFilter == "ALL" || user.role.equals(roleFilter, true)
            roleMatch && matchesSearch(user)
        }

        if (roleFilter == "ALL" && searchQuery.isEmpty()) {
            listOf(
                Triple("ADMIN", "🛡️ Administrators", allUsers.filter { it.role.equals("ADMIN", true) }),
                Triple("DRIVER", "🚌 Drivers (Driver 01 → Laluan 1)", AdminSortHelper.sortDrivers(allUsers.filter { it.role.equals("DRIVER", true) })),
                Triple("STUDENT", "🎓 Students", allUsers.filter { it.role.equals("STUDENT", true) })
            ).forEach { (_, title, users) ->
                if (users.isNotEmpty()) {
                    displayItems.add(AccountListItem.Header(title, users.size, ""))
                    users.forEach { displayItems.add(AccountListItem.User(it)) }
                }
            }
        } else {
            val sorted = if (roleFilter == "DRIVER") AdminSortHelper.sortDrivers(filtered) else filtered.sortedBy { it.name }
            sorted.forEach { displayItems.add(AccountListItem.User(it)) }
        }

        binding.tvEmptyUsers.visibility =
            if (displayItems.none { it is AccountListItem.User }) View.VISIBLE else View.GONE
        listAdapter.notifyDataSetChanged()
        AdminUiHelper.expandRecyclerView(binding.rvUsers)
    }

    private fun showEditUserDialog(user: UserProfile) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etName = EditText(ctx).apply { hint = "Full Name"; setText(user.name) }
        layout.addView(etName)

        when (user.role.uppercase()) {
            "DRIVER" -> {
                val etBus = EditText(ctx).apply { hint = "Bus ID"; setText(user.assignedBus) }
                val etPlate = EditText(ctx).apply { hint = "Plate"; setText(user.plateNumber) }
                layout.addView(etBus); layout.addView(etPlate)
                val routesList = RouteRepository.getCachedRoutes().map { it.name }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Edit Driver")
                    .setView(layout)
                    .setPositiveButton("Save") { _, _ ->
                        updateUser(user.uid, mapOf(
                            "name" to etName.text.toString().trim(),
                            "assignedBus" to etBus.text.toString().trim(),
                            "plateNumber" to etPlate.text.toString().trim()
                        ))
                    }
                    .setNeutralButton("Change Route") { _, _ ->
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                            .setTitle("Reassign Route")
                            .setItems(routesList.toTypedArray()) { _, which ->
                                updateUser(user.uid, mapOf("assignedRoute" to routesList[which]))
                            }.show()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            "STUDENT" -> {
                val etFaculty = EditText(ctx).apply { hint = "Faculty"; setText(user.faculty) }
                val etProgram = EditText(ctx).apply { hint = "Program"; setText(user.program) }
                layout.addView(etFaculty); layout.addView(etProgram)
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Edit Student").setView(layout)
                    .setPositiveButton("Save") { _, _ ->
                        updateUser(user.uid, mapOf(
                            "name" to etName.text.toString().trim(),
                            "faculty" to etFaculty.text.toString().trim(),
                            "program" to etProgram.text.toString().trim()
                        ))
                    }.setNegativeButton("Cancel", null).show()
            }
            else -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                    .setTitle("Edit ${user.name}").setView(layout)
                    .setPositiveButton("Save") { _, _ ->
                        updateUser(user.uid, mapOf("name" to etName.text.toString().trim()))
                    }.setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun updateUser(uid: String, updates: Map<String, Any>) {
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupCreateButton() {
        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val username = binding.etAdminUsername.text.toString().trim()
            val staffNo = binding.etStaffNo.text.toString().trim()
            val password = binding.etAdminPassword.text.toString().trim()
            val role = binding.spinnerRole.selectedItem.toString()

            if (name.isEmpty() || username.isEmpty() || password.length < 6) {
                Toast.makeText(requireContext(), "Fill name, username & password (6+ chars)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val email = if (username.contains("@")) username else "$username@upsi.edu.my"
            binding.btnCreateAccount.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener
                    val profile = hashMapOf<String, Any>(
                        "uid" to uid, "name" to name, "email" to email,
                        "role" to role, "staffNo" to staffNo, "accountType" to "AUTH"
                    )
                    when (role) {
                        "DRIVER" -> {
                            profile["assignedRoute"] = routeNames.getOrElse(binding.spinnerDriverRoute.selectedItemPosition) { "" }
                            profile["assignedBus"] = binding.etDriverBus.text.toString().trim()
                            profile["plateNumber"] = binding.etDriverPlate.text.toString().trim()
                        }
                        "STUDENT" -> {
                            profile["faculty"] = binding.etFaculty.text.toString().trim()
                            profile["program"] = binding.etProgram.text.toString().trim()
                        }
                    }
                    db.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            auth.signOut()
                            Toast.makeText(requireContext(), "Created! Sign in again as admin.", Toast.LENGTH_LONG).show()
                            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            requireActivity().finish()
                        }
                }
                .addOnFailureListener { e ->
                    binding.btnCreateAccount.isEnabled = true
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteUser(user: UserProfile) {
        if (user.uid == auth.currentUser?.uid) {
            Toast.makeText(requireContext(), "Cannot delete your own admin account", Toast.LENGTH_SHORT).show()
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${user.name}?")
            .setMessage("Remove this account from Firestore?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("users").document(user.uid).delete()
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDestroyView() {
        usersListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    class AccountListAdapter(
        private val items: List<AccountListItem>,
        private val onDelete: (UserProfile) -> Unit,
        private val onEdit: (UserProfile) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_USER = 1
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is AccountListItem.Header -> TYPE_HEADER
            is AccountListItem.User -> TYPE_USER
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(ItemAccountSectionHeaderBinding.inflate(inflater, parent, false))
            } else {
                UserVH(ItemUserCardBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is AccountListItem.Header -> {
                    (holder as HeaderVH).binding.tvSectionTitle.text = item.title
                    holder.binding.tvSectionCount.text = "${item.count}"
                }
                is AccountListItem.User -> bindUser((holder as UserVH).binding, item.profile)
            }
        }

        private fun bindUser(b: ItemUserCardBinding, user: UserProfile) {
            b.tvUserName.text = user.name
            b.tvUserEmail.text = buildString {
                append(user.email)
                if (user.staffNo.isNotEmpty()) append(" • ${user.staffNo}")
            }
            b.tvUserRole.text = user.role.uppercase()
            b.tvUserInitial.text = user.name.firstOrNull()?.uppercase() ?: "?"

            val isPortal = user.accountType.equals("PORTAL", true) || user.uid.matches(Regex("driver_\\d{2}"))
            b.tvAccountType.visibility = if (isPortal) View.VISIBLE else View.GONE
            b.tvAccountType.text = if (isPortal) "PORTAL" else "AUTH"

            when (user.role.uppercase()) {
                "DRIVER" -> {
                    b.tvAssignedRoute.visibility = View.VISIBLE
                    val num = if (user.driverNumber > 0) user.driverNumber else AdminSortHelper.driverOrderKey(user)
                    b.tvDriverNumber.visibility = View.VISIBLE
                    b.tvDriverNumber.text = String.format("%02d", num)
                    b.tvAssignedRoute.text = "→ ${user.assignedRoute} • 🚌 ${user.assignedBus}"
                }
                "STUDENT" -> {
                    if (user.faculty.isNotEmpty() || user.program.isNotEmpty()) {
                        b.tvAssignedRoute.visibility = View.VISIBLE
                        b.tvAssignedRoute.text = "${user.faculty} • ${user.program}"
                    } else b.tvAssignedRoute.visibility = View.GONE
                }
                else -> b.tvAssignedRoute.visibility = View.GONE
            }

            val (avatarBg, textColor) = when (user.role.uppercase()) {
                "STUDENT" -> R.drawable.bg_avatar_student to R.color.status_moving
                "DRIVER" -> R.drawable.bg_avatar_driver to R.color.status_resting
                "ADMIN" -> R.drawable.bg_avatar_admin to R.color.crimson_primary
                else -> R.drawable.bg_avatar_student to R.color.status_offline
            }
            b.flAvatarContainer.setBackgroundResource(avatarBg)
            b.tvUserInitial.setTextColor(ContextCompat.getColor(b.root.context, textColor))
            b.btnDeleteUser.setOnClickListener { onDelete(user) }
            b.btnEditUser.setOnClickListener { onEdit(user) }
        }

        override fun getItemCount() = items.size

        class HeaderVH(val binding: ItemAccountSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        class UserVH(val binding: ItemUserCardBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
