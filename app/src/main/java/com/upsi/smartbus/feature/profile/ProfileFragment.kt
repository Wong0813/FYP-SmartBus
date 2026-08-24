package com.upsi.smartbus.feature.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.model.UserProfile
import com.upsi.smartbus.databinding.FragmentProfileBinding
import com.upsi.smartbus.feature.auth.LoginActivity

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnHeroDrawer.setOnClickListener {
            (activity as? com.upsi.smartbus.feature.admin.AdminActivity)?.openDrawer()
        }
        loadProfile()
        setupSignOut()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                val profile = doc.toObject(UserProfile::class.java)
                if (profile != null) {
                    populateProfile(profile)
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }

    }

    private fun populateProfile(profile: UserProfile) {
        binding.tvFullName.text = profile.name.ifEmpty { "SmartBus User" }
        binding.tvUsername.text = profile.email.ifEmpty { auth.currentUser?.email ?: "" }
        binding.tvUserId.text = "#${profile.uid.take(8).uppercase()}"
        binding.tvEmail.text = profile.email.ifEmpty { auth.currentUser?.email ?: "" }

        // Set role-specific avatar icon
        when (profile.role.uppercase()) {
            "STUDENT" -> {
                binding.ivRoleIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
                showStudentFields(profile)
            }
            "DRIVER" -> {
                binding.ivRoleIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                showDriverFields(profile)
            }
            "ADMIN" -> {
                binding.ivRoleIcon.setImageResource(android.R.drawable.ic_menu_manage)
                hideStudentFields()
            }
            else -> {
                binding.ivRoleIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
                showStudentFields(profile)
            }
        }
    }

    private fun showStudentFields(profile: UserProfile) {
        // Show faculty and program rows for students
        binding.rowFaculty.visibility = View.VISIBLE
        binding.dividerFaculty.visibility = View.VISIBLE
        binding.rowProgram.visibility = View.VISIBLE
        binding.dividerProgram.visibility = View.VISIBLE

        binding.tvFaculty.text = profile.faculty.ifEmpty { "—" }
        binding.tvProgram.text = profile.program.ifEmpty { "—" }

        // Hide driver-specific rows
        binding.rowAssignedBus.visibility = View.GONE
        binding.dividerDriver.visibility = View.GONE
        binding.rowAssignedRoute.visibility = View.GONE
        binding.dividerRoute.visibility = View.GONE
    }

    private fun showDriverFields(profile: UserProfile) {
        // Hide student-specific rows
        binding.rowFaculty.visibility = View.GONE
        binding.dividerFaculty.visibility = View.GONE
        binding.rowProgram.visibility = View.GONE
        binding.dividerProgram.visibility = View.GONE

        // Show driver-specific rows
        binding.dividerDriver.visibility = View.VISIBLE
        binding.rowAssignedBus.visibility = View.VISIBLE
        binding.dividerRoute.visibility = View.VISIBLE
        binding.rowAssignedRoute.visibility = View.VISIBLE

        binding.tvAssignedBus.text = profile.assignedBus.ifEmpty { "—" }
        binding.tvAssignedRoute.text = profile.assignedRoute.ifEmpty { "—" }
    }

    private fun hideStudentFields() {
        // Admin: hide faculty/program/driver rows
        binding.rowFaculty.visibility = View.GONE
        binding.dividerFaculty.visibility = View.GONE
        binding.rowProgram.visibility = View.GONE
        binding.dividerProgram.visibility = View.GONE
        binding.rowAssignedBus.visibility = View.GONE
        binding.dividerDriver.visibility = View.GONE
        binding.rowAssignedRoute.visibility = View.GONE
        binding.dividerRoute.visibility = View.GONE
    }

    private fun setupSignOut() {
        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
