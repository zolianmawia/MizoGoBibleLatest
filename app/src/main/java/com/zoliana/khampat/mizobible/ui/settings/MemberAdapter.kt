package com.zoliana.khampat.mizobible.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.zoliana.khampat.mizobible.data.MemberInfo
import com.zoliana.khampat.mizobible.databinding.ItemMemberBinding
import java.text.SimpleDateFormat
import java.util.*

class MemberAdapter(
    private val isAdmin: Boolean = false,
    private val onMemberClick: ((MemberInfo) -> Unit)? = null
) : ListAdapter<MemberInfo, MemberAdapter.MemberViewHolder>(MemberDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding, onMemberClick)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MemberViewHolder(
        private val binding: ItemMemberBinding,
        private val onMemberClick: ((MemberInfo) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val ADMIN_EMAIL = "zlphoto02@gmail.com"

        fun bind(member: MemberInfo) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val currentEmail = currentUser?.email?.lowercase()?.trim()
            val isReallyAdmin = currentEmail == ADMIN_EMAIL.lowercase()

            binding.textMemberName.text = if (member.name.isNotEmpty()) member.name else "User"

            if (member.address.isNotEmpty()) {
                binding.textMemberAddress.text = member.address
                binding.textMemberAddress.visibility = View.VISIBLE
            } else {
                binding.textMemberAddress.visibility = View.GONE
            }

            binding.textMemberEmail.text = member.uid

            if (isReallyAdmin && member.phone.isNotEmpty()) {
                binding.textMemberPhone?.text = "Phone: ${member.phone}"
                binding.textMemberPhone?.visibility = View.VISIBLE
            } else {
                binding.textMemberPhone?.visibility = View.GONE
            }

            val statusText = when (member.membershipType.uppercase()) {
                "GOLD", "LIVE" -> {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateStr = sdf.format(Date(member.timestamp))
                    "Gold Active: $dateStr 💎"
                }
                "SILVER", "PATRON" -> {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateStr = sdf.format(Date(member.expiresAt))
                    "Expires: $dateStr ✨"
                }
                else -> "Free Member"
            }
            binding.textMembershipStatus.text = statusText

            if (member.membershipType.uppercase() == "GOLD" || member.membershipType.uppercase() == "LIVE") {
                binding.textMembershipStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_blue_dark))
                binding.textMembershipStatus.alpha = 1.0f
            } else {
                binding.textMembershipStatus.setTextColor(binding.root.context.getColor(android.R.color.darker_gray))
                binding.textMembershipStatus.alpha = 0.7f
            }

            if (isReallyAdmin) {
                binding.root.setOnClickListener {
                    onMemberClick?.invoke(member)
                }
                binding.root.isClickable = true
                binding.root.isFocusable = true
            } else {
                binding.root.setOnClickListener(null)
                binding.root.isClickable = false
                binding.root.isFocusable = false
            }
        }
    }

    class MemberDiffCallback : DiffUtil.ItemCallback<MemberInfo>() {
        override fun areItemsTheSame(oldItem: MemberInfo, newItem: MemberInfo): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: MemberInfo, newItem: MemberInfo): Boolean = oldItem == newItem
    }
}
