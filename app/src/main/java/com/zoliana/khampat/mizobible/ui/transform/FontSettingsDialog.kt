package com.zoliana.khampat.mizobible.ui.transform

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.DialogFontSettingsBinding
import com.zoliana.khampat.mizobible.ui.settings.ColorPickerDialog
import com.zoliana.khampat.mizobible.ui.settings.ColorSwatchAdapter
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlin.math.roundToInt

class FontSettingsDialog : BottomSheetDialogFragment() {

    private var _binding: DialogFontSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFontSettingsBinding.inflate(inflater, container, false)
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
        val activeFontColor = ThemeHelper.getFontColor(context)
        if (viewModel.fontSettings.value.fontColor != activeFontColor) {
            viewModel.updateFontColor(activeFontColor)
        }
        val currentSettings = viewModel.fontSettings.value
        setupFontList(currentSettings.fontFamily)
        setupFontColorList(activeFontColor)

        // Crash prevention: Slider value hi a step nena inmil turin setSafeSliderValue kan hmang vek ang
        setSafeSliderValue(binding.sliderFontSize, currentSettings.fontSize)
        setSafeSliderValue(binding.sliderLetterSpacing, currentSettings.letterSpacing)
        setSafeSliderValue(binding.sliderLineHeight, currentSettings.lineHeight)
        setSafeSliderValue(binding.sliderPadding, viewModel.sidePadding.value)

