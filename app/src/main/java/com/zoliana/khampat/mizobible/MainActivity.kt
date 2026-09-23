package com.zoliana.khampat.mizobible

import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.zoliana.khampat.mizobible.utils.NotificationHelper
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.room.withTransaction
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.onesignal.OneSignal
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.data.MemberInfo
import com.zoliana.khampat.mizobible.data.MembershipType
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.ActivityMainBinding
import com.zoliana.khampat.mizobible.databinding.DialogAboutBinding
import com.zoliana.khampat.mizobible.databinding.DialogDownloadProgressBinding
import com.zoliana.khampat.mizobible.ui.transform.BiblePickerDialog
import com.zoliana.khampat.mizobible.ui.transform.BibleVersionDialog
import com.zoliana.khampat.mizobible.ui.transform.FontSettingsDialog
import com.zoliana.khampat.mizobible.ui.transform.TransformFragment
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), PaymentResultListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var binding: ActivityMainBinding
    internal var remoteVersions: Map<String, Any>? = null
    private val assetsDatabaseVersion = 1

    private var downloadJob: Job? = null
    private var downloadDialog: AlertDialog? = null
    private var downloadProgressBinding: DialogDownloadProgressBinding? = null

    private var backPressedTime: Long = 0

    val viewModel: TransformViewModel by viewModels {
        val bibleDb = BibleDatabase.getDatabase(this)
        val userDb = UserDatabase.getDatabase(this)
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        FirebaseApp.initializeApp(this)

        Checkout.preload(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        val defaultTheme = if (prefs.contains("dark_mode") && !prefs.contains("theme_mode")) {
            val isDark = prefs.getBoolean("dark_mode", false)
            val mode =
                if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            prefs.edit().putInt("theme_mode", mode).apply()
            mode
        } else {
            prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        AppCompatDelegate.setDefaultNightMode(defaultTheme)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            lifecycleScope.launch(Dispatchers.IO) {
                if (user != null) {
                    OneSignal.login(user.uid)
                } else {
                    OneSignal.logout()
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(left = systemBars.left, right = systemBars.right)

            // Fix: CoordinatorLayout (root) padded aiin AppBarLayout zawk hi pad ang
            // Tichuan AppBarLayout kan hide hunah fragment kha a chung berah a in-nawr chho thei ang
            binding.appBarMain.appBarLayout.updatePadding(top = systemBars.top)

            val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
            val layoutStyle = prefs.getString("app_layout_style", "Classic")

            val bottomPadding = if (ime.bottom > 0) {
                ime.bottom
            } else if (layoutStyle == "Modern") {
                systemBars.bottom
            } else {
                0
            }
            binding.appBarMain.bottomContainer.updatePadding(bottom = bottomPadding)

            insets
        }

        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_bookmark,
                R.id.nav_pin,
                R.id.nav_search,
                R.id.nav_you,
                R.id.nav_note
            ), binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.navView.setupWithNavController(navController)
        binding.navView.setNavigationItemSelectedListener { item ->
            val handled = when (item.itemId) {
                R.id.feedback_mail -> {
                    setupFeedback(); true
                }

                R.id.about -> {
                    showAboutDialog(); true
                }

                R.id.nav_premium -> {
                    navController.navigate(R.id.nav_you); true
                }

                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
            if (handled) binding.drawerLayout.closeDrawers()
            handled
        }

        setupNavHeaderBadges()
        setupSplitModeToggle(binding.navView)

        val bottomNav = binding.appBarMain.bottomNavView
        bottomNav.setupWithNavController(navController)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (navController.currentDestination?.id != R.id.nav_home) {
                        navController.navigate(R.id.nav_home)
                    }
                    true
                }

                R.id.nav_pin -> {
                    val nhf =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                    val fragment = nhf?.childFragmentManager?.primaryNavigationFragment
                        ?: nhf?.childFragmentManager?.fragments?.firstOrNull { it is TransformFragment }

                    if (navController.currentDestination?.id == R.id.nav_home && fragment is TransformFragment) {
                        fragment.showPinSelectionPopup(bottomNav)
                        false
                    } else {
                        NavigationUI.onNavDestinationSelected(item, navController)
                    }
                }

                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        binding.appBarMain.btnBottomDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.appBarMain.btnBottomPin.setOnClickListener {
            val nhf =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
            val fragment = nhf?.childFragmentManager?.primaryNavigationFragment
                ?: nhf?.childFragmentManager?.fragments?.firstOrNull { it is TransformFragment }

            if (fragment is TransformFragment) {
                fragment.showPinSelectionPopup(binding.appBarMain.btnBottomPin)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val passageSelectionScreens = listOf(
                R.id.nav_select_passage,
                R.id.chapterGridFragment,
                R.id.verseGridFragment
            )
            val noBottomBarScreens = listOf(
                R.id.nav_you,
                R.id.nav_quiz,
                R.id.nav_bookmark,
                R.id.nav_pin,
                R.id.nav_search
            ) + passageSelectionScreens

            if (destination.id in noBottomBarScreens) {
                binding.appBarMain.bottomContainer.visibility = View.GONE
            } else {
                binding.appBarMain.bottomContainer.visibility = View.VISIBLE
            }

            // Hide main ActionBar and AppBarLayout for passage selection and search screens
            if (destination.id in passageSelectionScreens || destination.id == R.id.nav_search) {
                supportActionBar?.hide()
                binding.appBarMain.appBarLayout.visibility = View.GONE

                // Remove behavior to collapse the gap
                val params =
                    binding.appBarMain.contentMain.root.layoutParams as CoordinatorLayout.LayoutParams
                params.behavior = null
                binding.appBarMain.contentMain.root.requestLayout()
            } else {
                supportActionBar?.show()
                binding.appBarMain.appBarLayout.visibility = View.VISIBLE

                // Restore behavior
                val params =
                    binding.appBarMain.contentMain.root.layoutParams as CoordinatorLayout.LayoutParams
                params.behavior = AppBarLayout.ScrollingViewBehavior()
                binding.appBarMain.contentMain.root.requestLayout()
            }

            refreshSelectorVisibility(destination.id)
            refreshToolbarSelector()
            updateNavHistoryVisibility()
            applyLayoutStyle()
        }

        applyLayoutStyle()
        setupVersionSelector()
        setupSplitLabel()
        checkDatabaseState()
        setupToolbarButtons()
        handleImmersiveMode(resources.configuration.orientation)
        intent?.let { handleWidgetIntent(it) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSplitMode.collectLatest { refreshSelectorVisibility() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isVerticalSplit.collectLatest { refreshSelectorVisibility() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.canGoBack.collectLatest { updateNavHistoryVisibility() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.canGoForward.collectLatest { updateNavHistoryVisibility() }
            }
        }

        viewModel.availableVersions.observe(this) { }

        binding.appBarMain.btnNavBack.setOnClickListener { viewModel.goBack() }
        binding.appBarMain.btnNavForward.setOnClickListener { viewModel.goForward() }
        binding.appBarMain.layoutSelector.setOnClickListener { openChapterSelector() }
        binding.appBarMain.btnPrevChapter.setOnClickListener {
            val nhf =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            val fragment = nhf.childFragmentManager.primaryNavigationFragment
                ?: nhf.childFragmentManager.fragments.firstOrNull { it is TransformFragment }
            if (fragment is TransformFragment) fragment.goToChapter(-1)
        }
        binding.appBarMain.btnNextChapter.setOnClickListener {
            val nhf =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            val fragment = nhf.childFragmentManager.primaryNavigationFragment
                ?: nhf.childFragmentManager.fragments.firstOrNull { it is TransformFragment }
            if (fragment is TransformFragment) fragment.goToChapter(1)
        }

        lifecycleScope.launch { viewModel.currentBook.collectLatest { updateToolbarText() } }
        lifecycleScope.launch { viewModel.currentChapter.collectLatest { updateToolbarText() } }

        binding.appBarMain.progressDownloadMinimized.setOnClickListener {
            downloadDialog?.show()
            binding.appBarMain.progressDownloadMinimized.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    val nav = findNavController(R.id.nav_host_fragment_content_main)

                    // Check if we can pop the backstack
                    // This allows navigating back from Home to Search if we came from Search
                    if (nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                    } else {
                        // We are at the root
                        if (backPressedTime + 2000 > System.currentTimeMillis()) {
                            finish()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Chhuah nan hmet nawn leh rawh",
                                Toast.LENGTH_SHORT
                            ).show()
                            backPressedTime = System.currentTimeMillis()
                        }
                    }
                }
            }
        })
    }

    private fun openChapterSelector() {
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        val style = prefs.getString("chapter_selector_style", "Grid")

        if (style == "Grid") {
            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.nav_select_passage)
        } else {
            BiblePickerDialog().show(supportFragmentManager, "BiblePicker")
        }
    }

    private fun setupNavHeaderBadges() {
        if (binding.navView.headerCount == 0) return
        val headerView = binding.navView.getHeaderView(0)
        val premiumBadge = headerView.findViewById<View>(R.id.layout_premium_badge)
        val silverBadge = headerView.findViewById<View>(R.id.layout_patron_badge)
        val goldBadge = headerView.findViewById<View>(R.id.layout_live_badge)
        val headerSubtitle = headerView.findViewById<TextView>(R.id.text_header_subtitle)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.membershipType.collectLatest { type ->
                    premiumBadge?.visibility = View.GONE
                    silverBadge?.visibility = View.GONE
                    goldBadge?.visibility = View.GONE

                    when (type) {
                        MembershipType.SILVER -> {
                            silverBadge?.visibility = View.VISIBLE
                            headerSubtitle?.text = "Silver Member i ni e! ✨"
                        }

                        MembershipType.GOLD -> {
                            goldBadge?.visibility = View.VISIBLE
                            headerSubtitle?.text = "Gold Member i ni e! 💎"
                        }

                        MembershipType.FREE -> {
                            headerSubtitle?.text = getString(R.string.nav_header_subtitle)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNavHistoryVisibility()
    }

    fun startRazorpayPayment(
        type: MembershipType,
        name: String,
        address: String,
        phone: String,
        email: String
    ) {
        val checkout = Checkout()
        checkout.setKeyID("rzp_live_TSRn8yx3gcRlkk")

        viewModel.pendingMembershipType = type
        viewModel.pendingName = name
        viewModel.pendingAddress = address
        viewModel.pendingPhone = phone
        viewModel.pendingEmail = email

        val amount = if (type == MembershipType.SILVER) 20000 else 199900

        try {
            val options = JSONObject()
            options.put("name", "Mizo Go Bible")
            options.put("description", "${type.name} Membership Fee")
            options.put("theme.color", "#448AFF")
            options.put("currency", "INR")
            options.put("amount", amount)
            options.put("prefill.email", email)
            options.put("prefill.contact", phone)

            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Payment error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        val type = viewModel.pendingMembershipType ?: return
        val name = viewModel.pendingName ?: "User"
        val address = viewModel.pendingAddress ?: "Mizoram"
        val phone = viewModel.pendingPhone ?: ""
        val email = viewModel.pendingEmail ?: ""

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val accountId = user.email?.lowercase()?.trim() ?: user.uid
            val db = FirebaseFirestore.getInstance()
            val activatedAt = System.currentTimeMillis()
            val expiry =
                if (type == MembershipType.SILVER) activatedAt + (365L * 24 * 60 * 60 * 1000) else Long.MAX_VALUE

            val memberData =
                MemberInfo(accountId, name, address, phone, type.name, expiry, activatedAt)
            db.collection("public_members").document(accountId).set(memberData)

            val premiumData = mapOf(
                "active" to true,
                "type" to type.name,
                "expiresAt" to expiry,
                "activatedAt" to activatedAt,
                "name" to name,
                "address" to address,
                "phone" to phone,
                "deviceIds" to FieldValue.arrayUnion(
                    Settings.Secure.getString(
                        contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                )
            )

            db.collection("premium_users").document(accountId).set(premiumData, SetOptions.merge())
                .addOnSuccessListener {
                    viewModel.setMembership(type, expiry)

                    if (type == MembershipType.GOLD || type == MembershipType.SILVER) {
                        generateCertificateImageAndSend(type, name, address, email, expiry)
                    }

                    viewModel.pendingMembershipType = null
                    viewModel.pendingName = null
                    viewModel.pendingAddress = null
                    viewModel.pendingPhone = null
                    viewModel.pendingEmail = null

                    val nhf =
                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                    val profileFragment =
                        nhf?.childFragmentManager?.fragments?.find { it is com.zoliana.khampat.mizobible.ui.settings.ProfileFragment } as? com.zoliana.khampat.mizobible.ui.settings.ProfileFragment
                    profileFragment?.showPremiumSuccessUI(type)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error updating status: ${it.message}", Toast.LENGTH_LONG)
                        .show()
                    viewModel.pendingMembershipType = null
                }
        } else {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_LONG).show()
            viewModel.pendingMembershipType = null
        }
    }

    fun generateCertificateImageAndSend(
        type: MembershipType,
        name: String,
        address: String,
        email: String,
        expiry: Long
    ) {
        lifecycleScope.launch {
            try {
                val templateFileName =
                    if (type == MembershipType.GOLD) "citation/gold.html" else "citation/silver.html"
                val htmlContent =
                    assets.open(templateFileName).bufferedReader().use { it.readText() }
                        .replace("{{USERNAME}}", name.uppercase())
                        .replace("{{ADDRESS}}", address)
                        .replace("{{EMAIL}}", email)
                        .replace(
                            "{{VALIDITY}}",
                            if (type == MembershipType.SILVER) SimpleDateFormat(
                                "dd/MM/yyyy",
                                Locale.getDefault()
                            ).format(Date(expiry)) else "Lifetime Membership"
                        )
                        .replace(
                            "{{DATE}}",
                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                        )

                showCertificateAndCapture(htmlContent) { bitmap ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
                        val certificateFile = File(imagesDir, "certificate.jpg")
                        FileOutputStream(certificateFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        withContext(Dispatchers.Main) {
                            sendCertificateEmail(type, email, certificateFile, "image/jpeg")
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Certificate siamnaah tihsual a awm: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showCertificateAndCapture(html: String, onCaptured: (Bitmap) -> Unit) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setContentView(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.postDelayed({
                    val rootView = dialog.window!!.decorView.rootView
                    val bitmap = Bitmap.createBitmap(
                        rootView.width,
                        rootView.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    rootView.draw(canvas)
                    dialog.dismiss()
                    onCaptured(bitmap)
                }, 300)
            }
        }
        webView.loadDataWithBaseURL(
            "file:///android_asset/citation/",
            html,
            "text/html",
            "UTF-8",
            null
        )
        dialog.show()
    }


    private fun sendCertificateEmail(
        type: MembershipType,
        toEmail: String,
        file: File,
        mimeType: String
    ) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val typeName = type.name.lowercase().replaceFirstChar { it.uppercase() }

        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = mimeType
            putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Mizo Go Bible $typeName Membership Certificate")
            putExtra(
                Intent.EXTRA_TEXT,
                "Mizo Go Bible $typeName Member i ni e! I certificate hi i lo lachhuak dawn nia.\n\nKan lawm e!"
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Send certificate via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Email app a awm lo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Bible Verse"
            val descriptionText = "Nitin Bible Chang leh Sunday Notification te"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NotificationHelper.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        Toast.makeText(this, "Payment failed: $response", Toast.LENGTH_LONG).show()
        viewModel.pendingMembershipType = null
    }

    fun updateNavHistoryVisibility() {
        val nhf = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val isHome = nhf?.navController?.currentDestination?.id == R.id.nav_home
        val hasHistory = viewModel.canGoBack.value || viewModel.canGoForward.value
        binding.appBarMain.navHistoryContainer.visibility = if (isHome && hasHistory) View.VISIBLE else View.GONE
        binding.appBarMain.btnNavBack.isEnabled = viewModel.canGoBack.value
        binding.appBarMain.btnNavBack.alpha = if (viewModel.canGoBack.value) 1.0f else 0.3f
        binding.appBarMain.btnNavForward.isEnabled = viewModel.canGoForward.value
        binding.appBarMain.btnNavForward.alpha = if (viewModel.canGoForward.value) 1.0f else 0.3f
    }

    fun applyLayoutStyle() {
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        val layoutStyle = prefs.getString("app_layout_style", "Classic")

        val bottomNav = binding.appBarMain.bottomNavView
        val btnDrawer = binding.appBarMain.btnBottomDrawer
        val btnPin = binding.appBarMain.btnBottomPin
        val nhf =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val navController = nhf?.navController

        if (layoutStyle == "Modern") {
            bottomNav.visibility = View.GONE
            btnDrawer.visibility = View.VISIBLE
            btnPin.visibility = View.VISIBLE
            binding.appBarMain.toolbar.navigationIcon = null
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        } else {
            // Classic Layout (Default)
            bottomNav.visibility = View.VISIBLE
            btnDrawer.visibility = View.GONE
            btnPin.visibility = View.GONE
            if (navController != null) {
                setupActionBarWithNavController(navController, appBarConfiguration)
            }
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }


    fun refreshSelectorVisibility(destId: Int? = null) {
        val nhf =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val currentId = destId ?: nhf?.navController?.currentDestination?.id
        val isHome = currentId == R.id.nav_home
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val noSelector = listOf(
            R.id.nav_you,
            R.id.nav_quiz,
            R.id.nav_bookmark,
            R.id.nav_pin,
            R.id.nav_search,
            R.id.nav_select_passage
        )
        binding.appBarMain.layoutSelector.visibility =
            if (isHome && isPortrait && currentId !in noSelector) View.VISIBLE else View.GONE
    }

    private fun checkDatabaseState() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dbFile =
                applicationContext.getDatabasePath("mizogobible_v2.db")
            if (!dbFile.exists() || viewModel.repository.getVerseCountByVersion("MzOV") < 100) {
                withContext(Dispatchers.Main) {
                    showFirstTimeDownloadDialog()
                }
            } else {
                fetchRemoteVersions()
            }
        }
    }

    private fun showFirstTimeDownloadDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Welcome!")
            .setMessage("Mizo Go Bible i hman theih nan a database download hmasak a ngai e.")
            .setPositiveButton("Download") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    fetchRemoteVersions(true)
                }
            }
            .setCancelable(false)
            .create()
        dialog.show()
        limitDialogWidth(dialog)
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkForUpdates(isFirstTime: Boolean = false) {
        val prefs = getSharedPreferences("bible_prefs", MODE_PRIVATE)
        val remoteMap = remoteVersions ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val outdatedVersions = mutableListOf<String>()
            var updateMessage: String? = null

            val versionsToCheck = listOf("MzOV", "KJV", "NIV", "MzCL")

            versionsToCheck.forEach { vCode ->
                val isDownloaded = prefs.getBoolean("imported_$vCode", false)
                if (!isDownloaded) return@forEach

                val vData = remoteMap[vCode] as? Map<String, Any> ?: return@forEach
                val remote = (vData["version"] as? Number)?.toInt() ?: 0
                val url = (vData["url"] as? String)?.trim() ?: ""

                val local = prefs.getInt("version_$vCode", 0)
                val ignored = prefs.getInt("ignored_version_$vCode", 0)

                if (remote > local && remote > ignored && url.isNotEmpty() && !url.contains("VA_DAH_RAWH")) {
                    outdatedVersions.add(vCode)
                    if (updateMessage == null) {
                        updateMessage =
                            (vData["message"] as? String) ?: "$vCode Bible update thar a awm e."
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (isFirstTime) {
                    val vData = remoteMap["MzOV"] as? Map<String, Any>
                    val url = vData?.get("url") as? String
                    if (url != null) {
                        downloadBibleVersion(url, "MzOV")
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "MzOV download link a hmuh loh.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@withContext
                }

                binding.appBarMain.viewUpdateBadge.visibility =
                    if (outdatedVersions.isNotEmpty()) View.VISIBLE else View.GONE

                if (outdatedVersions.isNotEmpty() && updateMessage != null) {
                    val targetVCode =
                        if (outdatedVersions.contains("MzOV")) "MzOV" else outdatedVersions.first()
                    val vData = remoteMap[targetVCode] as? Map<String, Any>
                    val url = (vData?.get("url") as? String)?.trim()

                    if (!url.isNullOrEmpty() && !url.contains("VA_DAH_RAWH")) {
                        MaterialAlertDialogBuilder(this@MainActivity).setTitle("Update Thar a awm e")
                            .setMessage(updateMessage).setPositiveButton("Update") { _, _ ->
                                downloadBibleVersion(url, targetVCode)
                            }.setNegativeButton("Nakinah") { _, _ ->
                                val remoteVer = (vData?.get("version") as? Number)?.toInt() ?: 0
                                prefs.edit().putInt("ignored_version_$targetVCode", remoteVer)
                                    .apply()
                            }.show()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent) {
        val vId = intent.getIntExtra("WIDGET_VERSE_ID", -1)
        val book = intent.getStringExtra("WIDGET_BOOK")
        val chap = intent.getIntExtra("WIDGET_CHAPTER", -1)
        val ver = intent.getStringExtra("WIDGET_VERSION")
        if (vId != -1 && book != null && chap != -1) {
            ver?.let { viewModel.updateVersion(it) }; viewModel.updateSelection(book, chap, vId)
            val nhf =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            if (nhf.navController.currentDestination?.id != R.id.nav_home) nhf.navController.navigate(
                R.id.nav_home
            )
        }
    }

    private fun normalizeMizo(text: String?): String {
        if (text == null) return ""
        return text.lowercase().replace("â", "a").replace("ê", "e").replace("î", "i")
            .replace("ô", "o").replace("û", "u").replace("ṭ", "t").replace("ṛ", "r")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); refreshSelectorVisibility(); refreshToolbarSelector(); updateNavHistoryVisibility(); handleImmersiveMode(
            newConfig.orientation
        )
    }

    private fun handleImmersiveMode(orientation: Int) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val uiMode = this.resources.configuration.uiMode
        val isDark =
            (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.systemBars()); controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars()); controller.isAppearanceLightStatusBars =
                !isDark; controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun setupToolbarButtons() {
        binding.appBarMain.btnPrevToolbar.setOnClickListener {
            val nhf =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            val f = nhf.childFragmentManager.primaryNavigationFragment
                ?: nhf.childFragmentManager.fragments.firstOrNull { it is TransformFragment }; if (f is TransformFragment) f.goToChapter(
            -1
        )
        }
        binding.appBarMain.btnNextToolbar.setOnClickListener {
            val nhf =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            val f = nhf.childFragmentManager.primaryNavigationFragment
                ?: nhf.childFragmentManager.fragments.firstOrNull { it is TransformFragment }; if (f is TransformFragment) f.goToChapter(
            1
        )
        }
        binding.appBarMain.textSelectionToolbar.setOnClickListener { openChapterSelector() }
        binding.appBarMain.layoutTitlesContainer.setOnClickListener { openChapterSelector() }
        binding.appBarMain.btnToolbarSearch.setOnClickListener {
            findNavController(R.id.nav_host_fragment_content_main).navigate(
                R.id.nav_search
            )
        }
        binding.appBarMain.btnToolbarFont.setOnClickListener {
            FontSettingsDialog().show(
                supportFragmentManager,
                "FontSettings"
            )
        }
    }

    fun refreshToolbarSelector() {
        val nhf =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val currentDest = nhf?.navController?.currentDestination
        val isTransform = currentDest?.id == R.id.nav_home
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.appBarMain.toolbarChapterSelector.visibility =
            if (isTransform && isLandscape) View.VISIBLE else View.GONE
        binding.appBarMain.layoutTitlesContainer.visibility =
            if (isTransform && isLandscape) View.GONE else View.VISIBLE
        binding.appBarMain.layoutToolbarButtons.visibility =
            if (isTransform) View.VISIBLE else View.GONE
        binding.appBarMain.layoutSplitHeader.visibility =
            if (viewModel.isSplitMode.value && isTransform) View.VISIBLE else View.GONE
        updateToolbarText()
    }

    fun updateToolbarText() {
        val nhf =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val currentDest = nhf?.navController?.currentDestination
        supportActionBar?.title = ""; binding.appBarMain.toolbar.title =
            ""; supportActionBar?.setDisplayShowTitleEnabled(false)
        val bibleRef = "${viewModel.currentBook.value} ${viewModel.currentChapter.value}"
        binding.appBarMain.textSelectionToolbar.text =
            bibleRef; binding.appBarMain.textCurrentSelection.text = bibleRef
        if (currentDest?.id == R.id.nav_home) {
            binding.appBarMain.textToolbarBibleRef.text =
                bibleRef; binding.appBarMain.textToolbarBibleRef.visibility =
                View.VISIBLE; binding.appBarMain.textToolbarFragmentTitle.visibility = View.GONE
        } else {
            binding.appBarMain.textToolbarBibleRef.visibility = View.GONE
            val destName = when (currentDest?.id) {
                R.id.nav_bookmark -> getString(R.string.menu_bookmark); R.id.nav_pin -> getString(R.string.menu_pin); R.id.nav_search -> getString(
                    R.string.menu_search
                ); R.id.nav_you -> getString(R.string.menu_you); R.id.nav_settings -> getString(R.string.menu_settings); R.id.nav_quiz -> "Bible Quiz"; R.id.nav_note -> getString(
                    R.string.menu_note
                ); else -> currentDest?.label?.toString() ?: ""
            }
            binding.appBarMain.textToolbarFragmentTitle.text =
                destName; binding.appBarMain.textToolbarFragmentTitle.visibility = View.VISIBLE
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchRemoteVersions(isFirstTime: Boolean = false) {
        val dbUrl = "https://mizogobible-2c7f1-default-rtdb.firebaseio.com/"
        try {
            FirebaseDatabase.getInstance(dbUrl).reference.get().addOnSuccessListener { s ->
                var rootMap = s.value as? Map<String, Any>
                if (rootMap != null && rootMap.size == 1) {
                    val firstKey = rootMap.keys.first()
                    if (firstKey.startsWith("http")) rootMap =
                        rootMap[firstKey] as? Map<String, Any>
                }
                remoteVersions =
                    if (rootMap?.containsKey("versions") == true) rootMap["versions"] as? Map<String, Any>
                    else if (rootMap?.containsKey("MzOV") == true) rootMap else rootMap
                checkForUpdates(isFirstTime)
            }.addOnFailureListener {
                // Fallback can be removed if you only use Firebase
            }
        } catch (e: Exception) {
            // Fallback can be removed
        }
    }

    internal fun downloadBibleVersion(urlStr: String?, vCode: String) {
        val rawUrl = urlStr?.trim() ?: return
        if (rawUrl.isEmpty() || rawUrl.contains("VA_DAH_RAWH")) return
        if (downloadJob?.isActive == true) {
            Toast.makeText(this, "Download dang a kal mek e.", Toast.LENGTH_SHORT).show(); return
        }
        val progressBinding = DialogDownloadProgressBinding.inflate(layoutInflater)
        downloadProgressBinding = progressBinding
        val dialog = AlertDialog.Builder(this)
            .setView(progressBinding.root)
            .setCancelable(false)
            .create()
        downloadDialog = dialog

        progressBinding.btnMinimizeDownload.setOnClickListener {
            dialog.dismiss()
            binding.appBarMain.progressDownloadMinimized.visibility = View.VISIBLE
        }

        progressBinding.btnCloseDownload.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Download")
                .setMessage("Download hi tihtawp i duh tak zet em?")
                .setPositiveButton("Aw") { _, _ ->
                    downloadJob?.cancel()
                    dialog.dismiss()
                    binding.appBarMain.progressDownloadMinimized.visibility = View.GONE
                    Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Aih", null)
                .show()
        }

        dialog.show()
        limitDialogWidth(dialog, true)

        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                var currentUrl = rawUrl
                var connection: HttpURLConnection
                var responseCode: Int
                do {
                    if (!isActive) return@launch
                    connection = URL(currentUrl).openConnection() as HttpURLConnection
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0"
                    ); connection.instanceFollowRedirects = false; responseCode =
                        connection.responseCode
                    if (responseCode in 301..308) currentUrl = connection.getHeaderField("Location")
                        ?: throw Exception("Redirect location is null")
                    else if (responseCode == HttpURLConnection.HTTP_OK) {
                        val contentType = connection.contentType
                        if (contentType != null && contentType.contains("text/html")) {
                            val match = Regex("confirm=([a-zA-Z0-9_]+)").find(
                                connection.inputStream.bufferedReader().use { it.readText() })
                            if (match != null) {
                                currentUrl = "https://drive.google.com/uc?export=download&id=${
                                    rawUrl.substringAfter("id=").substringBefore("&")
                                }&confirm=${match.groupValues[1]}"
                                continue
                            }
                        }
                        break
                    } else break
                } while (true)

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val tempFile = File(cacheDir, "download_$vCode.db")
                val output = FileOutputStream(tempFile)
                val data = ByteArray(4096)
                var total: Long = 0
                var count = 0
                while (isActive && input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        withContext(Dispatchers.Main) {
                            downloadProgressBinding?.progressBarDownload?.progress =
                                progress; downloadProgressBinding?.textDownloadPercent?.text =
                            "$progress%"; binding.appBarMain.progressDownloadMinimized.progress =
                            progress
                        }
                    }
                    output.write(data, 0, count)
                }
                output.close(); input.close()
                if (!isActive) {
                    tempFile.delete(); return@launch
                }
                withContext(Dispatchers.Main) {
                    downloadProgressBinding?.textDownloadStatus?.text =
                        "Database update hna thawh mek a ni..."
                }
                val sourceDb = SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                val importedCount =
                    importFromSqlite(sourceDb, vCode); sourceDb.close(); tempFile.delete()
                withContext(Dispatchers.Main) {
                    downloadDialog?.dismiss()
                    binding.appBarMain.progressDownloadMinimized.visibility = View.GONE
                    if (importedCount > 1000) {
                        val remoteVer = if (remoteVersions?.containsKey(vCode) == true) {
                            val vData =
                                remoteVersions!![vCode]; if (vData is Map<*, *>) (vData["version"] as? Number)?.toInt()
                                ?: 0 else 0
                        } else 0
                        getSharedPreferences("bible_prefs", MODE_PRIVATE).edit()
                            .putBoolean("imported_$vCode", true).putInt("version_$vCode", remoteVer)
                            .apply()
                        checkForUpdates(); Toast.makeText(
                            this@MainActivity,
                            "$vCode database updated!",
                            Toast.LENGTH_SHORT
                        ).show(); viewModel.updateVersion(vCode)
                    } else Toast.makeText(
                        this@MainActivity,
                        "Import failed: Database a kim lo.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadDialog?.dismiss()
                    binding.appBarMain.progressDownloadMinimized.visibility = View.GONE
                }
            }
        }
    }

    private fun setupVersionSelector() {
        lifecycleScope.launch {
            viewModel.currentVersion.collectLatest {
                binding.appBarMain.textVersionSelector.text = getVersionDisplayName(it)
            }
        }
        binding.appBarMain.textVersionSelector.setOnClickListener { showVersionSelectionDialog() }
    }

    private fun showVersionSelectionDialog() {
        val versions = viewModel.availableVersions.value ?: emptyList()
        val versionPairs = versions.filter {
            val up = it.uppercase().trim()
            !up.startsWith("HEAD") && !up.startsWith("TITL") && !up.startsWith("SUB") &&
                    !up.startsWith("PER") && up != "VERSE"
        }.map { it to getFullVersionName(it) }
            .distinctBy { it.second }
            .sortedBy { if (it.first.uppercase() == "MZOV") "" else it.second.lowercase() }

        val names = versionPairs.map { it.second }.toMutableList()
        names.add("Download More...")

        val dialog = MaterialAlertDialogBuilder(this).setTitle("Select Version")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == names.size - 1) {
                    showDownloadableVersionsDialog()
                } else {
                    val original = versionPairs[which].first
                    if (viewModel.currentVersion.value != original) {
                        viewModel.updateVersion(original)
                    }
                }
            }.show()
        limitDialogWidth(dialog)
    }

    fun showDownloadableVersionsDialog() {
        BibleVersionDialog().show(supportFragmentManager, "BibleVersionDialog")
    }

    private fun getVersionDisplayName(v: String): String = when (v.lowercase().trim()) {
        "verse", "mizo go bible", "mgb", "mzov", "pericope", "percope" -> "MzOV"
        "kjv", "kjb" -> "KJV"
        "hindi" -> "HINDI"
        "greek", "greek (grk)" -> "GRK"
        "burmesebible" -> "MYJ"
        "niv" -> "NIV"
        "mzcl" -> "MzCL"
        else -> v.uppercase()
    }

    private fun getFullVersionName(v: String): String = when (v.lowercase().trim()) {
        "verse", "mizo go bible", "mgb", "mzov", "pericope", "percope" -> "Mizo Old Version (MzOV)"
        "kjv", "kjb" -> "King James Version (KJV)"
        "hindi" -> "Hindi Bible (HINDI)"
        "greek", "greek (grk)" -> "Greek Bible (GRK)"
        "burmesebible" -> "Myanmar Judson (MYJ)"
        "niv" -> "New International Version (NIV)"
        "mzcl" -> "Mizo Common Language (MzCL)"
        else -> v.uppercase()
    }

    private suspend fun importFromSqlite(sourceDb: SQLiteDatabase, versionCode: String): Int {
        return try {
            val cursorTable = sourceDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null
            )
            val tableNames =
                mutableListOf<String>(); while (cursorTable.moveToNext()) tableNames.add(
                cursorTable.getString(
                    0
                )
            ); cursorTable.close()
            if (tableNames.isEmpty()) return 0
            val tableName = tableNames.find { n ->
                val c = sourceDb.rawQuery("PRAGMA table_info($n)", null)
                var found = false; while (c.moveToNext()) {
                if (c.getString(1).lowercase()
                        .let { it.contains("text") || it.contains("content") }
                ) {
                    found = true; break
                }
            }; c.close(); found
            } ?: tableNames[0]
            val cursor = sourceDb.rawQuery("SELECT * FROM $tableName", null)
            val cols = cursor.columnNames.map { it.lowercase() }
            val bIdx = cols.indexOfFirst { it.contains("book") }
            val cIdx = cols.indexOfFirst { it.contains("chap") }
            val vIdx = cols.indexOfFirst { it.contains("verse") }
            val tIdx = cols.indexOfFirst { it.contains("text") || it.contains("content") }
            val allVerses = mutableListOf<BibleVerse>()
            if (cursor.moveToFirst()) {
                do {
                    val raw = if (tIdx != -1) cursor.getString(tIdx) ?: "" else ""
                    val norm = normalizeMizo(raw)
                    allVerses.add(
                        BibleVerse(
                            null,
                            versionCode,
                            if (bIdx != -1) cursor.getString(bIdx) ?: "" else "",
                            if (cIdx != -1) cursor.getInt(cIdx) else 0,
                            if (vIdx != -1) cursor.getString(vIdx) else null,
                            raw,
                            norm,
                            norm.replace(Regex("[^a-z0-9]"), "")
                        )
                    )
                } while (cursor.moveToNext())
            }; cursor.close()
            if (allVerses.isNotEmpty()) {
                BibleDatabase.getDatabase(applicationContext).withTransaction {
                    if (versionCode.uppercase() == "MZOV" || versionCode.uppercase() == "MGB") viewModel.repository.deleteMizoBible()
                    else viewModel.repository.deleteVersion(versionCode)
                    allVerses.chunked(1000).forEach { viewModel.repository.insertVerses(it) }
                }; allVerses.size
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun showAboutDialog() {
        val aboutBinding = DialogAboutBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(aboutBinding.root).create()
        try {
            aboutBinding.textAppVersion?.text =
                "Version ${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (e: Exception) {
            aboutBinding.textAppVersion?.visibility = View.GONE
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        aboutBinding.btnDeveloper?.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://api.whatsapp.com/send?phone=917005623762")
                )
            )
        }
        aboutBinding.btnWebsite?.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://khampat.com")
                )
            )
        }
        aboutBinding.btnPrivacyPolicy?.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://apps.khampat.com/mgb/privacy-policy")
                )
            )
        }
        dialog.show(); limitDialogWidth(dialog, true)
    }

    private fun setupFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:"); putExtra(
            Intent.EXTRA_EMAIL,
            arrayOf("zlphoto02@gmail.com")
        ); putExtra(Intent.EXTRA_SUBJECT, "Mizo Go Bible Feedback")
        }
        try {
            startActivity(Intent.createChooser(intent, "Send feedback..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Email app a awm lo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSplitLabel() {
        lifecycleScope.launch {
            viewModel.isSplitMode.collectLatest {
                val isTransform =
                    (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment)?.navController?.currentDestination?.id == R.id.nav_home; binding.appBarMain.layoutSplitHeader.visibility =
                if (it && isTransform) View.VISIBLE else View.GONE; updateSplitLabelText(); (binding.navView.menu.findItem(
                R.id.split
            )?.actionView as? MaterialSwitch)?.isChecked = it
            }
        }
        lifecycleScope.launch { viewModel.splitVersion.collectLatest { updateSplitLabelText() } }
        binding.appBarMain.textSplitVersionLabel.setOnClickListener { showSplitVersionSelectionDialog() }
        binding.appBarMain.btnCloseParallel.setOnClickListener { viewModel.setSplitMode(false) }
    }

    private fun updateSplitLabelText() {
        binding.appBarMain.textSplitVersionLabel.text =
            "Parallel: ${getVersionDisplayName(viewModel.splitVersion.value)}"
    }

    private fun setupSplitModeToggle(navView: NavigationView?) {
        val actionView =
            navView?.menu?.findItem(R.id.split)?.actionView as? MaterialSwitch; actionView?.isChecked =
            viewModel.isSplitMode.value; actionView?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) showSplitVersionSelectionDialog(
                actionView
            ) else viewModel.setSplitMode(false)
        }
    }

    private fun showSplitVersionSelectionDialog(switch: MaterialSwitch? = null) {
        val versions = viewModel.availableVersions.value ?: emptyList()
        val versionPairs = versions.filter {
            val up = it.uppercase().trim()
            !up.startsWith("HEAD") && !up.startsWith("TITL") && !up.startsWith("SUB") &&
                    !up.startsWith("PER") && up != "VERSE"
        }.map { it to getFullVersionName(it) }
            .distinctBy { it.second }
            .sortedBy { if (it.first.uppercase() == "MZOV") "" else it.second.lowercase() }

        val names = versionPairs.map { it.second }.toMutableList()
        names.add("Download More...")

        val dialog = AlertDialog.Builder(this).setTitle("Select Parallel Version")
            .setItems(names.toTypedArray()) { _, which ->
                if (which < versionPairs.size) {
                    val original = versionPairs[which].first
                    viewModel.updateSplitVersion(original)
                    viewModel.setSplitMode(true)
                    binding.drawerLayout.closeDrawers()
                } else {
                    showDownloadableVersionsDialog()
                    switch?.isChecked = false
                }
            }.setNegativeButton("Cancel") { _, _ -> switch?.isChecked = false }
            .show(); limitDialogWidth(dialog)
    }

    fun limitDialogWidth(dialog: Dialog, useTransparentBackground: Boolean = false) {
        dialog.window?.apply {
            val dm = resources.displayMetrics
            val maxWidth = (500 * dm.density).toInt()
            val targetWidth =
                if (dm.widthPixels > maxWidth) maxWidth else (dm.widthPixels * 0.95).toInt()
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT); setGravity(Gravity.CENTER)
            if (useTransparentBackground) setBackgroundDrawableResource(android.R.color.transparent)
            else {
                val tv = TypedValue(); context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorSurface,
                    tv,
                    true
                ); setBackgroundDrawable(ColorDrawable(tv.data))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        findNavController(R.id.nav_host_fragment_content_main).let {
            NavigationUI.navigateUp(
                it,
                appBarConfiguration
            ) || super.onSupportNavigateUp()
        }
}
