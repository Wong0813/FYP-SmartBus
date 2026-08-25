package com.upsi.smartbus.feature.profile

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.model.UserProfile
import com.upsi.smartbus.databinding.FragmentProfileBinding

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
                ?: (activity as? com.upsi.smartbus.feature.student.StudentActivity)?.openDrawer()
                ?: (activity as? com.upsi.smartbus.feature.driver.DriverActivity)?.openDrawer()
        }
        loadProfile()
    }

    private fun loadProfile() {
        val user = auth.currentUser
        val uid = user?.uid

        if (uid == null) {
            populateFallback(user?.email ?: "admin@upsi.edu.my")
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener
                val profile = doc.toObject(UserProfile::class.java)
                if (profile != null) {
                    populateProfile(profile.copy(uid = doc.id))
                } else {
                    populateFallback(user.email ?: "admin@upsi.edu.my")
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                populateFallback(user.email ?: "admin@upsi.edu.my")
            }
    }

    private fun populateProfile(profile: UserProfile) {
        val name = profile.name.ifEmpty { "SmartBus Administrator" }
        val email = profile.email.ifEmpty { auth.currentUser?.email ?: "admin@upsi.edu.my" }
        val role = profile.role.uppercase()

        binding.tvFullName.text = name
        binding.tvUserEmail.text = email
        binding.tvEmail.text = email

        // Load avatar photo from Firebase or fallback
        loadAvatar(binding.ivUserAvatar, profile.photoUrl)

        // Top role badge is always styled consistently (ADMIN, DRIVER, STUDENT)
        binding.tvHeroRoleBadge.text = role.ifEmpty { "ADMIN" }
        binding.tvRole.text = role.ifEmpty { "ADMIN" }

        when (role) {
            "ADMIN" -> {
                binding.tvLabelStaffNo.text = "Staff ID"
                binding.tvStaffNo.text = profile.staffNo.ifEmpty { "ADM-001" }

                // Hide Driver and Student specifics
                binding.rowAssignedBus.visibility = View.GONE
                binding.dividerDriverBus.visibility = View.GONE
                binding.rowAssignedRoute.visibility = View.GONE
                binding.dividerRoute.visibility = View.GONE
                binding.rowFaculty.visibility = View.GONE
                binding.dividerFaculty.visibility = View.GONE
                binding.rowProgram.visibility = View.GONE
                binding.dividerProgram.visibility = View.GONE
            }

            "DRIVER" -> {
                val staff = profile.staffNo.ifEmpty { "DRV-01" }
                binding.tvLabelStaffNo.text = "Staff ID"
                binding.tvStaffNo.text = staff

                // Show Driver specifics
                binding.rowAssignedBus.visibility = View.VISIBLE
                binding.dividerDriverBus.visibility = View.VISIBLE
                val busId = profile.assignedBus.ifEmpty { "BUS-001" }
                val plate = profile.plateNumber.ifEmpty { "WAA 1001 X" }
                binding.tvAssignedBus.text = "$busId • $plate"

                binding.rowAssignedRoute.visibility = View.VISIBLE
                binding.dividerRoute.visibility = View.VISIBLE
                binding.tvAssignedRoute.text = profile.assignedRoute.ifEmpty { "Laluan 1" }

                // Hide Student specifics
                binding.rowFaculty.visibility = View.GONE
                binding.dividerFaculty.visibility = View.GONE
                binding.rowProgram.visibility = View.GONE
                binding.dividerProgram.visibility = View.GONE
            }

            else -> {
                // STUDENT
                val matric = profile.staffNo.ifEmpty { "STU-001" }
                val fac = profile.faculty.ifEmpty { "FCI" }
                val prog = profile.program.ifEmpty { "Software Engineering" }

                binding.tvLabelStaffNo.text = "Matric ID"
                binding.tvStaffNo.text = matric

                // Show Student specifics
                binding.rowFaculty.visibility = View.VISIBLE
                binding.dividerFaculty.visibility = View.VISIBLE
                binding.tvFaculty.text = fac

                binding.rowProgram.visibility = View.VISIBLE
                binding.dividerProgram.visibility = View.VISIBLE
                binding.tvProgram.text = prog

                // Hide Driver specifics
                binding.rowAssignedBus.visibility = View.GONE
                binding.dividerDriverBus.visibility = View.GONE
                binding.rowAssignedRoute.visibility = View.GONE
                binding.dividerRoute.visibility = View.GONE
            }
        }
    }

    private fun populateFallback(email: String) {
        val isAdmin = email.contains("admin", ignoreCase = true)
        val isDriver = email.contains("driver", ignoreCase = true)

        val fallbackProfile = when {
            isAdmin -> UserProfile(
                name = "SmartBus Administrator",
                email = email,
                role = "ADMIN",
                staffNo = "ADM-001"
            )
            isDriver -> UserProfile(
                name = "UPSI Fleet Driver",
                email = email,
                role = "DRIVER",
                staffNo = "DRV-01",
                assignedBus = "BUS-001",
                plateNumber = "WAA 1001 X",
                assignedRoute = "Laluan 1"
            )
            else -> UserProfile(
                name = "UPSI Student",
                email = email,
                role = "STUDENT",
                staffNo = "STU-001",
                faculty = "FCI",
                program = "Software Engineering"
            )
        }
        populateProfile(fallbackProfile)
    }

    private fun loadAvatar(iv: ImageView, photoUrl: String) {
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
        // Fallback placeholder
        iv.setImageResource(R.drawable.ic_nav_profile)
        iv.setBackgroundColor(Color.parseColor("#F1F5F9"))
        val p = (iv.resources.displayMetrics.density * 12).toInt()
        iv.setPadding(p, p, p, p)
        iv.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
