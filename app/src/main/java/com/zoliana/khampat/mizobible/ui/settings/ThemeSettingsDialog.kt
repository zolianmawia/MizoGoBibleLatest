package com.zoliana.khampat.mizobible.ui.settings

import android.app.Dialog
import android.content.res.ColorStateList
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
    private var initialOpacity: Float = 0.0f
    private var currentOpacity: Float = 0.0f

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
        initialOpacity = ThemeHelper.getThemeOpacity(context)
        currentOpacity = initialOpacity

        setupThemePresetsRecycler()
        setupOpacitySlider()
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

    private fun setupOpacitySlider() {
        val sliderVal = (currentOpacity * 100f).coerceIn(0f, 100f)
        val roundedVal = (Math.round(sliderVal / 5f) * 5f).coerceIn(0f, 100f)
        try {
            binding.sliderThemeOpacity.value = roundedVal
        } catch (_: Exception) {
            try { binding.sliderThemeOpacity.value = 0f } catch (_: Exception) {}
        }
        binding.textOpacityValue.text = "${roundedVal.toInt()}%"

        binding.sliderThemeOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentOpacity = (value / 100f).coerceIn(0f, 1f)
                binding.textOpacityValue.text = "${value.toInt()}%"
                updatePreview()
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnResetDefaults.setOnClickListener {
            selectedPreset = ThemeHelper.ThemePreset.SYSTEM
            presetAdapter.setSelected(ThemeHelper.ThemePreset.SYSTEM)
            currentOpacity = 0.0f
            try {
                binding.sliderThemeOpacity.value = 0f
            } catch (_: Exception) {}
            binding.textOpacityValue.text = "0%"
            updatePreview()
            Toast.makeText(requireContext(), "Default (System)-ah reset a ni e", Toast.LENGTH_SHORT).show()
        }

        binding.btnApplyTheme.setOnClickListener {
            val context = requireContext()
            ThemeHelper.setThemeOpacity(context, currentOpacity)
            ThemeHelper.applyThemePreset(context, selectedPreset)

            (activity as? MainActivity)?.applyThemeColors()
            viewModel.refreshFontSettings()

            dismiss()
            activity?.recreate()
        }
    }

    private fun updatePreview() {
        val context = requireContext()
        val isSystemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        // 1. Determine Preview Background / Toolbar Color with current opacity
        val toolbarColor = ThemeHelper.getEffectiveToolbarColorForPreset(selectedPreset, context, currentOpacity)

        // 2. Determine Card Color with current opacity
        val cardColor = ThemeHelper.getEffectiveCardColorForPreset(selectedPreset, context, currentOpacity)

        // 3. Determine Primary Accent Color with current opacity
        val primaryColor = ThemeHelper.getPrimaryColorForPreset(selectedPreset, context, currentOpacity)

        binding.previewMockBody.setBackgroundColor(toolbarColor)
        binding.previewMockToolbar.setBackgroundColor(toolbarColor)
        binding.previewMockCard.setCardBackgroundColor(cardColor)

        val isCardDark = ThemeHelper.isColorDark(cardColor)
        binding.previewMockCard.strokeColor = if (isCardDark) {
            Color.parseColor("#25FFFFFF")
        } else {
            Color.parseColor("#15000000")
        }

        // 4. Custom font color from Font Settings if set for previewed mode
        val isPreviewDark = (selectedPreset == ThemeHelper.ThemePreset.NIGHT) ||
                (selectedPreset == ThemeHelper.ThemePreset.SYSTEM && isSystemNight)
        val customFontHex = ThemeHelper.getFontColorForMode(context, isPreviewDark)
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

        // 5. Opacity slider styling
        binding.sliderThemeOpacity.thumbTintList = ColorStateList.valueOf(primaryColor)
        binding.sliderThemeOpacity.trackActiveTintList = ColorStateList.valueOf(primaryColor)
        binding.sliderThemeOpacity.trackInactiveTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 50))
        binding.textOpacityTitle.setTextColor(primaryColor)
        binding.textOpacityValue.setTextColor(primaryColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
