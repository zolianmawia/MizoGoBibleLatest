package com.zoliana.khampat.mizobible.utils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.CompoundButtonCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.zoliana.khampat.mizobible.R

object ThemeHelper {

    private const val PREFS_NAME = "bible_prefs"
    const val KEY_THEME_PRESET = "app_theme_preset"
    const val KEY_APP_COLOR = "app_color_theme"
    const val KEY_THEME_STYLE = "app_theme_style"
    const val KEY_FONT_COLOR = "font_color"
    const val KEY_ICON_COLOR = "custom_icon_color"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_CUSTOM_THEME_COLOR = "custom_theme_mode_color"
    const val KEY_TOOLBAR_COLOR = "custom_toolbar_color"
    const val KEY_CARD_COLOR = "custom_card_color"

    // Bookmark Color Palette (Dal deuh / Soft Pastel tones)
    const val BOOKMARK_YELLOW = "#FFF59D" // Soft Butter / Pastel Yellow (Dal deuh)
    const val BOOKMARK_GREEN = "#C8E6C9"  // Soft Mint Green (Dal deuh)
    const val BOOKMARK_BLUE = "#BBDEFB"   // Soft Sky Blue (Dal deuh)
    const val BOOKMARK_RED = "#FFCDD2"    // Soft Rose / Pastel Pink (Dal deuh)
    const val BOOKMARK_PURPLE = "#E1BEE7" // Soft Lavender / Violet (Dal deuh)

    fun getSoftBookmarkColor(rawColor: String?, effectiveBgColor: Int): Int {
        val parsedColor = try {
            if (!rawColor.isNullOrBlank()) {
                val c = rawColor.trim().lowercase()
                when {
                    c.contains("red") || c.contains("pink") || c == "#ff5252" || c == "#ff0000" || c == "#e53935" || c == "#ff8a80" || c == "#ef9a9a" || c == "#ffcdd2" -> Color.parseColor(BOOKMARK_RED)
                    c.contains("blue") || c.contains("cyan") || c == "#448aff" || c == "#2196f3" || c == "#1e88e5" || c == "#80d8ff" || c == "#90caf9" || c == "#bbdefb" -> Color.parseColor(BOOKMARK_BLUE)
                    c.contains("green") || c.contains("mint") || c == "#4caf50" || c == "#43a047" || c == "#00e676" || c == "#b9f6ca" || c == "#a5d6a7" || c == "#c8e6c9" -> Color.parseColor(BOOKMARK_GREEN)
                    c.contains("yellow") || c.contains("gold") || c.contains("amber") || c == "#ffd740" || c == "#ffc107" || c == "#ffeb3b" || c == "#ffe082" || c == "#fff59d" || c == "#fff9c4" -> Color.parseColor(BOOKMARK_YELLOW)
                    c.contains("purple") || c.contains("violet") || c == "#9c27b0" || c == "#8e24aa" || c == "#ab47bc" || c == "#ce93d8" || c == "#e1bee7" -> Color.parseColor(BOOKMARK_PURPLE)
                    c == "#e0e0e0" || c == "#eeeeee" || c.contains("gray") || c.contains("grey") -> Color.parseColor("#E0E0E0")
                    else -> Color.parseColor(rawColor)
                }
            } else Color.parseColor(BOOKMARK_YELLOW)
        } catch (_: Exception) {
            Color.parseColor(BOOKMARK_YELLOW)
        }

        val isDark = isColorDark(effectiveBgColor)
        val blendFactor = if (isDark) 0.16f else 0.20f
        return ColorUtils.blendARGB(effectiveBgColor, parsedColor, blendFactor)
    }

    data class AppColorOption(
        val id: String,
        val name: String,
        val colorHex: String,
        val themeResId: Int
    ) {
        val colorInt: Int get() = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#1976D2") }
    }

    data class FontColorOption(
        val id: String,
        val name: String,
        val hex: String,
        val displayColor: Int
    )

