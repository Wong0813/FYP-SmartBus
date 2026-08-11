package com.upsi.smartbus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.databinding.FragmentAdminAccountsBinding
import com.upsi.smartbus.databinding.ItemUserCardBinding
import com.upsi.smartbus.model.RouteData
import com.upsi.smartbus.model.UserProfile

class AdminAccountsFragment : Fragment() {

    private var _binding: FragmentAdminAccountsBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val userList = mutableListOf<UserProfile>()
    private lateinit var userAdapter: UserListAdapter

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
        setupRecyclerView()
        setupCreateButton()
        loadUsers()
    }

    private fun setupRoleSpinner() {
        val roles = arrayOf("STUDENT", "DRIVER", "ADMIN")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
        binding.spinnerRole.adapter = adapter

        binding.spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.etDriverRoute.visibility = if (roles[position] == "DRIVER") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        userAdapter = UserListAdapter(userList,
            onDelete = { user -> deleteUser(user) },
            onEdit = { user -> showEditUserDialog(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = userAdapter
    }

    private fun showEditUserDialog(user: UserProfile) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Name field
        val etName = EditText(ctx).apply {
            hint = "Full Name"
            setText(user.name)
        }
        layout.addView(etName)

        if (user.role == "DRIVER") {
            // Bus ID field
            val etBus = EditText(ctx).apply {
                hint = "Bus ID (e.g. BUS-001)"
                setText(user.assignedBus)
            }
            layout.addView(etBus)

            val routesList = RouteData.getAllRoutes().map { it.name }
            val currentRouteIndex = routesList.indexOf(user.assignedRoute).coerceAtLeast(0)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("✏️ Edit Driver: ${user.name}")
                .setView(layout)
                .setPositiveButton("Save") { _, _ ->
                    val newName = etName.text.toString().trim()
                    val newBus = etBus.text.toString().trim()
                    val updates = hashMapOf<String, Any>(
                        "name" to newName,
                        "assignedBus" to newBus
                    )
                    db.collection("users").document(user.uid).update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(ctx, "Updated $newName", Toast.LENGTH_SHORT).show()
                            loadUsers()
                        }
                }
                .setNeutralButton("Change Route") { _, _ ->
                    val itemsArray: Array<CharSequence> = routesList.map { it as CharSequence }.toTypedArray()
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle("Reassign Route")
                        .setItems(itemsArray) { _, which ->
                            db.collection("users").document(user.uid)
                                .update("assignedRoute", routesList[which])
                                .addOnSuccessListener {
                                    Toast.makeText(ctx, "Route → ${routesList[which]}", Toast.LENGTH_SHORT).show()
                                    loadUsers()
                                }
                        }
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Non-driver edit — just name
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("✏️ Edit: ${user.name}")
                .setView(layout)
                .setPositiveButton("Save") { _, _ ->
                    val newName = etName.text.toString().trim()
                    db.collection("users").document(user.uid)
                        .update("name", newName)
                        .addOnSuccessListener {
                            Toast.makeText(ctx, "Updated $newName", Toast.LENGTH_SHORT).show()
                            loadUsers()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupCreateButton() {
        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val username = binding.etAdminUsername.text.toString().trim()
            val staffNo = binding.etStaffNo.text.toString().trim()
            val password = binding.etAdminPassword.text.toString().trim()
            val role = binding.spinnerRole.selectedItem.toString()
            val driverRoute = binding.etDriverRoute.text.toString().trim()

            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val email = "$username@upsi.edu.my"

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener
                    val profile = hashMapOf(
                        "uid" to uid,
                        "name" to name,
                        "email" to email,
                        "role" to role,
                        "staffNo" to staffNo,
                        "assignedRoute" to if (role == "DRIVER") driverRoute else ""
                    )

                    db.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Account created successfully", Toast.LENGTH_SHORT).show()
                            clearForm()
                            loadUsers()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to create account: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun clearForm() {
        binding.etName.text?.clear()
        binding.etAdminUsername.text?.clear()
        binding.etStaffNo.text?.clear()
        binding.etAdminPassword.text?.clear()
        binding.etDriverRoute.text?.clear()
        binding.spinnerRole.setSelection(0)
    }

    private fun loadUsers() {
        seedAllDriverAccountsToFirebase()

        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                userList.clear()
                for (doc in snapshot.documents) {
                    val profile = doc.toObject(UserProfile::class.java)
                    if (profile != null) {
                        userList.add(profile)
                    }
                }
                // Sort: ADMIN first, then DRIVER, then STUDENT
                userList.sortBy { when (it.role) { "ADMIN" -> 0; "DRIVER" -> 1; else -> 2 } }
                userAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                userList.clear()
                userList.addAll(listOf(
                    UserProfile("1", "Ahmad Bin Ali", "student@upsi.edu.my", "STUDENT", "FCI", "Software Engineering"),
                    UserProfile("2", "Razif Bin Hassan", "driver@upsi.edu.my", "DRIVER", assignedBus = "BUS-001", assignedRoute = "Laluan 1"),
                    UserProfile("3", "Admin UPSI", "admin@upsi.edu.my", "ADMIN")
                ))
                userAdapter.notifyDataSetChanged()
            }
    }

    private fun seedAllDriverAccountsToFirebase() {
        RouteData.defaultDrivers.forEach { driver ->
            val docId = "driver_${driver.routeName.replace(" ", "_").lowercase()}"
            val profile = hashMapOf(
                "uid" to docId,
                "name" to driver.driverName,
                "email" to driver.email,
                "role" to "DRIVER",
                "assignedBus" to driver.busId,
                "assignedRoute" to driver.routeName,
                "plateNumber" to driver.plateNumber
            )
            db.collection("users").document(docId).set(profile, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    private fun deleteUser(user: UserProfile) {
        if (user.uid.isEmpty()) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Delete ${user.name}?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("users").document(user.uid).delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "User deleted", Toast.LENGTH_SHORT).show()
                        loadUsers()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ════════════════════════════════════════════════════════════════════
    // USER LIST ADAPTER
    // ════════════════════════════════════════════════════════════════════

    class UserListAdapter(
        private val users: List<UserProfile>,
        private val onDelete: (UserProfile) -> Unit,
        private val onEdit: (UserProfile) -> Unit
    ) : RecyclerView.Adapter<UserListAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemUserCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemUserCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.binding.tvUserName.text = user.name
            holder.binding.tvUserEmail.text = user.email
            holder.binding.tvUserRole.text = user.role
            holder.binding.tvUserInitial.text = user.name.firstOrNull()?.uppercase() ?: "?"

            // Show assigned route for drivers
            if (user.role == "DRIVER" && user.assignedRoute.isNotEmpty()) {
                holder.binding.tvAssignedRoute.visibility = View.VISIBLE
                holder.binding.tvAssignedRoute.text = "📍 ${user.assignedRoute}"
            } else {
                holder.binding.tvAssignedRoute.visibility = View.GONE
            }

            // Set role color on initial circle
            val color = when (user.role) {
                "STUDENT" -> R.color.status_moving
                "DRIVER" -> R.color.status_resting
                "ADMIN" -> R.color.crimson_primary
                else -> R.color.status_offline
            }
            holder.binding.tvUserInitial.setTextColor(
                ContextCompat.getColor(holder.itemView.context, color)
            )

            holder.binding.btnDeleteUser.setOnClickListener { onDelete(user) }
            holder.binding.btnEditUser.setOnClickListener { onEdit(user) }
        }

        override fun getItemCount() = users.size
    }
}
