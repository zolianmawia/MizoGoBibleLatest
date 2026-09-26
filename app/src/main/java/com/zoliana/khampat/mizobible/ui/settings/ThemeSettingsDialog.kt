package com.zoliana.khampat.mizobible.ui.settings

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.DialogThemeSettingsBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.ThemeHelper

class ThemeSettingsDialog : BottomSheetDialogFragment() {

    private var _binding: DialogThemeSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private lateinit var presetAdapter: ThemePresetAdapter
    private var initialPreset: ThemeHelper.ThemePreset = ThemeHelper.ThemePreset.SYSTEM
    private var selectedPreset: ThemeHelper.ThemePreset = ThemeHelper.ThemePreset.SYSTEM

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogThemeSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                it.setBackgroundResource(android.R.color.transparent)

                val width = resources.displayMetrics.widthPixels
                val tabletWidth = (600 * resources.displayMetrics.density).toInt()
                if (width > tabletWidth) {
                    val params = it.layoutParams
                    params.width = tabletWidth
                    it.layoutParams = params
                }
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        initialPreset = ThemeHelper.getSelectedThemePreset(context)
        selectedPreset = initialPreset

        setupThemePresetsRecycler()
        setupActionButtons()
        updatePreview()
    }

    private fun setupThemePresetsRecycler() {
        val presets = ThemeHelper.ThemePreset.entries

        presetAdapter = ThemePresetAdapter(
            items = presets,
            selectedPreset = selectedPreset,
            onSelected = { preset ->
                selectedPreset = preset
                updatePreview()
            }
        )

        binding.recyclerThemePresets.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerThemePresets.adapter = presetAdapter
    }

    private fun setupActionButtons() {
        binding.btnResetDefaults.setOnClickListener {
            selectedPreset = ThemeHelper.ThemePreset.SYSTEM
            presetAdapter.setSelected(ThemeHelper.ThemePreset.SYSTEM)
            updatePreview()
            Toast.makeText(requireContext(), "Default (System)-ah reset a ni e", Toast.LENGTH_SHORT).show()
        }

        binding.btnApplyTheme.setOnClickListener {
            val context = requireContext()
            ThemeHelper.applyThemePreset(context, selectedPreset)

            val needsActivityRecreate = (selectedPreset != initialPreset)

            (activity as? MainActivity)?.applyThemeColors()

            dismiss()

            if (needsActivityRecreate) {
                activity?.recreate()
            } else {
                Toast.makeText(context, "${selectedPreset.displayName} theme apply fel a ni e", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePreview() {
        val context = requireContext()
        val isSystemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        // 1. Determine Preview Background / Toolbar Color
        val toolbarColor = when (selectedPreset) {
            ThemeHelper.ThemePreset.SYSTEM -> if (isSystemNight) Color.parseColor("#222333") else Color.parseColor("#FAF7F0")
            ThemeHelper.ThemePreset.LIGHT -> Color.parseColor("#FAF7F0")
            ThemeHelper.ThemePreset.NIGHT -> Color.parseColor("#222333")
            ThemeHelper.ThemePreset.RED -> Color.parseColor("#FFF0F2")
            ThemeHelper.ThemePreset.BLUE -> Color.parseColor("#EBF3FA")
            ThemeHelper.ThemePreset.GREEN -> Color.parseColor("#EDF7EE")
            ThemeHelper.ThemePreset.YELLOW -> Color.parseColor("#FEF9E7")
            ThemeHelper.ThemePreset.WHITE -> Color.parseColor("#FFFFFF")
        }

        // 2. Determine Card Color
        val cardColor = when (selectedPreset) {
            ThemeHelper.ThemePreset.SYSTEM -> if (isSystemNight) Color.parseColor("#2E2F45") else Color.parseColor("#EBE2CF")
            ThemeHelper.ThemePreset.LIGHT -> Color.parseColor("#EBE2CF")
            ThemeHelper.ThemePreset.NIGHT -> Color.parseColor("#2E2F45")
            ThemeHelper.ThemePreset.RED -> Color.parseColor("#FCE4EC")
            ThemeHelper.ThemePreset.BLUE -> Color.parseColor("#DCEBF7")
            ThemeHelper.ThemePreset.GREEN -> Color.parseColor("#DCEDDD")
            ThemeHelper.ThemePreset.YELLOW -> Color.parseColor("#FBF0CB")
            ThemeHelper.ThemePreset.WHITE -> Color.parseColor("#F3F4F6")
        }

        // 3. Determine Primary Accent Color
        val primaryColor = when (selectedPreset) {
            ThemeHelper.ThemePreset.SYSTEM -> if (isSystemNight) Color.parseColor("#90CAF9") else Color.parseColor("#1976D2")
            else -> try {
                Color.parseColor(selectedPreset.primaryHex)
            } catch (_: Exception) {
                Color.parseColor("#1976D2")
            }
        }

        binding.previewMockBody.setBackgroundColor(toolbarColor)
        binding.previewMockToolbar.setBackgroundColor(toolbarColor)
        binding.previewMockCard.setCardBackgroundColor(cardColor)

        val isCardDark = ThemeHelper.isColorDark(cardColor)
        binding.previewMockCard.strokeColor = if (isCardDark) {
            Color.parseColor("#25FFFFFF")
        } else {
            Color.parseColor("#15000000")
        }

        val isToolbarDark = ThemeHelper.isColorDark(toolbarColor)

        // 4. Custom font color from Font Settings if set
        val customFontHex = ThemeHelper.getFontColor(context)
        val customFontColor = try {
            if (customFontHex.isNotBlank() && customFontHex != "default") {
                Color.parseColor(customFontHex)
            } else null
        } catch (_: Exception) {
            null
        }

        val effectiveFont = ThemeHelper.getContrastingTextColor(cardColor, customFontColor)
        val tbTextColor = ThemeHelper.getContrastingTextColor(toolbarColor, customFontColor)

        binding.textPreviewReference.setTextColor(primaryColor)
        binding.textPreviewContent.setTextColor(effectiveFont)
        binding.previewMockCardText.setTextColor(effectiveFont)
        binding.previewMockCardIcon.setColorFilter(primaryColor)
        binding.previewMockSectionHeader.setTextColor(tbTextColor)

        binding.previewMockToolbarTitle.setTextColor(tbTextColor)
        binding.previewMockToolbarBack.setColorFilter(tbTextColor)
        binding.previewMockToolbarSearch.setColorFilter(tbTextColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
