package com.zoliana.khampat.mizobible.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import androidx.recyclerview.widget.LinearLayoutManager
import com.zoliana.khampat.mizobible.databinding.DialogColorPickerBinding

class ColorPickerDialog(
    context: Context,
    private val initialColorHex: String,
    private val title: String = "Color Picker",
    private val onColorSelected: (String) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogColorPickerBinding
    private val hsv = FloatArray(3)
    private var currentColorInt: Int = Color.parseColor("#1976D2")
    private var isUpdatingSliders = false

    companion object {
        val QUICK_COLORS = listOf(
            "#D32F2F" to "Red",
            "#C2185B" to "Crimson",
            "#E91E63" to "Pink",
            "#7B1FA2" to "Purple",
            "#512DA8" to "Deep Purple",
            "#303F9F" to "Indigo",
            "#1976D2" to "Blue",
            "#0288D1" to "Sky Blue",
            "#0097A7" to "Cyan",
            "#00796B" to "Teal",
            "#388E3C" to "Green",
            "#689F38" to "Light Green",
            "#FBC02D" to "Yellow",
            "#FFA000" to "Amber",
            "#F57C00" to "Orange",
            "#E64A19" to "Terracotta",
            "#5D4037" to "Brown",
            "#455A64" to "Blue Grey",
            "#263238" to "Charcoal",
            "#000000" to "Black",
            "#FFFFFF" to "White"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = DialogColorPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.textPickerTitle.text = title

        // 1. Initial color parsing
        currentColorInt = try {
            if (initialColorHex.startsWith("#")) Color.parseColor(initialColorHex)
            else if (initialColorHex.isNotBlank()) Color.parseColor("#$initialColorHex")
            else Color.parseColor("#1976D2")
        } catch (_: Exception) {
            Color.parseColor("#1976D2")
        }

        binding.viewInitialColor.setBackgroundColor(currentColorInt)
        Color.colorToHSV(currentColorInt, hsv)

        // 2. Setup Tracks
        setupHueTrack()

        // 3. Setup Quick Palette
        setupQuickPalette()

        // 4. Setup Sliders
        setupSliders()

        // 5. Update UI
        updateColorDisplay()

        // 6. Action buttons
        binding.btnPickerClose.setOnClickListener { dismiss() }
        binding.btnPickerCancel.setOnClickListener { dismiss() }
        binding.btnPickerSelect.setOnClickListener {
            val hex = formatHex(currentColorInt)
            onColorSelected(hex)
            dismiss()
        }

        binding.layoutInitialColor.setOnClickListener {
            Color.colorToHSV(currentColorInt, hsv)
            syncSlidersFromHsv()
            updateColorDisplay()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val dm = context.resources.displayMetrics
            val maxDialogWidth = (420 * dm.density).toInt()
            val width = (dm.widthPixels * 0.92).toInt().coerceAtMost(maxDialogWidth)
            window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {}
    }

    private fun setupHueTrack() {
        try {
            val hueColors = intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
            )
            val hueDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, hueColors).apply {
                cornerRadius = 10f
            }
            binding.viewHueTrack.background = hueDrawable
        } catch (_: Exception) {}
    }

    private fun setupQuickPalette() {
        try {
            val swatchItems = QUICK_COLORS.map { (hex, name) ->
                ColorSwatchAdapter.SwatchItem(
                    id = hex,
                    name = name,
                    colorInt = Color.parseColor(hex),
                    hexValue = hex
                )
            }

            val adapter = ColorSwatchAdapter(
                items = swatchItems,
                selectedId = formatHex(currentColorInt)
            ) { item ->
                currentColorInt = item.colorInt
                Color.colorToHSV(currentColorInt, hsv)
                syncSlidersFromHsv()
                updateColorDisplay()
            }

            binding.recyclerQuickPalette.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            binding.recyclerQuickPalette.adapter = adapter
        } catch (_: Exception) {}
    }

    private fun setupSliders() {
        syncSlidersFromHsv()

        binding.sliderHue.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingSliders) {
                hsv[0] = value.coerceIn(0f, 360f)
                currentColorInt = Color.HSVToColor(hsv)
                updateColorDisplay()
            }
        }

        binding.sliderSaturation.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingSliders) {
                hsv[1] = (value / 100f).coerceIn(0f, 1f)
                currentColorInt = Color.HSVToColor(hsv)
                updateColorDisplay()
            }
        }

        binding.sliderValue.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingSliders) {
                hsv[2] = (value / 100f).coerceIn(0f, 1f)
                currentColorInt = Color.HSVToColor(hsv)
                updateColorDisplay()
            }
        }
    }

    private fun syncSlidersFromHsv() {
        isUpdatingSliders = true
        try {
            binding.sliderHue.value = hsv[0].coerceIn(0f, 360f)
            binding.sliderSaturation.value = (hsv[1] * 100f).coerceIn(0f, 100f)
            binding.sliderValue.value = (hsv[2] * 100f).coerceIn(0f, 100f)
        } catch (_: Exception) {}
        isUpdatingSliders = false
    }

    private fun updateColorDisplay() {
        val hex = formatHex(currentColorInt)
        binding.viewPreviewColor.setBackgroundColor(currentColorInt)
        binding.textSelectedHex.text = hex

        // Slider Thumb Colors
        try {
            val colorStateList = ColorStateList.valueOf(currentColorInt)
            binding.sliderHue.thumbTintList = colorStateList
            binding.sliderSaturation.thumbTintList = colorStateList
            binding.sliderValue.thumbTintList = colorStateList
        } catch (_: Exception) {}

        // Text indicator values
        binding.textHueVal.text = "${hsv[0].toInt()}°"
        binding.textSatVal.text = "${(hsv[1] * 100).toInt()}%"
        binding.textValVal.text = "${(hsv[2] * 100).toInt()}%"

        // Dynamic gradients on Saturation and Value tracks
        try {
            val currentHue = hsv[0]
            val currentVal = hsv[2].coerceAtLeast(0.3f)
            val satStart = Color.HSVToColor(floatArrayOf(currentHue, 0f, currentVal))
            val satEnd = Color.HSVToColor(floatArrayOf(currentHue, 1f, currentVal))
            val satDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(satStart, satEnd)).apply {
                cornerRadius = 10f
            }
            binding.viewSatTrack.background = satDrawable

            val valStart = Color.BLACK
            val valEnd = Color.HSVToColor(floatArrayOf(currentHue, hsv[1], 1f))
            val valDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(valStart, valEnd)).apply {
                cornerRadius = 10f
            }
            binding.viewValTrack.background = valDrawable
        } catch (_: Exception) {}
    }

    private fun formatHex(colorInt: Int): String {
        return String.format("#%06X", 0xFFFFFF and colorInt)
    }
}
