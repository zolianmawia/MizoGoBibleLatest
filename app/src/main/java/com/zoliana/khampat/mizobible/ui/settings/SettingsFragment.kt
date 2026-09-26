package com.zoliana.khampat.mizobible.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentSettingsBinding
import com.zoliana.khampat.mizobible.ui.transform.FontSettingsDialog
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.NotificationHelper
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                enableDailyVerseNotification()
            } else {
                Toast.makeText(requireContext(), "Notification phalna pek a ni lo", Toast.LENGTH_SHORT).show()
                binding.switchDailyVerseNotification?.isChecked = false
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeHelper.applyThemeToView(requireContext(), view)

        val prefs = requireContext().getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)

        // --- Notification Settings ---
        binding.switchDailyVerseNotification?.isChecked = prefs.getBoolean("daily_verse_notification", false)
        binding.switchDailyVerseNotification?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        enableDailyVerseNotification()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    enableDailyVerseNotification()
                }
            } else {
                prefs.edit().putBoolean("daily_verse_notification", false).apply()
                NotificationHelper.cancelNotification(requireContext())
            }
        }

        // --- Cloud Sync Settings ---
        binding.switchAutoSync.isChecked = prefs.getBoolean("auto_sync", true)
        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_sync", isChecked).apply()
            if (isChecked) viewModel.syncToCloud()
        }

        binding.switchBackupPins.isChecked = prefs.getBoolean("backup_pins", true)
        binding.switchBackupPins.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("backup_pins", isChecked).apply()
            if (isChecked) viewModel.syncToCloud()
        }

        binding.switchBackupNotes?.isChecked = prefs.getBoolean("backup_notes", true)
        binding.switchBackupNotes?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("backup_notes", isChecked).apply()
            if (isChecked) viewModel.syncToCloud()
        }

        binding.switchBackupBookmarks.isChecked = prefs.getBoolean("backup_bookmarks", true)
        binding.switchBackupBookmarks.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("backup_bookmarks", isChecked).apply()
            if (isChecked) viewModel.syncToCloud()
        }

        binding.btnFontSettings.setOnClickListener {
            FontSettingsDialog().show(parentFragmentManager, "FontSettings")
        }

        binding.btnThemeSettings.setOnClickListener {
            ThemeSettingsDialog().show(parentFragmentManager, "ThemeSettings")
        }

        binding.btnChapterSelectorStyle?.setOnClickListener {
            val styles = arrayOf("Dialer", "Grid")
            val currentStyle = prefs.getString("chapter_selector_style", "Grid") // Default to Grid
            val checkedItem = if (currentStyle == "Grid") 1 else 0

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chapter Selector Style")
                .setSingleChoiceItems(styles, checkedItem) { dialog, which ->
                    val selectedStyle = if (which == 1) "Grid" else "Dialer"
                    prefs.edit().putString("chapter_selector_style", selectedStyle).apply()
                    dialog.dismiss()
                }
                .show()
            (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
        }

        val hasSeenNewLayout = prefs.getBoolean("has_seen_new_navigation_layout", false)
        binding.badgeNewLayout?.visibility = if (hasSeenNewLayout) View.GONE else View.VISIBLE

        binding.btnNavigationLayoutStyle?.setOnClickListener {
            prefs.edit().putBoolean("has_seen_new_navigation_layout", true).apply()
            binding.badgeNewLayout?.visibility = View.GONE

            val styles = arrayOf("Classic Layout (Default)", "Modern Floating Bar")
            val currentStyle = prefs.getString("app_layout_style", "Classic")
            val checkedItem = if (currentStyle == "Modern") 1 else 0

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Navigation Layout Style")
                .setSingleChoiceItems(styles, checkedItem) { dialogInterface, which ->
                    val selectedStyle = if (which == 1) "Modern" else "Classic"
                    prefs.edit().putString("app_layout_style", selectedStyle).apply()
                    dialogInterface.dismiss()

                    (activity as? MainActivity)?.applyLayoutStyle()
                }
                .show()
            (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
        }

        // Eye Protection Switch logic
        binding.switchEyeProtection.isChecked = viewModel.isEyeProtectionEnabled.value
        binding.switchEyeProtection.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEyeProtectionEnabled(isChecked)
        }

        binding.switchKeepScreenOn.isChecked = viewModel.keepScreenOn.value
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setKeepScreenOn(isChecked)
        }

        // --- Bible Copy Settings ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.copyIncludeReference.collectLatest {
                binding.switchCopyIncludeReference?.isChecked = it
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.copyReferenceAtBottom.collectLatest {
                binding.switchCopyReferenceTopBottom?.isChecked = it
            }
        }

        binding.switchCopyIncludeReference?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setCopyIncludeReference(isChecked)
            binding.switchCopyReferenceTopBottom?.isEnabled = isChecked
        }

        binding.switchCopyReferenceTopBottom?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setCopyReferenceAtBottom(isChecked)
        }

        binding.btnClearCache?.setOnClickListener {
            context?.cacheDir?.deleteRecursively()
            Toast.makeText(context, "Cache cleared!", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetAll.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Data")
                .setMessage("Mizo Go Bible apps-a i data neih sa leh Cloud backup zawng zawng a bo vek dâwn a ni. I duh tak tak em?")
                .setPositiveButton("Reset") { _, _ -> viewModel.resetAllData(requireContext()) }
                .setNegativeButton("Cancel", null)
                .show()
            (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
        }

        try {
            val pInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.textAppVersion.text = "Version ${pInfo.versionName} (${pInfo.versionCode})"
        } catch (e: Exception) {
            binding.textAppVersion.visibility = View.GONE
        }
    }

    private fun enableDailyVerseNotification() {
        val prefs = requireContext().getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("daily_verse_notification", true).apply()
        NotificationHelper.scheduleNextNotification(requireContext())
        Toast.makeText(requireContext(), "Nitin Bible Chang Notification a in-On ta e", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<View>(R.id.bottom_container)?.visibility = View.GONE
        ThemeHelper.applyThemeToView(requireContext(), binding.root)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().findViewById<View>(R.id.bottom_container)?.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null; super.onDestroyView()
    }
}
