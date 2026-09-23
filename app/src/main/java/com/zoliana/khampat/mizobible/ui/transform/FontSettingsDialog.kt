package com.zoliana.khampat.mizobible.ui.transform

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.DialogFontSettingsBinding
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

        val currentSettings = viewModel.fontSettings.value
        setupFontList(currentSettings.fontFamily)

        // Crash prevention: Slider value hi a step nena inmil turin snapToStep kan hmang vek ang
        binding.sliderFontSize.value = snapToStep(currentSettings.fontSize, binding.sliderFontSize)
        binding.sliderLetterSpacing.value =
            snapToStep(currentSettings.letterSpacing, binding.sliderLetterSpacing)
        binding.sliderLineHeight.value =
            snapToStep(currentSettings.lineHeight, binding.sliderLineHeight)
        binding.sliderPadding.value = snapToStep(viewModel.sidePadding.value, binding.sliderPadding)

        updatePreview(currentSettings)
        updateValueTexts(currentSettings, viewModel.sidePadding.value)

        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            updateSettings {
                it.copy(
                    fontSize = value
                )
            }
        }
        binding.sliderLetterSpacing.addOnChangeListener { _, value, _ ->
            updateSettings {
                it.copy(
                    letterSpacing = value
                )
            }
        }
        binding.sliderLineHeight.addOnChangeListener { _, value, _ ->
            updateSettings {
                it.copy(
                    lineHeight = value
                )
            }
        }
        binding.sliderPadding.addOnChangeListener { _, value, _ ->
            viewModel.updateSidePadding(value)
            updateValueTexts(viewModel.fontSettings.value, value)
        }

        binding.btnBold.setOnClickListener { updateSettings { it.copy(isBold = !it.isBold) } }
        binding.btnItalic.setOnClickListener { updateSettings { it.copy(isItalic = !it.isItalic) } }
        binding.btnDone.setOnClickListener { dismiss() }
    }

    // He function hi a pawimawh ber: Value kha stepSize hnai berah a round sak ang
    private fun snapToStep(value: Float, slider: Slider): Float {
        val step = slider.stepSize
        val from = slider.valueFrom
        val to = slider.valueTo
        val clamped = value.coerceIn(from, to)
        if (step <= 0f) return clamped

        val steps = ((clamped - from) / step).roundToInt()
        return from + (steps * step)
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

    private fun updateSettings(update: (FontSettings) -> FontSettings) {
        val newSettings = update(viewModel.fontSettings.value)
        viewModel.updateFontSettings(newSettings)
        updatePreview(newSettings)
        updateValueTexts(newSettings, viewModel.sidePadding.value)
    }

    private fun updateValueTexts(settings: FontSettings, padding: Float) {
        binding.textFontSizeVal.text = settings.fontSize.toInt().toString()
        binding.textPaddingVal.text = padding.toInt().toString()
        binding.textLetterSpacingVal.text = String.format("%.2f", settings.letterSpacing)
        binding.textLineHeightVal.text = String.format("%.2f", settings.lineHeight)

        if (settings.isBold) {
            binding.btnBold.setIconResource(R.drawable.ic_check_24)
            binding.btnBold.alpha = 1.0f
        } else {
            binding.btnBold.icon = null
            binding.btnBold.alpha = 0.5f
        }

        if (settings.isItalic) {
            binding.btnItalic.setIconResource(R.drawable.ic_check_24)
            binding.btnItalic.alpha = 1.0f
        } else {
            binding.btnItalic.icon = null
            binding.btnItalic.alpha = 0.5f
        }
    }

    private fun updatePreview(settings: FontSettings) {
        binding.textPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSize)
        binding.textPreview.letterSpacing = settings.letterSpacing
        binding.textPreview.setLineSpacing(0f, settings.lineHeight)

        val style = when {
            settings.isBold && settings.isItalic -> Typeface.BOLD_ITALIC
            settings.isBold -> Typeface.BOLD
            settings.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        val tf = getFontTypeface(settings.fontFamily, style)
        binding.textPreview.typeface = tf
    }

    private fun getFontTypeface(fontName: String, style: Int): Typeface {
        return try {
            when (fontName) {
                "Default" -> Typeface.create(Typeface.DEFAULT, style)
                "Sans Serif" -> Typeface.create(Typeface.SANS_SERIF, style)
                "Serif" -> Typeface.create(Typeface.SERIF, style)
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

            val typedValue = TypedValue()
            val theme = holder.itemView.context.theme

            if (fontName == selectedFont) {
                if (theme.resolveAttribute(
                        androidx.appcompat.R.attr.colorPrimary,
                        typedValue,
                        true
                    )
                ) {
                    holder.textFontName.setTextColor(typedValue.data)
                }
                holder.textFontName.setBackgroundResource(R.drawable.bg_selection_box)
            } else {
                if (theme.resolveAttribute(
                        com.google.android.material.R.attr.colorOnSurface,
                        typedValue,
                        true
                    )
                ) {
                    holder.textFontName.setTextColor(typedValue.data)
                }
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