    enum class ThemePreset(
        val id: String,
        val displayName: String,
        val subtitle: String,
        val modeNight: Int,
        val appColorId: String,
        val toolbarHex: String,
        val cardHex: String,
        val primaryHex: String,
        val sampleBgHex: String,
        val sampleAccentHex: String
    ) {
        SYSTEM(
            id = "system",
            displayName = "System",
            subtitle = "Device mil zelin",
            modeNight = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            appColorId = "blue",
            toolbarHex = "",
            cardHex = "",
            primaryHex = "#1976D2",
            sampleBgHex = "#E0E0E0",
            sampleAccentHex = "#1976D2"
        ),
        LIGHT(
            id = "light",
            displayName = "Light",
            subtitle = "Earthy Paper",
            modeNight = AppCompatDelegate.MODE_NIGHT_NO,
            appColorId = "blue",
            toolbarHex = "#FAF7F0",
            cardHex = "#EBE2CF",
            primaryHex = "#1976D2",
            sampleBgHex = "#FAF7F0",
            sampleAccentHex = "#1976D2"
        ),
        NIGHT(
            id = "night",
            displayName = "Night",
            subtitle = "Thim / Zan",
            modeNight = AppCompatDelegate.MODE_NIGHT_YES,
            appColorId = "blue",
            toolbarHex = "#222333",
            cardHex = "#2E2F45",
            primaryHex = "#90CAF9",
            sampleBgHex = "#222333",
            sampleAccentHex = "#90CAF9"
        ),
        RED(
            id = "red",
            displayName = "Red",
            subtitle = "A Sen",
            modeNight = AppCompatDelegate.MODE_NIGHT_NO,
            appColorId = "red",
            toolbarHex = "#FFF0F2",
            cardHex = "#FCE4EC",
            primaryHex = "#C2185B",
            sampleBgHex = "#FFF0F2",
            sampleAccentHex = "#C2185B"
        ),
        BLUE(
            id = "blue",
            displayName = "Blue",
            subtitle = "A Pawl",
            modeNight = AppCompatDelegate.MODE_NIGHT_NO,
            appColorId = "blue",
            toolbarHex = "#EBF3FA",
            cardHex = "#DCEBF7",
            primaryHex = "#1976D2",
            sampleBgHex = "#EBF3FA",
            sampleAccentHex = "#1976D2"
        ),
        GREEN(
            id = "green",
            displayName = "Green",
            subtitle = "A Hring",
            modeNight = AppCompatDelegate.MODE_NIGHT_NO,
            appColorId = "green",
            toolbarHex = "#EDF7EE",
            cardHex = "#DCEDDD",
            primaryHex = "#2E7D32",
            sampleBgHex = "#EDF7EE",
            sampleAccentHex = "#2E7D32"
        ),
        YELLOW(
            id = "yellow",
            displayName = "Yellow",
            subtitle = "A Eng",
            modeNight = AppCompatDelegate.MODE_NIGHT_NO,
            appColorId = "yellow",
            toolbarHex = "#FEF9E7",
            cardHex = "#FBF0CB",
            primaryHex = "#D84315",
            sampleBgHex = "#FEF9E7",
            sampleAccentHex = "#D84315"
        ),
        WHITE(
            id = "white",
            displayName = "White",
            subtitle = "A Var",
            modeNight = AppCompatDelegate.MODE_NIGHT_NO,
            appColorId = "white",
            toolbarHex = "#FFFFFF",
            cardHex = "#F3F4F6",
            primaryHex = "#1976D2",
            sampleBgHex = "#FFFFFF",
            sampleAccentHex = "#1976D2"
        );

        companion object {
            fun fromId(id: String?): ThemePreset {
                return entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
            }
        }
    }

    enum class ThemeMode(val title: String, val modeNight: Int, val styleKey: String) {
        SYSTEM("System Default", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, "default"),
        LIGHT("Light Mode", AppCompatDelegate.MODE_NIGHT_NO, "default"),
        DARK("Night / Dark", AppCompatDelegate.MODE_NIGHT_YES, "default"),
        SEPIA("Sepia / Paper", AppCompatDelegate.MODE_NIGHT_NO, "sepia"),
        AMOLED("AMOLED Black", AppCompatDelegate.MODE_NIGHT_YES, "amoled"),
        CUSTOM("Custom Color", AppCompatDelegate.MODE_NIGHT_NO, "custom")
    }

