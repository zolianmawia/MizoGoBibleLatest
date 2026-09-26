package com.zoliana.khampat.mizobible.ui.settings

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import java.security.MessageDigest
import com.zoliana.khampat.mizobible.data.*
import com.zoliana.khampat.mizobible.databinding.DialogLoginBinding
import com.zoliana.khampat.mizobible.databinding.FragmentProfileBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val ADMIN_EMAIL = "zlphoto02@gmail.com"

    private lateinit var goldAdapter: MemberAdapter
    private lateinit var silverAdapter: MemberAdapter

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.root?.let { rootView ->
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(bottom = systemBars.bottom)
                insets
            }
        }

        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        createNotificationChannel()
        checkLoginStatus()
        viewModel.syncPublicMembers()

        binding?.imgProfile?.let {
            it.colorFilter = null
            androidx.core.widget.ImageViewCompat.setImageTintList(it, null)
        }
        binding?.imgGoldBadge?.let {
            it.colorFilter = null
            androidx.core.widget.ImageViewCompat.setImageTintList(it, null)
        }
        binding?.imgSilverBadge?.let {
            it.colorFilter = null
            androidx.core.widget.ImageViewCompat.setImageTintList(it, null)
        }

        binding?.btnLoginTrigger?.setOnClickListener { showLoginDialog() }
        binding?.btnLoginTrigger?.setOnLongClickListener {
            showSha1ConfigDialog()
            true
        }
        binding?.btnLogout?.setOnClickListener { logoutUser() }
        binding?.btnBuyPatron?.setOnClickListener { showPaymentDetailsDialog(MembershipType.SILVER) }
        binding?.btnBuyLive?.setOnClickListener { showPaymentDetailsDialog(MembershipType.GOLD) }

        binding?.btnAdminActivatePatron?.setOnClickListener { performAdminActivation(MembershipType.SILVER) }
        binding?.btnAdminActivateLive?.setOnClickListener { performAdminActivation(MembershipType.GOLD) }
        binding?.btnAdminDeactivate?.setOnClickListener { performManualDeactivation() }

        binding?.layoutPinsStats?.setOnClickListener { findNavController().navigate(R.id.nav_pin) }
        binding?.layoutBookmarksStats?.setOnClickListener { findNavController().navigate(R.id.nav_bookmark) }

        viewModel.allPins.observe(viewLifecycleOwner) { updateStats() }
        viewModel.allBookmarks.observe(viewLifecycleOwner) { updateStats() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.membershipType.collectLatest { type ->
                    binding?.textMembershipStatus?.text = when(type) {
                        MembershipType.SILVER -> "Silver Member ✨"
                        MembershipType.GOLD -> "Gold Member 💎"
                        else -> "Free Member"
                    }
                    binding?.imgGoldBadge?.visibility = if (type == MembershipType.GOLD) View.VISIBLE else View.GONE
                    binding?.imgSilverBadge?.visibility = if (type == MembershipType.SILVER) View.VISIBLE else View.GONE
                    binding?.layoutMembershipOptions?.visibility = if (type == MembershipType.FREE) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.goldMembers.collectLatest { members ->
                    if (::goldAdapter.isInitialized) goldAdapter.submitList(members)
                    binding?.textEmptyLive?.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
                    binding?.textLiveMembersTitle?.text = "Gold Members (${members.size}) 💎"
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.silverMembers.collectLatest { members ->
                    if (::silverAdapter.isInitialized) silverAdapter.submitList(members)
                    binding?.textEmptyPatron?.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
                    binding?.textPatronMembersTitle?.text = "Silver Members (${members.size}) ✨"
                }
            }
        }
    }

    private fun setupMemberLists() {
        val currentUser = auth.currentUser
        val isAdmin = currentUser?.email?.lowercase()?.trim() == ADMIN_EMAIL.lowercase()
        
        val onMemberClick: (MemberInfo) -> Unit = { member ->
            val currentEmail = auth.currentUser?.email?.lowercase()?.trim()
            if (currentEmail == ADMIN_EMAIL.lowercase()) {
                showAdminMemberOptions(member)
            }
        }

        goldAdapter = MemberAdapter(isAdmin, onMemberClick)
        binding?.rvLiveMembers?.layoutManager = LinearLayoutManager(context)
        binding?.rvLiveMembers?.adapter = goldAdapter

        silverAdapter = MemberAdapter(isAdmin, onMemberClick)
        binding?.rvPatronMembers?.layoutManager = LinearLayoutManager(context)
        binding?.rvPatronMembers?.adapter = silverAdapter

        binding?.containerLiveMembers?.visibility = View.GONE
        binding?.iconExpandLive?.rotation = 0f

        binding?.headerLiveMembers?.setOnClickListener {
            val isVisible = binding?.containerLiveMembers?.visibility == View.VISIBLE
            binding?.containerLiveMembers?.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding?.iconExpandLive?.animate()?.rotation(if (isVisible) 0f else 180f)?.setDuration(200)?.start()
        }

        binding?.headerPatronMembers?.setOnClickListener {
            val isVisible = binding?.containerPatronMembers?.visibility == View.VISIBLE
            binding?.containerPatronMembers?.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding?.iconExpandPatron?.animate()?.rotation(if (isVisible) 0f else 180f)?.setDuration(200)?.start()
        }

        binding?.headerAdminPanel?.setOnClickListener {
            val isVisible = binding?.containerAdminPanel?.visibility == View.VISIBLE
            binding?.containerAdminPanel?.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding?.iconExpandAdmin?.animate()?.rotation(if (isVisible) 0f else 180f)?.setDuration(200)?.start()
        }
        
        goldAdapter.submitList(viewModel.goldMembers.value)
        silverAdapter.submitList(viewModel.silverMembers.value)
    }

    private fun showAdminMemberOptions(member: MemberInfo) {
        val currentEmail = auth.currentUser?.email?.lowercase()?.trim()
        if (currentEmail != ADMIN_EMAIL.lowercase()) return

        val options = arrayOf("Deactivate Member (Tih thi)", "Delete Member (Nuaibo)", "Regenerate Certificate")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Options: ${member.uid}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deactivateMemberByEmail(member.uid)
                    1 -> deleteMemberByEmail(member.uid)
                    2 -> {
                        val type = if (member.membershipType.equals("GOLD", ignoreCase = true)) MembershipType.GOLD else MembershipType.SILVER
                        (activity as? MainActivity)?.generateCertificateImageAndSend(type, member.name, member.address, member.uid, member.timestamp)
                    }
                }
            }
            .show()
    }

    private fun performManualDeactivation() {
        if (auth.currentUser?.email?.lowercase()?.trim() != ADMIN_EMAIL.lowercase()) return
        val email = binding?.adminUserEmail?.text.toString().trim().lowercase()
        if (email.isEmpty()) {
            Toast.makeText(context, "Deactivate tur email dah rawh!", Toast.LENGTH_SHORT).show()
            return
        }
        deactivateMemberByEmail(email)
    }

    private fun deactivateMemberByEmail(email: String) {
        val db = FirebaseFirestore.getInstance()
        val lowerEmail = email.lowercase().trim()
        
        db.collection("premium_users").document(lowerEmail).update("active", false)
            .addOnSuccessListener {
                db.collection("public_members").document(lowerEmail).delete()
                    .addOnSuccessListener {
                        if (isAdded) Toast.makeText(context, "$email deactivated! ✨", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun deleteMemberByEmail(email: String) {
        if (auth.currentUser?.email?.lowercase()?.trim() != ADMIN_EMAIL.lowercase()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Member")
            .setMessage("$email hi nuaibo i duh tak tak em?")
            .setPositiveButton("Delete") { _, _ ->
                val db = FirebaseFirestore.getInstance()
                val lowerEmail = email.lowercase().trim()
                db.collection("premium_users").document(lowerEmail).delete()
                db.collection("public_members").document(lowerEmail).delete()
                    .addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(requireContext().applicationContext, "Member deleted!", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStats() {
        binding?.textPinCount?.text = (viewModel.allPins.value?.size ?: 0).toString()
        binding?.textBookmarkCount?.text = (viewModel.allBookmarks.value?.size ?: 0).toString()
    }

    private fun performAdminActivation(type: MembershipType) {
        if (auth.currentUser?.email?.lowercase()?.trim() != ADMIN_EMAIL.lowercase()) return
        val email = binding?.adminUserEmail?.text.toString().trim().lowercase()
        val name = binding?.adminUserName?.text.toString().trim()
        val address = binding?.adminUserAddress?.text.toString().trim()
        val phone = binding?.adminUserPhone?.text.toString().trim()

        if (email.isEmpty() || !email.contains("@")) {
            Toast.makeText(context, "Email dik lo!", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val activatedAt = System.currentTimeMillis()
        val expiry = if (type == MembershipType.SILVER) {
            activatedAt + (365L * 24 * 60 * 60 * 1000)
        } else {
            Long.MAX_VALUE
        }

        val displayName = if (name.isNotEmpty()) name else email.substringBefore("@")

        val premiumData = mapOf(
            "active" to true,
            "type" to type.name,
            "expiresAt" to expiry,
            "activatedAt" to activatedAt,
            "name" to displayName,
            "address" to address,
            "phone" to phone
        )

        db.collection("premium_users").document(email).set(premiumData, SetOptions.merge())
            .addOnSuccessListener {
                val memberData = MemberInfo(email, displayName, address, phone, type.name, expiry, activatedAt)
                db.collection("public_members").document(email).set(memberData)
                    .addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(context, "$email activated! ✨", Toast.LENGTH_LONG).show()
                            (requireActivity() as? MainActivity)?.generateCertificateImageAndSend(type, displayName, address, email, expiry)
                        }
                    }
                binding?.adminUserEmail?.setText("")
                binding?.adminUserName?.setText("")
                binding?.adminUserAddress?.setText("")
                binding?.adminUserPhone?.setText("")
            }
    }

    private fun showPaymentDetailsDialog(type: MembershipType) {
        val user = auth.currentUser ?: return
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10) 
        }
        val nameInput = EditText(context).apply { hint = "Hming pum"; setText(user.displayName) }
        val addressInput = EditText(context).apply { hint = "Veng / Khua" }
        val phoneInput = EditText(context).apply { hint = "Phone"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        layout.addView(nameInput)
        layout.addView(addressInput)
        layout.addView(phoneInput)
        scrollView.addView(layout)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (type == MembershipType.SILVER) "Silver Fee" else "Gold Fee")
            .setView(scrollView)
            .setPositiveButton("Pay Now") { _, _ ->
                val name = nameInput.text.toString().ifEmpty { user.displayName ?: "User" }
                val address = addressInput.text.toString().ifEmpty { "Mizoram" }
                val phone = phoneInput.text.toString()
                (activity as? MainActivity)?.startRazorpayPayment(type, name, address, phone, user.email ?: "")
            }
            .setNegativeButton("Cancel", null)
            .show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    fun showPremiumSuccessUI(type: MembershipType? = null) {
        viewModel.syncFromCloud()
        val currentType = type ?: viewModel.membershipType.value
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Welcome! ✨")
            .setMessage("Mizo Go Bible Member i ni ta e!")
            .setPositiveButton("Awle", null)
            .show()
        (activity as? MainActivity)?.limitDialogWidth(dialog) 
    }

    private fun checkLoginStatus() {
        val user = auth.currentUser
        setupMemberLists()
        if (user != null) {
            binding?.layoutNotLoggedIn?.visibility = View.GONE
            binding?.layoutProfile?.visibility = View.VISIBLE
            binding?.textUserName?.text = user.displayName ?: "User"
            binding?.textUserId?.text = user.email ?: ""
            binding?.textProfileInitial?.text = (user.displayName ?: "U").first().toString().uppercase()
            if (user.photoUrl != null) {
                binding?.imgProfile?.let { imgView ->
                    imgView.colorFilter = null
                    androidx.core.widget.ImageViewCompat.setImageTintList(imgView, null)
                    Glide.with(this).load(user.photoUrl).circleCrop().into(imgView)
                }
                binding?.imgProfile?.visibility = View.VISIBLE
                binding?.textProfileInitial?.visibility = View.GONE
                binding?.viewProfilePlaceholder?.visibility = View.GONE
            } else {
                binding?.imgProfile?.visibility = View.GONE
                binding?.textProfileInitial?.visibility = View.VISIBLE
                binding?.viewProfilePlaceholder?.visibility = View.VISIBLE
            }
            if (user.email?.lowercase()?.trim() == ADMIN_EMAIL.lowercase()) {
                binding?.adminPanelCard?.visibility = View.VISIBLE
            } else {
                binding?.adminPanelCard?.visibility = View.GONE
            }
            viewModel.syncFromCloud()
        } else {
            binding?.layoutNotLoggedIn?.visibility = View.VISIBLE
            binding?.layoutProfile?.visibility = View.GONE
            binding?.adminPanelCard?.visibility = View.GONE
            viewModel.resetPremium()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("PREMIUM_CHANNEL", "Member Notifications", NotificationManager.IMPORTANCE_HIGH)
            (requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun showLoginDialog() {
        val loginBinding = DialogLoginBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(loginBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loginBinding.btnOneClickLogin.setOnClickListener { dialog.dismiss(); signIn() }
        dialog.show(); (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account?.idToken != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                } else {
                    Toast.makeText(context, "Google Sign-In: ID token hmuh a ni lo.", Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                val code = e.statusCode
                val errorString = GoogleSignInStatusCodes.getStatusCodeString(code)
                android.util.Log.e("GoogleSignIn", "ApiException code: $code ($errorString)", e)
                if (code == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    Toast.makeText(context, "Sign-In thulh (cancelled) a ni.", Toast.LENGTH_SHORT).show()
                } else if (code == CommonStatusCodes.DEVELOPER_ERROR || code == 10) {
                    showSha1ConfigDialog()
                } else {
                    Toast.makeText(context, "Login error ($code: $errorString)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("GoogleSignIn", "Unexpected sign-in error", e)
                Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            if (result.resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(context, "Sign-In thulh a ni.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Google Sign-In failed. Check internet and Google Play Services.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSha1ConfigDialog() {
        val currentSha1 = getSha1Fingerprint()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("SHA-1 Config Ngai (Error 10)")
            .setMessage("Google Sign-In fail chhan hi Firebase Console-a SHA-1 fingerprint la dah loh vang a ni e.\n\nHe app SHA-1:\n$currentSha1\n\nFirebase Console -> Project Settings -> SHA Certificate Fingerprints-ah he SHA-1 hi belh (Add fingerprint) rawh le.")
            .setPositiveButton("Copy SHA-1") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SHA1", currentSha1))
                Toast.makeText(requireContext(), "SHA-1 copy a ni e!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hrethiam e", null)
            .create()
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun getSha1Fingerprint(): String {
        return try {
            val ctx = requireContext()
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray()
            if (cert != null) {
                val md = MessageDigest.getInstance("SHA-1")
                val digest = md.digest(cert)
                digest.joinToString(":") { String.format("%02X", it) }
            } else {
                "C2:B6:90:97:AF:D5:DA:44:E0:AF:34:2B:54:C9:B2:5D:AB:25:95:93"
            }
        } catch (e: Exception) {
            "C2:B6:90:97:AF:D5:DA:44:E0:AF:34:2B:54:C9:B2:5D:AB:25:95:93"
        }
    }

    private fun signIn() { googleSignInClient.signOut().addOnCompleteListener { signInLauncher.launch(googleSignInClient.signInIntent) } }

    private fun firebaseAuthWithGoogle(idToken: String) { 
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                checkLoginStatus()
            } else {
                Toast.makeText(context, "Firebase auth failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        } 
    }

    private fun logoutUser() { 
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setPositiveButton("Logout") { _, _ -> 
                viewLifecycleOwner.lifecycleScope.launch { 
                    viewModel.repository.clearAllUserData() 
                    viewModel.resetPremium()
                    auth.signOut()
                    googleSignInClient.signOut().addOnCompleteListener { checkLoginStatus() }
                } 
            }.setNegativeButton("Cancel", null).show() 
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    override fun onResume() {
        super.onResume()
        binding?.imgProfile?.let {
            it.colorFilter = null
            androidx.core.widget.ImageViewCompat.setImageTintList(it, null)
        }
        binding?.imgGoldBadge?.let {
            it.colorFilter = null
            androidx.core.widget.ImageViewCompat.setImageTintList(it, null)
        }
        binding?.imgSilverBadge?.let {
            it.colorFilter = null
            androidx.core.widget.ImageViewCompat.setImageTintList(it, null)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
