package com.upsi.smartbus.ui.adapter

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.R
import com.upsi.smartbus.core.model.UserProfile
import com.upsi.smartbus.databinding.ItemAccountSectionHeaderBinding
import com.upsi.smartbus.databinding.ItemUserCardBinding

sealed class AccountListItem {
    data class Header(val title: String, val count: Int) : AccountListItem()
    data class User(val profile: UserProfile) : AccountListItem()
}

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
            iv.imageTintList = ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
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