    val APP_COLORS = listOf(
        AppColorOption("blue", "Classic Blue", "#1976D2", R.style.Theme_MizoGoBible_NoActionBar_Blue),
        AppColorOption("green", "Forest Green", "#2E7D32", R.style.Theme_MizoGoBible_NoActionBar_Green),
        AppColorOption("red", "Crimson Red", "#C2185B", R.style.Theme_MizoGoBible_NoActionBar_Red),
        AppColorOption("yellow", "Warm Gold", "#D84315", R.style.Theme_MizoGoBible_NoActionBar_Yellow),
        AppColorOption("white", "Pure White", "#1976D2", R.style.Theme_MizoGoBible_NoActionBar_White),
        AppColorOption("wine", "Burgundy Wine", "#880E4F", R.style.Theme_MizoGoBible_NoActionBar_Wine),
        AppColorOption("purple", "Royal Purple", "#6A1B9A", R.style.Theme_MizoGoBible_NoActionBar_Purple),
        AppColorOption("amber", "Warm Amber", "#D84315", R.style.Theme_MizoGoBible_NoActionBar_Amber),
        AppColorOption("teal", "Deep Teal", "#00796B", R.style.Theme_MizoGoBible_NoActionBar_Teal),
        AppColorOption("navy", "Midnight Navy", "#1A237E", R.style.Theme_MizoGoBible_NoActionBar_Navy),
        AppColorOption("brown", "Coffee Brown", "#5D4037", R.style.Theme_MizoGoBible_NoActionBar_Brown),
        AppColorOption("charcoal", "Slate Charcoal", "#37474F", R.style.Theme_MizoGoBible_NoActionBar_Charcoal),
        AppColorOption("rose", "Sunset Rose", "#C2185B", R.style.Theme_MizoGoBible_NoActionBar_Rose)
    )

    val FONT_COLORS = listOf(
        FontColorOption("default", "Auto (Default)", "", Color.parseColor("#424242")),
        FontColorOption("black", "Pitch Black", "#000000", Color.parseColor("#000000")),
        FontColorOption("charcoal", "Soft Charcoal", "#262626", Color.parseColor("#262626")),
        FontColorOption("sepia", "Sepia Brown", "#3E2723", Color.parseColor("#3E2723")),
        FontColorOption("navy", "Deep Navy", "#0D1B2A", Color.parseColor("#0D1B2A")),
        FontColorOption("forest", "Forest Slate", "#1B3828", Color.parseColor("#1B3828")),
        FontColorOption("white", "Pure White", "#FFFFFF", Color.parseColor("#FFFFFF")),
        FontColorOption("cream", "Warm Cream", "#FFF8E7", Color.parseColor("#FFF8E7")),
        FontColorOption("silver", "Soft Silver", "#D3D4F2", Color.parseColor("#D3D4F2")),
        FontColorOption("gold", "Golden Amber", "#FFD54F", Color.parseColor("#FFD54F"))
    )

    fun getSelectedThemePreset(context: Context): ThemePreset {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val presetId = prefs.getString(KEY_THEME_PRESET, null)
        if (presetId != null) {
            return ThemePreset.fromId(presetId)
        }
        val appColor = prefs.getString(KEY_APP_COLOR, "blue") ?: "blue"
        val nightMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val toolbar = prefs.getString(KEY_TOOLBAR_COLOR, "") ?: ""
        return when {
            appColor.equals("red", ignoreCase = true) || appColor.equals("wine", ignoreCase = true) || appColor.equals("rose", ignoreCase = true) -> ThemePreset.RED
            appColor.equals("green", ignoreCase = true) -> ThemePreset.GREEN
            appColor.equals("yellow", ignoreCase = true) || appColor.equals("amber", ignoreCase = true) -> ThemePreset.YELLOW
            toolbar == "#FFFFFF" -> ThemePreset.WHITE
            nightMode == AppCompatDelegate.MODE_NIGHT_YES -> ThemePreset.NIGHT
            nightMode == AppCompatDelegate.MODE_NIGHT_NO -> ThemePreset.LIGHT
            else -> ThemePreset.SYSTEM
        }
    }