        updatePreview(currentSettings)
        updateValueTexts(currentSettings, viewModel.sidePadding.value)
        applyTheme()

        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            updateSettings {
                it.copy(
                    fontSize = value
                )
            }
        }
        binding.sliderLetterSpacing.addOnChangeListener { _, value, _ ->
            val cleanVal = (value * 100f).roundToInt() / 100f
            updateSettings {
                it.copy(
                    letterSpacing = cleanVal
                )
            }
        }
        binding.sliderLineHeight.addOnChangeListener { _, value, _ ->
            val cleanVal = (value * 100f).roundToInt() / 100f
            updateSettings {
                it.copy(
                    lineHeight = cleanVal
                )
            }
        }
        binding.sliderPadding.addOnChangeListener { _, value, _ ->
            viewModel.updateSidePadding(value)
            updateValueTexts(viewModel.fontSettings.value, value)
        }

        binding.btnBold.setOnClickListener { updateSettings { it.copy(isBold = !it.isBold) } }
        binding.btnItalic.setOnClickListener { updateSettings { it.copy(isItalic = !it.isItalic) } }

        binding.btnFontColorPicker?.setOnClickListener {
            val isDark = ThemeHelper.isCurrentThemeDark(requireContext())
            val opacity = ThemeHelper.getThemeOpacity(requireContext())
            val allowWhite = isDark || opacity >= 0.45f
            val currentHex = if (viewModel.fontSettings.value.fontColor.startsWith("#")) {
                viewModel.fontSettings.value.fontColor
            } else if (allowWhite) "#FFFFFF" else "#000000"

            ColorPickerDialog(
                context = requireContext(),
                initialColorHex = currentHex,
                title = if (allowWhite) "Font Color (A var/eng lam)" else "Font Color"
            ) { hex ->
                val chosenColor = try { Color.parseColor(hex) } catch (_: Exception) { null }
                if (chosenColor != null) {
                    val isColorDark = ThemeHelper.isColorDark(chosenColor)
                    if (isDark && isColorDark && opacity < 0.5f) {
                        Toast.makeText(requireContext(), "Dark mode-ah chuan rawng eng/var lam chi chauh a fiah e", Toast.LENGTH_SHORT).show()
                        return@ColorPickerDialog
                    }
                    if (!isDark && !isColorDark && opacity < 0.45f) {
                        Toast.makeText(requireContext(), "Theme Color a la en lutuk a ni. Settings-ah Theme Color ti tak (50% aia tam) pawt phei la, fonts var hman theih a ni ang.", Toast.LENGTH_LONG).show()
                        return@ColorPickerDialog
                    }
                }
                updateSettings { it.copy(fontColor = hex) }
                setupFontColorList(hex)
            }.show()
        }

        binding.btnDone.setTextColor(Color.WHITE)
        binding.btnDone.setOnClickListener { dismiss() }
    }

    private fun setSafeSliderValue(slider: Slider, value: Float) {
        try {
            slider.value = snapToStep(value, slider)
        } catch (_: Exception) {
            try {
                slider.value = slider.valueFrom
            } catch (_: Exception) {}
        }
    }

    // He function hi a pawimawh ber: Value kha stepSize hnai berah a round sak ang
    private fun snapToStep(value: Float, slider: Slider): Float {
        val step = slider.stepSize
        val from = slider.valueFrom
        val to = slider.valueTo
        val clamped = value.coerceIn(from, to)
        if (step <= 0f) return clamped

        val steps = ((clamped - from) / step).roundToInt()
        val snapped = from + (steps * step)
        val cleanSnapped = (snapped * 1000f).roundToInt() / 1000f
        return cleanSnapped.coerceIn(from, to)
    }

    private fun setupFontList(currentFont: String) {
        val fonts = mutableListOf("Default", "Sans Serif", "Serif", "Monospace")
        try {
            val assetsFonts = requireContext().assets.list("fonts")
            assetsFonts?.forEach { fileName ->
                if (fileName.endsWith(".ttf") || fileName.endsWith(".otf")) {
                    fonts.add(fileName.substringBeforeLast("."))
                }
            }
        } catch (e: Exception) {
        }

        val adapter = FontListAdapter(fonts, currentFont) { fontName ->
            updateSettings { it.copy(fontFamily = fontName) }
        }
        binding.recyclerFonts.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerFonts.adapter = adapter

        val index = fonts.indexOf(currentFont)
        if (index != -1) binding.recyclerFonts.scrollToPosition(index)
    }

    private fun setupFontColorList(currentColor: String) {
        val context = requireContext()
        val isDark = ThemeHelper.isCurrentThemeDark(context)
        val opacity = ThemeHelper.getThemeOpacity(context)
        val availableOptions = ThemeHelper.getAvailableFontColors(context)

        binding.textFontColorSectionTitle?.text = if (isDark || opacity >= 0.45f) {
            "Font Color (A var / eng lam chi)"
        } else {
            "Font Color (A fiah / thim lam chi)"
        }

        val swatchItems = availableOptions.map { option ->
            ColorSwatchAdapter.SwatchItem(
                id = option.hex,
                name = option.name,
                colorInt = option.displayColor,
                hexValue = option.hex
            )
        }

        val adapter = ColorSwatchAdapter(swatchItems, currentColor) { item ->
            updateSettings { it.copy(fontColor = item.hexValue) }
        }
        binding.recyclerFontColors?.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerFontColors?.adapter = adapter

        val index = swatchItems.indexOfFirst { it.id.equals(currentColor, ignoreCase = true) }
        if (index != -1) binding.recyclerFontColors?.scrollToPosition(index)
    }

    private fun updateSettings(update: (FontSettings) -> FontSettings) {
        val newSettings = update(viewModel.fontSettings.value)
        viewModel.updateFontSettings(newSettings)
        ThemeHelper.setFontColor(requireContext(), newSettings.fontColor)
        ThemeHelper.setCustomIconColor(requireContext(), newSettings.fontColor)
        (activity as? MainActivity)?.applyThemeColors()
        updatePreview(newSettings)
        updateValueTexts(newSettings, viewModel.sidePadding.value)
        applyTheme()
    }

    fun applyTheme() {
        val ctx = context ?: return
        val bgColor = ThemeHelper.getEffectiveToolbarColor(ctx)
        val cardColor = ThemeHelper.getEffectiveCardColor(ctx) ?: bgColor
        val primaryColor = ThemeHelper.getPrimaryColor(ctx)
        val fontColor = ThemeHelper.getEffectiveFontColor(ctx)
        val iconColor = ThemeHelper.getEffectiveIconColor(ctx) ?: primaryColor
        val isDark = ThemeHelper.isColorDark(bgColor)

        val density = resources.displayMetrics.density
        val dialogBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                28f * density, 28f * density,
                28f * density, 28f * density,
                0f, 0f, 0f, 0f
            )
            setColor(bgColor)
        }
        binding.root.background = dialogBg

        val titleColor = ThemeHelper.getContrastingTextColor(bgColor, fontColor)
        val subtitleColor = ColorUtils.setAlphaComponent(titleColor, 180)

        binding.dialogHandle?.setBackgroundColor(ColorUtils.setAlphaComponent(titleColor, 50))
        binding.textFontSettingsTitle?.setTextColor(titleColor)

        binding.cardPreview?.setCardBackgroundColor(cardColor)
        binding.cardPreview?.strokeColor = if (isDark) Color.parseColor("#25FFFFFF") else Color.parseColor("#15000000")
        binding.cardPreview?.strokeWidth = (1 * density).toInt()

        binding.textFamilyTitle?.setTextColor(subtitleColor)
        binding.textFontColorSectionTitle?.setTextColor(subtitleColor)
        binding.textFontColorSectionTitle?.text = if (isDark) {
            "Font Color (Dark Mode - A var lam chi chauh)"
        } else {
            "Font Color (Light Mode - A fiah lam chi chauh)"
        }

        binding.btnFontColorPicker?.backgroundTintList = ColorStateList.valueOf(cardColor)
        binding.btnFontColorPicker?.setTextColor(primaryColor)
        binding.btnFontColorPicker?.iconTint = ColorStateList.valueOf(primaryColor)
        binding.btnFontColorPicker?.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 80))

        binding.textFontSizeTitle?.setTextColor(subtitleColor)
        binding.textLineHeightTitle?.setTextColor(subtitleColor)
        binding.textLetterSpacingTitle?.setTextColor(subtitleColor)
        binding.textPaddingTitle?.setTextColor(subtitleColor)

        binding.textFontSizeVal.setTextColor(primaryColor)
        binding.textLetterSpacingVal.setTextColor(primaryColor)
        binding.textLineHeightVal.setTextColor(primaryColor)
        binding.textPaddingVal.setTextColor(primaryColor)

        fun styleSlider(slider: Slider) {
            slider.thumbTintList = ColorStateList.valueOf(primaryColor)
            slider.trackActiveTintList = ColorStateList.valueOf(primaryColor)
            slider.trackInactiveTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 60))
            slider.haloTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 40))
        }
        styleSlider(binding.sliderFontSize)
        styleSlider(binding.sliderLetterSpacing)
        styleSlider(binding.sliderLineHeight)
        styleSlider(binding.sliderPadding)

        val btnTextColor = ThemeHelper.getContrastingTextColor(cardColor, fontColor)
        val isBold = viewModel.fontSettings.value.isBold
        val isItalic = viewModel.fontSettings.value.isItalic

        binding.btnBold.backgroundTintList = ColorStateList.valueOf(if (isBold) ColorUtils.setAlphaComponent(primaryColor, 50) else cardColor)
        binding.btnBold.setTextColor(if (isBold) primaryColor else btnTextColor)
        binding.btnBold.iconTint = ColorStateList.valueOf(primaryColor)
        binding.btnBold.strokeColor = ColorStateList.valueOf(if (isBold) primaryColor else ColorUtils.setAlphaComponent(btnTextColor, 40))

        binding.btnItalic.backgroundTintList = ColorStateList.valueOf(if (isItalic) ColorUtils.setAlphaComponent(primaryColor, 50) else cardColor)
        binding.btnItalic.setTextColor(if (isItalic) primaryColor else btnTextColor)
        binding.btnItalic.iconTint = ColorStateList.valueOf(primaryColor)
        binding.btnItalic.strokeColor = ColorStateList.valueOf(if (isItalic) primaryColor else ColorUtils.setAlphaComponent(btnTextColor, 40))

        binding.btnDone.backgroundTintList = ColorStateList.valueOf(primaryColor)
        binding.btnDone.setTextColor(Color.WHITE)

        binding.recyclerFonts.adapter?.notifyDataSetChanged()
    }

    private fun updateValueTexts(settings: FontSettings, padding: Float) {
        val b = _binding ?: return
        b.textFontSizeVal.text = settings.fontSize.toInt().toString()
        b.textPaddingVal.text = padding.toInt().toString()

        val displayLetterSpacing = if (kotlin.math.abs(settings.letterSpacing) < 0.001f) 0.0f else settings.letterSpacing
        b.textLetterSpacingVal.text = String.format(java.util.Locale.US, "%.2f", displayLetterSpacing)

        val displayLineHeight = if (kotlin.math.abs(settings.lineHeight) < 0.001f) 0.0f else settings.lineHeight
        b.textLineHeightVal.text = String.format(java.util.Locale.US, "%.2f", displayLineHeight)

        if (settings.isBold) {
            b.btnBold.setIconResource(R.drawable.ic_check_24)
            b.btnBold.alpha = 1.0f
        } else {
            b.btnBold.icon = null
            b.btnBold.alpha = 0.5f
        }

        if (settings.isItalic) {
            b.btnItalic.setIconResource(R.drawable.ic_check_24)
            b.btnItalic.alpha = 1.0f
        } else {
            b.btnItalic.icon = null
            b.btnItalic.alpha = 0.5f
        }
    }

    private fun updatePreview(settings: FontSettings) {
        val b = _binding ?: return
        val ctx = context ?: return
        b.textPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSize)
        b.textPreview.letterSpacing = settings.letterSpacing
        val density = resources.displayMetrics.density
        val mult = (1.0f + settings.lineHeight * 0.7f).coerceIn(0.65f, 2.5f)
        val add = (settings.lineHeight * 2f * density).coerceIn(-3f * density, 6f * density)
        b.textPreview.setLineSpacing(add, mult)

        val effectiveVerseBg = ThemeHelper.getEffectiveToolbarColor(ctx)
        val effectiveCardBg = ThemeHelper.getEffectiveCardColor(ctx) ?: effectiveVerseBg
        b.cardPreview?.setCardBackgroundColor(effectiveCardBg)

        val customColor = try {
            if (settings.fontColor.isNotBlank() && settings.fontColor != "default") {
                Color.parseColor(settings.fontColor)
            } else null
        } catch (_: Exception) { null }

        val finalTextColor = ThemeHelper.getContrastingTextColor(effectiveCardBg, customColor)
        b.textPreview.setTextColor(finalTextColor)

        val style = when {
            settings.isBold && settings.isItalic -> Typeface.BOLD_ITALIC
            settings.isBold -> Typeface.BOLD
            settings.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        val tf = getFontTypeface(settings.fontFamily, style)
        b.textPreview.typeface = tf
    }

    private fun getFontTypeface(fontName: String, style: Int): Typeface {
        return try {
            when (fontName) {
                "Default" -> Typeface.create(Typeface.DEFAULT, style)
                "Sans Serif" -> Typeface.create(Typeface.SANS_SERIF, style)
                "Serif", "Times New Roman" -> Typeface.create(Typeface.SERIF, style)
                "Monospace" -> Typeface.create(Typeface.MONOSPACE, style)
                else -> {
                    val extensions = listOf(".ttf", ".otf")
                    var assetTf: Typeface? = null
                    for (ext in extensions) {
                        try {
                            assetTf = Typeface.createFromAsset(
                                requireContext().assets,
                                "fonts/$fontName$ext"
                            )
                            break
                        } catch (e: Exception) {
                        }
                    }
                    if (assetTf != null) Typeface.create(assetTf, style) else Typeface.create(
                        Typeface.DEFAULT,
                        style
                    )
                }
            }
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, style)
        }
    }

    inner class FontListAdapter(
        private val fonts: List<String>,
        private var selectedFont: String,
        private val onFontSelected: (String) -> Unit
    ) : RecyclerView.Adapter<FontListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textFontName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val fontName = fonts[position]
            holder.textFontName.text = fontName
            holder.textFontName.setPadding(32, 16, 32, 16)
            holder.textFontName.textSize = 14f

            val tf = getFontTypeface(fontName, Typeface.NORMAL)
            holder.textFontName.typeface = tf

            val primaryColor = ThemeHelper.getPrimaryColor(holder.itemView.context)
            val fontColor = ThemeHelper.getEffectiveFontColor(holder.itemView.context)
            val bgColor = ThemeHelper.getEffectiveToolbarColor(holder.itemView.context)
            val normalTextColor = ThemeHelper.getContrastingTextColor(bgColor, fontColor)

            if (fontName == selectedFont) {
                holder.textFontName.setTextColor(primaryColor)
                holder.textFontName.setBackgroundResource(R.drawable.bg_selection_box)
            } else {
                holder.textFontName.setTextColor(normalTextColor)
                holder.textFontName.background = null
            }

            holder.itemView.setOnClickListener {
                val oldIndex = fonts.indexOf(selectedFont)
                selectedFont = fontName
                notifyItemChanged(oldIndex)
                notifyItemChanged(position)
                onFontSelected(fontName)
            }
        }

        override fun getItemCount() = fonts.size
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}