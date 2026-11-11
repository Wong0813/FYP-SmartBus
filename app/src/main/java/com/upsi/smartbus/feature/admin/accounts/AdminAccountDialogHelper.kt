package com.upsi.smartbus.feature.admin.accounts

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.upsi.smartbus.core.model.UserProfile
import com.upsi.smartbus.databinding.DialogEditUserBinding
import com.upsi.smartbus.ui.adapter.AccountListAdapter

class AdminAccountDialogHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val routeNames: List<String>,
    private val onPickPhotoRequested: ((onPhotoSelected: (String) -> Unit) -> Unit)
) {
    private var activePhotoUrl: String = ""

    fun showEditUserDialog(
        user: UserProfile,
        screenWidthPixels: Int,
        onSaveUser: (updatedUser: UserProfile, updates: Map<String, Any>) -> Unit
    ) {
        val dialog = Dialog(context)
        val b = DialogEditUserBinding.inflate(layoutInflater)
        activePhotoUrl = user.photoUrl

        dialog.setContentView(b.root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((screenWidthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        b.tvEditDialogTitle.text = "Edit ${user.name}"
        b.etEditName.setText(user.name)
        b.etEditStaffNo.setText(user.staffNo)

        val isDriver = user.role.equals("DRIVER", true)
        val isStudent = user.role.equals("STUDENT", true)

        fun updateAvatar() {
            AccountListAdapter.loadAvatarFromFirebase(b.ivDialogUserAvatar, activePhotoUrl, user.name)
        }
        updateAvatar()

        b.btnPickGallery.setOnClickListener {
            onPickPhotoRequested { newUrl ->
                activePhotoUrl = newUrl
                updateAvatar()
            }
        }

        if (isDriver) {
            b.tvLabelStaffNo.text = "DRIVER ID / STAFF NO"
            b.llEditDriverFields.visibility = View.VISIBLE
            b.etEditBus.setText(user.assignedBus)
            b.etEditPlate.setText(user.plateNumber)

            val adapter = ArrayAdapter(context, com.upsi.smartbus.R.layout.item_custom_spinner_selected, routeNames).apply {
                setDropDownViewResource(com.upsi.smartbus.R.layout.item_custom_spinner_dropdown)
            }
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
                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = mutableMapOf<String, Any>(
                "name" to newName,
                "staffNo" to newStaffNo,
                "photoUrl" to activePhotoUrl
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

            val updatedUser = user.copy(
                name = newName,
                staffNo = newStaffNo,
                photoUrl = activePhotoUrl,
                assignedRoute = if (isDriver) b.spinnerEditRoute.selectedItem?.toString() ?: user.assignedRoute else user.assignedRoute,
                assignedBus = if (isDriver) b.etEditBus.text.toString().trim() else user.assignedBus,
                plateNumber = if (isDriver) b.etEditPlate.text.toString().trim() else user.plateNumber,
                faculty = if (isStudent) b.etEditFaculty.text.toString().trim() else user.faculty,
                program = if (isStudent) b.etEditProgram.text.toString().trim() else user.program
            )

            dialog.dismiss()
            Toast.makeText(context, "Changes saved successfully", Toast.LENGTH_SHORT).show()
            onSaveUser(updatedUser, updates)
        }

        dialog.show()
    }
}