    fun applyThemePreset(context: Context, preset: ThemePreset) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_PRESET, preset.id)
            .putInt(KEY_THEME_MODE, preset.modeNight)
            .putString(KEY_THEME_STYLE, "preset")
            .putString(KEY_APP_COLOR, preset.appColorId)
            .putString(KEY_TOOLBAR_COLOR, preset.toolbarHex)
            .putString(KEY_CUSTOM_THEME_COLOR, if (preset.toolbarHex.isNotEmpty()) preset.toolbarHex else "#FAF7F0")
            .putString(KEY_CARD_COLOR, preset.cardHex)
            .apply()

        AppCompatDelegate.setDefaultNightMode(preset.modeNight)
    }

    fun isColorDark(colorInt: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(colorInt) + 0.587 * Color.green(colorInt) + 0.114 * Color.blue(colorInt)) / 255
        return darkness >= 0.5
    }

    fun getContrastingTextColor(bgColor: Int, preferredColor: Int? = null): Int {
        val isDark = isColorDark(bgColor)
        val fallbackColor = if (isDark) Color.parseColor("#F5F5F7") else Color.parseColor("#121212")
        if (preferredColor == null) return fallbackColor
        val ratio = ColorUtils.calculateContrast(preferredColor, bgColor)
        return if (ratio >= 4.0) preferredColor else fallbackColor
    }

    fun getContrastingVerseNumberColor(bgColor: Int, primaryColor: Int): Int {
        val isDark = isColorDark(bgColor)
        val ratio = ColorUtils.calculateContrast(primaryColor, bgColor)
        if (ratio >= 3.8) {
            return primaryColor
        }
        return if (isDark) {
            val lightened = adjustLightness(primaryColor, 0.55f)
            if (ColorUtils.calculateContrast(lightened, bgColor) >= 3.5) lightened
            else Color.parseColor("#90CAF9")
        } else {
            val darkened = adjustLightness(primaryColor, -0.45f)
            if (ColorUtils.calculateContrast(darkened, bgColor) >= 3.5) darkened
            else Color.parseColor("#0D47A1")
        }
    }

    fun getCustomThemeColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_THEME_COLOR, "#FAF7F0") ?: "#FAF7F0"
    }

    fun setCustomThemeColor(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_THEME_COLOR, hex).apply()
    }

    fun applyTheme(activity: Activity) {
        val preset = getSelectedThemePreset(activity)
        AppCompatDelegate.setDefaultNightMode(preset.modeNight)
        val themeRes = getThemeResourceId(activity)
        activity.setTheme(themeRes)
    }

    fun getThemeResourceId(context: Context): Int {
        val preset = getSelectedThemePreset(context)
        return when (preset) {
            ThemePreset.RED -> R.style.Theme_MizoGoBible_NoActionBar_Red
            ThemePreset.GREEN -> R.style.Theme_MizoGoBible_NoActionBar_Green
            ThemePreset.YELLOW -> R.style.Theme_MizoGoBible_NoActionBar_Yellow
            ThemePreset.WHITE -> R.style.Theme_MizoGoBible_NoActionBar_White
            ThemePreset.BLUE, ThemePreset.LIGHT, ThemePreset.NIGHT, ThemePreset.SYSTEM -> R.style.Theme_MizoGoBible_NoActionBar_Blue
        }
    }

    fun getSelectedAppColor(context: Context): String {
        return getSelectedThemePreset(context).appColorId
    }

    fun getPrimaryColor(context: Context): Int {
        val preset = getSelectedThemePreset(context)
        if (preset == ThemePreset.SYSTEM) {
            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            return if (isNight) Color.parseColor("#90CAF9") else Color.parseColor("#1976D2")
        }
        return try {
            Color.parseColor(preset.primaryHex)
        } catch (_: Exception) {
            Color.parseColor("#1976D2")
        }
    }

    fun setSelectedAppColor(context: Context, colorId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_COLOR, colorId).apply()
    }

    fun getCurrentThemeMode(context: Context): ThemeMode {
        val preset = getSelectedThemePreset(context)
        return when (preset) {
            ThemePreset.NIGHT -> ThemeMode.DARK
            ThemePreset.LIGHT -> ThemeMode.LIGHT
            ThemePreset.SYSTEM -> ThemeMode.SYSTEM
            else -> ThemeMode.LIGHT
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode, customHex: String? = null) {
        val preset = when (mode) {
            ThemeMode.DARK, ThemeMode.AMOLED -> ThemePreset.NIGHT
            ThemeMode.LIGHT, ThemeMode.SEPIA -> ThemePreset.LIGHT
            ThemeMode.SYSTEM -> ThemePreset.SYSTEM
            ThemeMode.CUSTOM -> ThemePreset.BLUE
        }
        applyThemePreset(context, preset)
    }

    fun getCustomToolbarColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOOLBAR_COLOR, "") ?: ""
    }

    fun setCustomToolbarColor(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOOLBAR_COLOR, hex).apply()
    }

    fun getCustomCardColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CARD_COLOR, "") ?: ""
    }

    fun setCustomCardColor(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CARD_COLOR, hex).apply()
    }

    fun adjustLightness(colorInt: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        if (factor > 0) {
            hsv[2] = (hsv[2] + (1f - hsv[2]) * factor).coerceIn(0f, 1f)
        } else {
            hsv[2] = (hsv[2] * (1f + factor)).coerceIn(0f, 1f)
        }
        return Color.HSVToColor(hsv)
    }

    fun getEffectiveToolbarColor(context: Context): Int {
        val preset = getSelectedThemePreset(context)
        return when (preset) {
            ThemePreset.SYSTEM -> {
                val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                if (isNight) Color.parseColor("#222333") else Color.parseColor("#FAF7F0")
            }
            ThemePreset.LIGHT -> Color.parseColor("#FAF7F0")
            ThemePreset.NIGHT -> Color.parseColor("#222333")
            ThemePreset.RED -> Color.parseColor("#FFF0F2")
            ThemePreset.BLUE -> Color.parseColor("#EBF3FA")
            ThemePreset.GREEN -> Color.parseColor("#EDF7EE")
            ThemePreset.YELLOW -> Color.parseColor("#FEF9E7")
            ThemePreset.WHITE -> Color.parseColor("#FFFFFF")
        }
    }

    fun getEffectiveCardColor(context: Context): Int? {
        val preset = getSelectedThemePreset(context)
        return when (preset) {
            ThemePreset.SYSTEM -> {
                val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                if (isNight) Color.parseColor("#2E2F45") else Color.parseColor("#EBE2CF")
            }
            ThemePreset.LIGHT -> Color.parseColor("#EBE2CF")
            ThemePreset.NIGHT -> Color.parseColor("#2E2F45")
            ThemePreset.RED -> Color.parseColor("#FCE4EC")
            ThemePreset.BLUE -> Color.parseColor("#DCEBF7")
            ThemePreset.GREEN -> Color.parseColor("#DCEDDD")
            ThemePreset.YELLOW -> Color.parseColor("#FBF0CB")
            ThemePreset.WHITE -> Color.parseColor("#F3F4F6")
        }
    }

    fun getCustomIconColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ICON_COLOR, "") ?: ""
    }

    fun setCustomIconColor(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ICON_COLOR, hex).apply()
    }

    fun getEffectiveFontColor(context: Context): Int? {
        val hex = getFontColor(context)
        if (hex.isNotBlank() && hex != "default") {
            try {
                return Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
            } catch (_: Exception) {}
        }
        return null
    }

    fun getEffectiveIconColor(context: Context): Int? {
        val customIcon = getCustomIconColor(context)
        if (customIcon.isNotBlank() && customIcon != "default") {
            try {
                return Color.parseColor(if (customIcon.startsWith("#")) customIcon else "#$customIcon")
            } catch (_: Exception) {}
        }
        return getEffectiveFontColor(context)
    }

    fun getEffectiveBackgroundColor(context: Context): Int {
        return getEffectiveToolbarColor(context)
    }

    fun applyThemeToView(context: Context, view: View) {
        if (view.id == R.id.dialog_theme_settings_root) return

        val toolbarColor = getEffectiveToolbarColor(context)
        val bgColor = toolbarColor
        val cardColor = getEffectiveCardColor(context)
        val fontColor = getEffectiveFontColor(context)
        val iconColor = getEffectiveIconColor(context)

        view.setBackgroundColor(bgColor)
        view.findViewById<AppBarLayout>(R.id.app_bar)?.let {
            it.setBackgroundColor(toolbarColor)
            it.backgroundTintList = ColorStateList.valueOf(toolbarColor)
        }
        view.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.let {
            it.setBackgroundColor(toolbarColor)
            it.backgroundTintList = ColorStateList.valueOf(toolbarColor)
            it.setContentScrimColor(toolbarColor)
        }
        view.findViewById<View>(R.id.view_chapter_header_background)?.setBackgroundColor(toolbarColor)
        view.findViewById<View>(R.id.recyclerview_bible)?.setBackgroundColor(toolbarColor)
        view.findViewById<View>(R.id.recyclerview_bible_split)?.setBackgroundColor(toolbarColor)
        view.findViewById<TextView>(R.id.text_chapter_title)?.let {
            it.setTextColor(getContrastingTextColor(toolbarColor, fontColor))
        }

        applyColorsRecursively(view, cardColor, fontColor, iconColor)
    }

    fun applyColorsRecursively(
        view: View,
        cardColor: Int?,
        fontColor: Int?,
        iconColor: Int?,
        insideCardDark: Boolean? = null
    ) {
        if (view.id == R.id.dialog_theme_settings_root) return

        var currentInsideCardDark = insideCardDark

        if (view is MaterialCardView) {
            if (cardColor != null) {
                view.setCardBackgroundColor(cardColor)
                val isDark = isColorDark(cardColor)
                view.strokeColor = if (isDark) Color.parseColor("#20FFFFFF") else Color.parseColor("#15000000")
                currentInsideCardDark = isDark
            }
        } else if (view is CardView) {
            if (cardColor != null) {
                view.setCardBackgroundColor(cardColor)
                currentInsideCardDark = isColorDark(cardColor)
            }
        }

        val targetBg = if (currentInsideCardDark != null && cardColor != null) {
            cardColor
        } else {
            getEffectiveBackgroundColor(view.context)
        }
        val effectiveText = getContrastingTextColor(targetBg, fontColor)
        val effectiveIcon = iconColor?.let { getContrastingTextColor(targetBg, it) } ?: effectiveText

        applySingleViewStyling(view, effectiveText, effectiveIcon)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyColorsRecursively(view.getChildAt(i), cardColor, fontColor, iconColor, currentInsideCardDark)
            }
        }
    }

    fun applySingleViewStyling(view: View, fontColor: Int?, iconColor: Int?) {
        val viewId = view.id
        if (viewId == R.id.badge_new_layout ||
            viewId == R.id.view_update_badge ||
            viewId == R.id.icon_checkmark ||
            viewId == R.id.view_color_circle ||
            viewId == R.id.view_selection_ring ||
            viewId == R.id.text_preview_reference ||
            viewId == R.id.btn_filter_all ||
            viewId == R.id.btn_filter_ot ||
            viewId == R.id.btn_filter_nt ||
            viewId == R.id.img_header_bg) {
            return
        }

        if (viewId == R.id.img_profile ||
            viewId == R.id.img_gold_badge ||
            viewId == R.id.img_silver_badge) {
            if (view is ImageView) {
                view.colorFilter = null
                ImageViewCompat.setImageTintList(view, null)
            }
            return
        }

        if (view is MaterialButton) {
            if (fontColor != null) {
                view.setTextColor(fontColor)
            }
            if (iconColor != null) {
                view.iconTint = ColorStateList.valueOf(iconColor)
            }
        } else if (view is MaterialSwitch) {
            if (fontColor != null) {
                view.setTextColor(fontColor)
            }
            if (iconColor != null) {
                view.thumbIconTintList = ColorStateList.valueOf(iconColor)
            }
        } else if (view is CompoundButton) {
            if (fontColor != null) {
                view.setTextColor(fontColor)
            }
            if (iconColor != null) {
                CompoundButtonCompat.setButtonTintList(view, ColorStateList.valueOf(iconColor))
            }
        } else if (view is TextView) {
            if (fontColor != null) {
                view.setTextColor(fontColor)
            }
            if (iconColor != null) {
                TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(iconColor))
            }
            if (view is android.widget.EditText) {
                if (fontColor != null) {
                    view.setHintTextColor(ColorUtils.setAlphaComponent(fontColor, 140))
                }
            }
        } else if (view is ImageView) {
            if (iconColor != null) {
                if (viewId != R.id.img_header_bg &&
                    viewId != R.id.img_profile &&
                    viewId != R.id.img_gold_badge &&
                    viewId != R.id.img_silver_badge) {
                    ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(iconColor))
                    view.setColorFilter(iconColor)
                }
            }
        } else if (view is androidx.appcompat.widget.Toolbar) {
            if (fontColor != null) {
                view.setTitleTextColor(fontColor)
                view.setSubtitleTextColor(ColorUtils.setAlphaComponent(fontColor, 180))
            }
            if (iconColor != null) {
                view.navigationIcon?.setTint(iconColor)
                view.overflowIcon?.setTint(iconColor)
                view.collapseIcon?.setTint(iconColor)
            }
        }
    }

    fun applyCardColorsRecursively(view: View, cardColor: Int) {
        applyColorsRecursively(
            view,
            cardColor,
            getEffectiveFontColor(view.context),
            getEffectiveIconColor(view.context)
        )
    }

    fun applyCustomBackgroundToView(context: Context, view: View) {
        applyThemeToView(context, view)
    }

    fun getFontColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FONT_COLOR, "") ?: ""
    }

    fun setFontColor(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FONT_COLOR, hex)
            .putString(KEY_ICON_COLOR, hex)
            .apply()
    }
}
