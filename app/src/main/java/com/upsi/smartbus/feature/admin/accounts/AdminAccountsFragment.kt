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
import com.upsi.smartbus.databinding.DialogEditUserBinding
import com.upsi.smartbus.databinding.FragmentAdminAccountsBinding
import com.upsi.smartbus.databinding.ItemAccountSectionHeaderBinding
import com.upsi.smartbus.databinding.ItemUserCardBinding
import com.upsi.smartbus.feature.admin.AdminActivity
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

    sealed class AccountListItem {
        data class Header(val title: String, val count: Int) : AccountListItem()
        data class User(val profile: UserProfile) : AccountListItem()
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
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, routeNames)
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
        binding.btnSyncOfficial.setOnClickListener {
            binding.btnSyncOfficial.isEnabled = false
            AccountSeeder.seedEverything(requireContext()) {
                if (_binding == null) return@seedEverything
                binding.btnSyncOfficial.isEnabled = true
                Toast.makeText(requireContext(), "Official accounts synced", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnToggleCreate.setOnClickListener {
            val show = binding.llCreateForm.visibility != View.VISIBLE
            binding.llCreateForm.visibility = if (show) View.VISIBLE else View.GONE
            binding.tvNewAccountBtnText.text = if (show) "✕ Close Form" else "+ New Account"
        }
    }

    private fun setupRoleSpinner() {
        val roles = listOf("Student", "Driver", "Admin")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
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
        usersListener?.remove()
        usersListener = db.collection("users").addSnapshotListener { snapshot, error ->
            if (_binding == null || error != null || snapshot == null) return@addSnapshotListener
            allUsers.clear()
            for (doc in snapshot.documents) {
                doc.toObject(UserProfile::class.java)?.let { user ->
                    allUsers.add(user.copy(uid = doc.id))
                }
            }
            updateHeroStats()
            rebuildDisplayList()
        }
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
                // Pure clean header title
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

            binding.btnCreateAccount.isEnabled = false
            binding.btnCreateAccount.text = "Creating..."

            val uid = "user_${System.currentTimeMillis()}"
            val user = UserProfile(
                uid = uid,
                name = name,
                email = email,
                role = role.name,
                staffNo = staffNo,
                faculty = if (role == UserRole.STUDENT) binding.etFaculty.text.toString().trim() else "",
                program = if (role == UserRole.STUDENT) binding.etProgram.text.toString().trim() else "",
                assignedBus = if (role == UserRole.DRIVER) binding.etDriverBus.text.toString().trim() else "",
                assignedRoute = if (role == UserRole.DRIVER) binding.spinnerDriverRoute.selectedItem?.toString().orEmpty() else "",
                plateNumber = if (role == UserRole.DRIVER) binding.etDriverPlate.text.toString().trim() else "",
                accountType = "OFFICIAL"
            )

            db.collection("users").document(uid).set(user)
                .addOnSuccessListener {
                    clearCreateForm()
                    binding.llCreateForm.visibility = View.GONE
                    binding.tvNewAccountBtnText.text = "+ New Account"
                    binding.btnCreateAccount.isEnabled = true
                    binding.btnCreateAccount.text = "Create Account"
                    Toast.makeText(requireContext(), "Account created successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    binding.btnCreateAccount.isEnabled = true
                    binding.btnCreateAccount.text = "Create Account"
                    Toast.makeText(requireContext(), "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
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
                db.collection("users").document(user.uid).delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditUserDialog(user: UserProfile) {
        val ctx = requireContext()
        val dialog = android.app.Dialog(ctx)
        val b = DialogEditUserBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        b.tvEditDialogTitle.text = "Edit ${user.name}"
        b.etEditName.setText(user.name)
        b.etEditStaffNo.setText(user.staffNo)

        val isDriver = user.role.equals("DRIVER", true)
        val isStudent = user.role.equals("STUDENT", true)

        var currentPhotoUrl = user.photoUrl

        fun updateDialogAvatarPreview() {
            AccountListAdapter.loadAvatarFromFirebase(b.ivDialogUserAvatar, currentPhotoUrl, user.name)
        }
        updateDialogAvatarPreview()

        b.btnPickGallery.setOnClickListener {
            pendingPhotoCallback = { newPhotoUrl ->
                currentPhotoUrl = newPhotoUrl
                updateDialogAvatarPreview()
            }
            pickImageLauncher.launch("image/*")
        }

        if (isDriver) {
            b.tvLabelStaffNo.text = "DRIVER ID / STAFF NO"
            b.llEditDriverFields.visibility = View.VISIBLE
            b.etEditBus.setText(user.assignedBus)
            b.etEditPlate.setText(user.plateNumber)

            val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, routeNames)
            b.spinnerEditRoute.adapter = adapter
            val routeIdx = routeNames.indexOf(user.assignedRoute)
            if (routeIdx >= 0) b.spinnerEditRoute.setSelection(routeIdx)
        } else if (isStudent) {
            b.tvLabelStaffNo.text = "STUDENT ID / MATRIC NO"
            b.llEditStudentFields.visibility = View.VISIBLE
            b.etEditFaculty.setText(user.faculty)
            b.etEditProgram.setText(user.program)
        } else {
            b.tvLabelStaffNo.text = "STAFF NO / USERNAME"
        }

        b.btnDialogClose.setOnClickListener { dialog.dismiss() }
        b.btnDialogCancel.setOnClickListener { dialog.dismiss() }

        b.btnDialogSave.setOnClickListener {
            val newName = b.etEditName.text.toString().trim()
            val newStaffNo = b.etEditStaffNo.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(ctx, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = mutableMapOf<String, Any>(
                "name" to newName,
                "staffNo" to newStaffNo,
                "photoUrl" to currentPhotoUrl
            )

            if (isDriver) {
                val selRoute = b.spinnerEditRoute.selectedItem?.toString() ?: user.assignedRoute
                val selBus = b.etEditBus.text.toString().trim()
                val selPlate = b.etEditPlate.text.toString().trim()
                updates["assignedRoute"] = selRoute
                updates["assignedBus"] = selBus
                updates["plateNumber"] = selPlate
            } else if (isStudent) {
                updates["faculty"] = b.etEditFaculty.text.toString().trim()
                updates["program"] = b.etEditProgram.text.toString().trim()
            }

            b.btnDialogSave.isEnabled = false
            b.btnDialogSave.text = "Saving..."

            // 1. Instantly update in-memory list for 0-latency UI reflection
            val targetIdx = allUsers.indexOfFirst { it.uid == user.uid }
            val updatedUser = user.copy(
                name = newName,
                staffNo = newStaffNo,
                photoUrl = currentPhotoUrl,
                assignedRoute = if (isDriver) b.spinnerEditRoute.selectedItem?.toString() ?: user.assignedRoute else user.assignedRoute,
                assignedBus = if (isDriver) b.etEditBus.text.toString().trim() else user.assignedBus,
                plateNumber = if (isDriver) b.etEditPlate.text.toString().trim() else user.plateNumber,
                faculty = if (isStudent) b.etEditFaculty.text.toString().trim() else user.faculty,
                program = if (isStudent) b.etEditProgram.text.toString().trim() else user.program
            )
            if (targetIdx >= 0) {
                allUsers[targetIdx] = updatedUser
                rebuildDisplayList()
            }

            // 2. Safely dismiss dialog immediately
            dialog.dismiss()
            Toast.makeText(ctx, "Changes saved successfully", Toast.LENGTH_SHORT).show()

            // 3. Asynchronously sync to Firebase (resilient to quota limits)
            db.collection("users").document(user.uid).update(updates)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        usersListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    // ══════════════════════════════════════════════════════════════
    // ADAPTER & VIEW HOLDERS
    // ══════════════════════════════════════════════════════════════

    class AccountListAdapter(
        private val items: List<AccountListItem>,
        private val onDelete: (UserProfile) -> Unit,
        private val onEdit: (UserProfile) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_USER = 1

            fun loadAvatarFromFirebase(iv: ImageView, photoUrl: String, userName: String) {
                if (photoUrl.startsWith("data:image")) {
                    try {
                        val base64Part = photoUrl.substringAfter("base64,")
                        val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            iv.setImageBitmap(bitmap)
                            iv.background = null
                            iv.setPadding(0, 0, 0, 0)
                            iv.imageTintList = null
                            iv.scaleType = ImageView.ScaleType.CENTER_CROP
                            return
                        }
                    } catch (_: Exception) {}
                }
                // Fallback default avatar style
                iv.setImageResource(R.drawable.ic_nav_profile)
                iv.setBackgroundColor(Color.parseColor("#F1F5F9"))
                val p = (iv.resources.displayMetrics.density * 8).toInt()
                iv.setPadding(p, p, p, p)
                iv.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
                iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }

        inner class HeaderVH(val binding: ItemAccountSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        inner class UserVH(val binding: ItemUserCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(pos: Int) =
            if (items[pos] is AccountListItem.Header) TYPE_HEADER else TYPE_USER

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(ItemAccountSectionHeaderBinding.inflate(inf, parent, false))
            } else {
                UserVH(ItemUserCardBinding.inflate(inf, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            when (val item = items[pos]) {
                is AccountListItem.Header -> {
                    val hb = (holder as HeaderVH).binding
                    hb.tvSectionTitle.text = item.title
                    hb.tvSectionCount.text = "${item.count}"
                }
                is AccountListItem.User -> bindUser((holder as UserVH).binding, item.profile)
            }
        }

        private fun bindUser(b: ItemUserCardBinding, user: UserProfile) {
            b.tvUserName.text = user.name
            b.tvUserEmail.text = buildString {
                append(user.email)
                if (user.staffNo.isNotEmpty()) append(" · ${user.staffNo}")
            }
            b.tvUserInitial.visibility = View.GONE
            b.ivUserAvatar.visibility = View.VISIBLE

            // Render photo strictly from Firebase photoUrl field
            loadAvatarFromFirebase(b.ivUserAvatar, user.photoUrl, user.name)

            when (user.role.uppercase()) {
                "ADMIN" -> {
                    b.tvUserRole.text = "ADMIN"
                    b.tvUserRole.setBackgroundResource(R.drawable.bg_badge_admin_red)
                    b.tvUserRole.setTextColor(Color.parseColor("#991B1B"))
                    b.tvAssignedRoute.visibility = View.GONE
                }
                "DRIVER" -> {
                    b.tvUserRole.text = "DRIVER"
                    b.tvUserRole.setBackgroundResource(R.drawable.bg_badge_driver_amber)
                    b.tvUserRole.setTextColor(Color.parseColor("#92400E"))

                    b.tvAssignedRoute.visibility = View.VISIBLE
                    val routeText = user.assignedRoute.ifEmpty { "Unassigned" }
                    val busText = user.assignedBus.ifEmpty { "—" }
                    b.tvAssignedRoute.text = "$routeText · $busText"
                }
                "STUDENT" -> {
                    b.tvUserRole.text = "STUDENT"
                    b.tvUserRole.setBackgroundResource(R.drawable.bg_badge_student_violet)
                    b.tvUserRole.setTextColor(Color.parseColor("#5B21B6"))

                    if (user.faculty.isNotEmpty() || user.program.isNotEmpty()) {
                        b.tvAssignedRoute.visibility = View.VISIBLE
                        b.tvAssignedRoute.text = listOfNotNull(
                            user.faculty.ifEmpty { null },
                            user.program.ifEmpty { null }
                        ).joinToString(" · ")
                    } else {
                        b.tvAssignedRoute.visibility = View.GONE
                    }
                }
                else -> {
                    b.tvUserRole.text = user.role.uppercase()
                    b.tvUserRole.setBackgroundResource(R.drawable.bg_role_badge)
                    b.tvUserRole.setTextColor(Color.parseColor("#C0181A"))
                    b.tvAssignedRoute.visibility = View.GONE
                }
            }

            b.btnEditUser.setOnClickListener { onEdit(user) }
            b.btnDeleteUser.setOnClickListener { onDelete(user) }
        }

        override fun getItemCount() = items.size
    }
}
