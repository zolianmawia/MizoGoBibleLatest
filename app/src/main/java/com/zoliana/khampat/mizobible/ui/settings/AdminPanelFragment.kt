package com.zoliana.khampat.mizobible.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.MemberInfo
import com.zoliana.khampat.mizobible.data.MembershipType
import com.zoliana.khampat.mizobible.databinding.FragmentAdminPanelBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AdminPanelFragment : Fragment() {

    private var _binding: FragmentAdminPanelBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val ADMIN_EMAIL = "zlphoto02@gmail.com"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerUsers?.layoutManager = LinearLayoutManager(requireContext())

        binding.btnGenerateNewKey?.setOnClickListener { generateNewKey() }
        binding.btnActivateEmail?.text = "Link Email & Premium Key"
        binding.btnActivateEmail?.setOnClickListener { showLinkEmailKeyDialog() }

        fetchPremiumUsers()
        fetchTotalKeys()
    }

    private fun fetchPremiumUsers() {
        db.collection("premium_users")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MGB_DEBUG", "Error fetching users: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val userList = mutableListOf<PremiumUser>()
                snapshot.documents.forEach { doc ->
                    userList.add(
                        PremiumUser(
                            email = doc.id,
                            activatedAt = doc.getLong("activatedAt") ?: 0L,
                            expiresAt = doc.getLong("expiresAt") ?: 0L,
                            active = doc.getBoolean("active") ?: false,
                            type = doc.getString("type") ?: "SILVER",
                            name = doc.getString("name") ?: "",
                            address = doc.getString("address") ?: "",
                            phone = doc.getString("phone") ?: ""
                        )
                    )
                }
                userList.sortByDescending { it.activatedAt }
                binding.textTotalUsers?.text = "Users: ${userList.size}"
                binding.recyclerUsers?.adapter = UserAdapter(userList)
            }
    }

    private fun fetchTotalKeys() {
        db.collection("premium_keys").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            binding.textTotalKeys?.text = "Keys Generated: ${snapshot.size()}"
        }
    }

    private fun generateNewKey() {
        val newKey = "MGB-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val data = hashMapOf(
            "used" to false,
            "createdAt" to System.currentTimeMillis(),
            "generatedBy" to ADMIN_EMAIL,
            "active" to true
        )
        db.collection("premium_keys").document(newKey).set(data).addOnSuccessListener {
            if (!isAdded) return@addOnSuccessListener
            val dialog = AlertDialog.Builder(requireContext()).setTitle("Key Generated! ✨")
                .setMessage("Key: $newKey")
                .setPositiveButton("Copy Key") { _, _ ->
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MGB Key", newKey))
                    Toast.makeText(context, "Key copied!", Toast.LENGTH_SHORT).show()
                }.show()
            (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
        }
    }

    private fun showLinkEmailKeyDialog() {
        val scrollView = ScrollView(requireContext())
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val editEmail = EditText(requireContext()).apply { hint = "User Email (Gmail)" }
        val editName =
            EditText(requireContext()).apply { hint = "Hming (Required for Certificate)" }
        val editAddress =
            EditText(requireContext()).apply { hint = "Veng/Khua (Required for Certificate)" }
        val editPhone = EditText(requireContext()).apply {
            hint = "Phone (Optional)"; inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val editKey = EditText(requireContext()).apply { hint = "Premium Key (MGB-XXXX)" }

        val textType = TextView(requireContext()).apply {
            text = "Membership Type Select rawh:"
            setPadding(0, 20, 0, 10)
        }
        val spinnerType = Spinner(requireContext())
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("SILVER", "GOLD")
        )
        spinnerType.adapter = adapter

        layout.addView(editEmail)
        layout.addView(editName)
        layout.addView(editAddress)
        layout.addView(editPhone)
        layout.addView(editKey)
        layout.addView(textType)
        layout.addView(spinnerType)
        scrollView.addView(layout)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Link Email & Key")
            .setView(scrollView)
            .setPositiveButton("Activate Now") { _, _ ->
                val email = editEmail.text.toString().trim().lowercase()
                val name = editName.text.toString().trim()
                val address = editAddress.text.toString().trim()
                val phone = editPhone.text.toString().trim()
                val key = editKey.text.toString().trim().uppercase()
                val selectedType = spinnerType.selectedItem.toString()

                if (email.isNotEmpty() && key.isNotEmpty() && name.isNotEmpty() && address.isNotEmpty()) {
                    checkAndLinkKey(email, key, name, address, phone, selectedType)
                } else {
                    Toast.makeText(
                        context,
                        "Hming leh Veng pawh dah tel a ngai (Certificate tan)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun checkAndLinkKey(
        email: String,
        key: String,
        name: String,
        address: String,
        phone: String,
        type: String
    ) {
        db.collection("premium_keys").document(key).get().addOnSuccessListener { doc ->
            if (doc.exists() && doc.getBoolean("used") == false) {
                db.collection("premium_keys").document(key).update(
                    "used", true, "usedBy", email, "activatedAt", System.currentTimeMillis()
                ).addOnSuccessListener {
                    grantPremium(email, name, address, phone, type)
                }
            } else {
                Toast.makeText(context, "Key a dik lo emaw, hman tawh a ni!", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun grantPremium(
        email: String,
        name: String,
        address: String,
        phone: String,
        type: String
    ) {
        val activatedAt = System.currentTimeMillis()
        val expiresAt = if (type == "GOLD") {
            Long.MAX_VALUE // Lifetime
        } else {
            Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
        }

        val displayName = if (name.isNotEmpty()) name else email.substringBefore("@")

        val premiumData = hashMapOf(
            "active" to true,
            "expiresAt" to expiresAt,
            "activatedAt" to activatedAt,
            "type" to type,
            "name" to displayName,
            "address" to address,
            "phone" to phone,
            "deviceIds" to emptyList<String>()
        )

        db.collection("premium_users").document(email).set(premiumData, SetOptions.merge())

        val memberData = MemberInfo(
            uid = email,
            name = displayName,
            address = address,
            phone = phone,
            membershipType = type,
            expiresAt = expiresAt,
            timestamp = activatedAt
        )

        db.collection("public_members").document(email).set(memberData)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, "Successfully activated: $email ✨", Toast.LENGTH_SHORT)
                        .show()

                    // Certificate thawnna tur call-na
                    val mainActivity = requireActivity() as? MainActivity
                    val mType = if (type == "GOLD") MembershipType.GOLD else MembershipType.SILVER
                    mainActivity?.generateCertificateImageAndSend(
                        mType,
                        displayName,
                        address,
                        email,
                        expiresAt
                    )
                }
            }
    }


    private fun deactivateUser(user: PremiumUser) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Deactivate User?")
            .setMessage("${user.email} hi i deactivate duh tak tak em?")
            .setPositiveButton("Hmet rawh") { _, _ ->
                db.collection("premium_users").document(user.email).delete()
                db.collection("public_members").document(user.email).delete()
                    .addOnSuccessListener {
                        if (isAdded) Toast.makeText(
                            context,
                            "User deactivated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton("Aih", null)
            .show()
        (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
    }

    data class PremiumUser(
        val email: String,
        val activatedAt: Long,
        val expiresAt: Long,
        val active: Boolean,
        val type: String,
        val name: String,
        val address: String,
        val phone: String
    )

    inner class UserAdapter(private val users: List<PremiumUser>) :
        RecyclerView.Adapter<UserAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_premium_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            if (user.name.isNotEmpty()) {
                holder.textName.text = "Hming: ${user.name}"
                holder.textName.visibility = View.VISIBLE
                holder.textEmail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                holder.textEmail.text = "Mail: ${user.email}"
            } else {
                holder.textName.visibility = View.GONE
                holder.textEmail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                holder.textEmail.text = "Mail: ${user.email}"
            }

            if (user.phone.isNotEmpty()) {
                holder.textPhone.text = "Phone: ${user.phone}"
                holder.textPhone.visibility = View.VISIBLE
            } else {
                holder.textPhone.visibility = View.GONE
            }

            if (user.address.isNotEmpty()) {
                holder.textAddress.text = "Veng: ${user.address}"
                holder.textAddress.visibility = View.VISIBLE
            } else {
                holder.textAddress.visibility = View.GONE
            }

            holder.textActivated.text =
                "📅 ${if (user.activatedAt > 0) sdf.format(Date(user.activatedAt)) else "N/A"}"

            if (!user.active || (user.expiresAt > 0 && System.currentTimeMillis() > user.expiresAt)) {
                holder.textExpire.setTextColor(Color.RED)
                holder.textExpire.text = "Status: Expired"
            } else {
                holder.textExpire.setTextColor(Color.parseColor("#4CAF50"))
                val displayType = when (user.type.uppercase()) {
                    "SILVER", "PATRON" -> "Silver"
                    "GOLD", "LIVE" -> "Gold"
                    else -> user.type
                }
                holder.textExpire.text = "Status: $displayType"
            }

            holder.btnDelete.setOnClickListener { deactivateUser(user) }
        }

        override fun getItemCount() = users.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textName: TextView = view.findViewById(R.id.text_user_name)
            val textPhone: TextView = view.findViewById(R.id.text_user_phone)
            val textEmail: TextView = view.findViewById(R.id.text_user_email)
            val textAddress: TextView = view.findViewById(R.id.text_user_address)
            val textActivated: TextView = view.findViewById(R.id.text_activated_date)
            val textExpire: TextView = view.findViewById(R.id.text_expire_date)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_user)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}
