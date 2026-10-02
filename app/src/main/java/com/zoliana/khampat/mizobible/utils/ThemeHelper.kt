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
import com.google.android.material.textfield.TextInputLayout
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.databinding.DialogBookmarkBinding
import com.zoliana.khampat.mizobible.databinding.DialogBookmarkActionsBinding

object ThemeHelper {

    private const val PREFS_NAME = "bible_prefs"
    const val KEY_THEME_PRESET = "app_theme_preset"
    const val KEY_APP_COLOR = "app_color_theme"
    const val KEY_THEME_STYLE = "app_theme_style"
    const val KEY_FONT_COLOR = "font_color"
    const val KEY_FONT_COLOR_LIGHT = "font_color_light"
    const val KEY_FONT_COLOR_DARK = "font_color_dark"
    const val KEY_ICON_COLOR = "custom_icon_color"
    const val KEY_ICON_COLOR_LIGHT = "icon_color_light"
    const val KEY_ICON_COLOR_DARK = "icon_color_dark"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_CUSTOM_THEME_COLOR = "custom_theme_mode_color"
    const val KEY_TOOLBAR_COLOR = "custom_toolbar_color"
    const val KEY_CARD_COLOR = "custom_card_color"
    const val KEY_THEME_OPACITY = "theme_opacity_level"

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
                    c == "#e0e0e0" || c == "#eeeeee" || c.contains("gray") || c.contains("grey") -> Color.parseColor(BOOKMARK_YELLOW)
                    else -> Color.parseColor(rawColor)
                }
            } else Color.parseColor(BOOKMARK_YELLOW)
        } catch (_: Exception) {
            Color.parseColor(BOOKMARK_YELLOW)
        }

        val isDark = isColorDark(effectiveBgColor)
        // High visibility blend: in dark mode, 0.38f ensures a rich luminous tint; in light/colored mode, 0.52f ensures vibrant color distinction
        val blendFactor = if (isDark) 0.38f else 0.52f
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

    // Dark Mode check: True if current theme/preset or system night mode is Dark
    fun isDarkMode(context: Context): Boolean {
        val preset = getSelectedThemePreset(context)
        return when (preset) {
            ThemePreset.NIGHT -> true
            ThemePreset.LIGHT, ThemePreset.RED, ThemePreset.BLUE, ThemePreset.GREEN, ThemePreset.YELLOW, ThemePreset.WHITE -> false
            ThemePreset.SYSTEM -> {
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    // Effective background check: True if current toolbar/verse background is dark
    fun isCurrentThemeDark(context: Context): Boolean {
        if (isDarkMode(context)) return true
        if (getThemeOpacity(context) >= 0.5f) return true
        val bg = getEffectiveToolbarColor(context)
        return isColorDark(bg)
    }

    // High-contrast, dark, clear fonts for Light Mode (Earthy, White, Red, Blue, Green, Yellow themes)
    // ONLY clearly visible dark tones: No white, light cream, pale gold or low-contrast washed-out tones
    val LIGHT_MODE_FONT_COLORS = listOf(
        FontColorOption("default", "Auto (Default)", "", Color.parseColor("#201A1B")),
        FontColorOption("black", "Pitch Black", "#000000", Color.parseColor("#000000")),
        FontColorOption("white", "Pure White (Var)", "#FFFFFF", Color.parseColor("#FFFFFF")),
        FontColorOption("charcoal", "Soft Charcoal", "#262626", Color.parseColor("#262626")),
        FontColorOption("sepia", "Sepia Brown", "#3E2723", Color.parseColor("#3E2723")),
        FontColorOption("navy", "Deep Navy", "#0D1B2A", Color.parseColor("#0D1B2A")),
        FontColorOption("forest", "Forest Slate", "#1B3828", Color.parseColor("#1B3828")),
        FontColorOption("wine", "Crimson Wine", "#4A1521", Color.parseColor("#4A1521")),
        FontColorOption("plum", "Deep Plum", "#3E1B4A", Color.parseColor("#3E1B4A")),
        FontColorOption("teal", "Dark Teal", "#00363A", Color.parseColor("#00363A"))
    )

    // High-contrast, white-toned, light glowing fonts for Dark Mode (Night / AMOLED themes)
    // ONLY usable light/white tones: No black, charcoal, sepia, deep navy, or dark tones
    val DARK_MODE_FONT_COLORS = listOf(
        FontColorOption("default", "Auto (Default)", "", Color.parseColor("#FFFFFF")),
        FontColorOption("white", "Pure White (Var)", "#FFFFFF", Color.parseColor("#FFFFFF")),
        FontColorOption("cream", "Warm Cream", "#FFF8E7", Color.parseColor("#FFF8E7")),
        FontColorOption("silver", "Soft Silver", "#D3D4F2", Color.parseColor("#D3D4F2")),
        FontColorOption("gold", "Golden Amber", "#FFE082", Color.parseColor("#FFE082")),
        FontColorOption("ice_blue", "Ice Blue", "#B3E5FC", Color.parseColor("#B3E5FC")),
        FontColorOption("mint", "Mint Glow", "#C8E6C9", Color.parseColor("#C8E6C9")),
        FontColorOption("rose", "Rose Glow", "#F8BBD0", Color.parseColor("#F8BBD0"))
    )

    val FONT_COLORS: List<FontColorOption>
        get() = LIGHT_MODE_FONT_COLORS

    // Dynamically returns font colors suitable for the active mode and theme
    fun getAvailableFontColors(context: Context): List<FontColorOption> {
        val isDark = isCurrentThemeDark(context)
        val opacity = getThemeOpacity(context)
        val bg = getEffectiveToolbarColor(context)

        // When theme is dark or color intensity is half or more (opacity >= 0.45f), show white & light font colors
        return if (isDark || opacity >= 0.45f) {
            DARK_MODE_FONT_COLORS.filter { option ->
                if (option.hex.isEmpty()) true
                else {
                    try {
                        val c = Color.parseColor(option.hex)
                        !isColorDark(c)
                    } catch (_: Exception) { true }
                }
            }
        } else {
            LIGHT_MODE_FONT_COLORS.filter { option ->
                if (option.hex.isEmpty()) true
                else {
                    try {
                        val c = Color.parseColor(option.hex)
                        isColorDark(c) && ColorUtils.calculateContrast(c, bg) >= 3.0
                    } catch (_: Exception) { true }
                }
            }
        }
    }

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
        val isPreferredDark = isColorDark(preferredColor)

        // If preferred color is light (e.g. white font) and background is dark (or dark-tinted/semi-opaque)
        if (!isPreferredDark) {
            val ratio = ColorUtils.calculateContrast(preferredColor, bgColor)
            if (isDark || ratio >= 2.0) return preferredColor
        }

        // If preferred color is dark and background is light
        if (isPreferredDark && !isDark) return preferredColor

        val ratio = ColorUtils.calculateContrast(preferredColor, bgColor)
        return if (ratio >= 2.0) preferredColor else fallbackColor
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

    fun getThemeOpacity(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_THEME_OPACITY, 0.0f).coerceIn(0.0f, 1.0f)
    }

    fun setThemeOpacity(context: Context, opacity: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_THEME_OPACITY, opacity.coerceIn(0.0f, 1.0f)).apply()
    }

    fun getPrimaryColorForPreset(
        preset: ThemePreset,
        context: Context,
        opacity: Float = getThemeOpacity(context)
    ): Int {
        val baseColor = if (preset == ThemePreset.SYSTEM) {
            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            return if (isNight) Color.parseColor("#90CAF9") else Color.parseColor("#1976D2")
        } else {
            try {
                Color.parseColor(preset.primaryHex)
            } catch (_: Exception) {
                Color.parseColor("#1976D2")
            }
        }
        val clampedOpacity = opacity.coerceIn(0f, 1f)
        if (clampedOpacity <= 0.001f) return baseColor
        val isNight = isCurrentThemeDark(context)
        return if (isNight) {
            adjustLightness(baseColor, clampedOpacity * 0.12f)
        } else {
            adjustLightness(baseColor, -clampedOpacity * 0.12f)
        }
    }

    fun getPrimaryColor(context: Context): Int {
        val preset = getSelectedThemePreset(context)
        return getPrimaryColorForPreset(preset, context)
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

    fun getEffectiveToolbarColorForPreset(preset: ThemePreset, context: Context, opacity: Float = getThemeOpacity(context)): Int {
        val isSystemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val (baseHex, richHex) = when (preset) {
            ThemePreset.SYSTEM -> if (isSystemNight) ("#222333" to "#0A0B10") else ("#FAF7F0" to "#3E2723")
            ThemePreset.LIGHT -> ("#FAF7F0" to "#3E2723")
            ThemePreset.NIGHT -> ("#222333" to "#0A0B10")
            ThemePreset.RED -> ("#FFF0F2" to "#880E4F")
            ThemePreset.BLUE -> ("#EBF3FA" to "#0D47A1")
            ThemePreset.GREEN -> ("#EDF7EE" to "#1B5E20")
            ThemePreset.YELLOW -> ("#FEF9E7" to "#BF360C")
            ThemePreset.WHITE -> ("#FFFFFF" to "#334155")
        }
        val baseColor = Color.parseColor(baseHex)
        val clampedOpacity = opacity.coerceIn(0f, 1f)
        if (clampedOpacity <= 0.001f) return baseColor
        val richColor = Color.parseColor(richHex)
        return ColorUtils.blendARGB(baseColor, richColor, clampedOpacity)
    }

    fun getEffectiveCardColorForPreset(preset: ThemePreset, context: Context, opacity: Float = getThemeOpacity(context)): Int {
        val isSystemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val (baseHex, richHex) = when (preset) {
            ThemePreset.SYSTEM -> if (isSystemNight) ("#2E2F45" to "#12131A") else ("#EBE2CF" to "#4E342E")
            ThemePreset.LIGHT -> ("#EBE2CF" to "#4E342E")
            ThemePreset.NIGHT -> ("#2E2F45" to "#12131A")
            ThemePreset.RED -> ("#FCE4EC" to "#AD1457")
            ThemePreset.BLUE -> ("#DCEBF7" to "#1565C0")
            ThemePreset.GREEN -> ("#DCEDDD" to "#2E7D32")
            ThemePreset.YELLOW -> ("#FBF0CB" to "#D84315")
            ThemePreset.WHITE -> ("#F3F4F6" to "#475569")
        }
        val baseColor = Color.parseColor(baseHex)
        val clampedOpacity = opacity.coerceIn(0f, 1f)
        if (clampedOpacity <= 0.001f) return baseColor
        val richColor = Color.parseColor(richHex)
        return ColorUtils.blendARGB(baseColor, richColor, clampedOpacity)
    }

    fun getEffectiveToolbarColor(context: Context): Int {
        val preset = getSelectedThemePreset(context)
        return getEffectiveToolbarColorForPreset(preset, context)
    }

    fun getEffectiveCardColor(context: Context): Int? {
        val preset = getSelectedThemePreset(context)
        return getEffectiveCardColorForPreset(preset, context)
    }

    fun getCustomIconColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDark = isCurrentThemeDark(context)
        val key = if (isDark) KEY_ICON_COLOR_DARK else KEY_ICON_COLOR_LIGHT
        return prefs.getString(key, null) ?: prefs.getString(KEY_ICON_COLOR, "") ?: ""
    }

    fun setCustomIconColor(context: Context, hex: String) {
        setFontColor(context, hex)
    }

    fun getEffectiveFontColor(context: Context): Int? {
        val hex = getFontColor(context)
        if (hex.isNotBlank() && hex != "default") {
            try {
                val color = Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
                val isDark = isCurrentThemeDark(context)
                val opacity = getThemeOpacity(context)
                val bg = getEffectiveToolbarColor(context)
                val contrast = ColorUtils.calculateContrast(color, bg)

                // When user explicitly sets white or light font and opacity is half or more (>= 0.45f) or theme is dark or contrast is sufficient:
                if (!isColorDark(color) && (isDark || opacity >= 0.45f || contrast >= 2.0)) {
                    return color
                }

                // In Dark mode: dark colors are invalid if contrast is too poor
                if (isDark && (isColorDark(color) && contrast < 2.0)) {
                    return null
                }
                // In purely Light mode with low opacity: light/faint colors are invalid
                if (!isDark && opacity < 0.45f && (!isColorDark(color) && contrast < 2.0)) {
                    return null
                }
                return color
            } catch (_: Exception) {}
        }
        return null
    }

    fun getEffectiveIconColor(context: Context): Int? {
        val customIcon = getCustomIconColor(context)
        if (customIcon.isNotBlank() && customIcon != "default") {
            try {
                val color = Color.parseColor(if (customIcon.startsWith("#")) customIcon else "#$customIcon")
                val isDark = isCurrentThemeDark(context)
                val bg = getEffectiveToolbarColor(context)
                val contrast = ColorUtils.calculateContrast(color, bg)
                if (isDark && (isColorDark(color) || contrast < 3.0)) return null
                if (!isDark && (!isColorDark(color) || contrast < 3.0)) return null
                return color
            } catch (_: Exception) {}
        }
        return getEffectiveFontColor(context)
    }

    fun getEffectiveBackgroundColor(context: Context): Int {
        return getEffectiveToolbarColor(context)
    }

    fun applyThemeToView(context: Context, view: View) {
        if (view.id == R.id.dialog_theme_settings_root ||
            view.id == R.id.dialog_font_settings_root ||
            view.id == R.id.dialog_bible_versions_root ||
            view.id == R.id.dialog_about_root) return

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
        if (view.id == R.id.dialog_theme_settings_root ||
            view.id == R.id.dialog_font_settings_root ||
            view.id == R.id.dialog_bible_versions_root ||
            view.id == R.id.dialog_about_root ||
            view.id == R.id.nav_view) return

        var currentInsideCardDark = insideCardDark

        if (view is MaterialCardView) {
            val isColorSwatch = view.id in listOf(
                R.id.colorYellow, R.id.colorGreen, R.id.colorBlue, R.id.colorRed, R.id.colorPurple,
                R.id.color_yellow, R.id.color_green, R.id.color_blue, R.id.color_red, R.id.color_purple,
                R.id.card_split_pill
            )
            if (!isColorSwatch && cardColor != null) {
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
        if (viewId == R.id.view_update_badge ||
            viewId == R.id.icon_checkmark ||
            viewId == R.id.view_color_circle ||
            viewId == R.id.view_selection_ring ||
            viewId == R.id.text_preview_reference ||
            viewId == R.id.btn_filter_all ||
            viewId == R.id.btn_filter_ot ||
            viewId == R.id.btn_filter_nt ||
            viewId == R.id.btn_done ||
            viewId == R.id.btn_apply_theme ||
            viewId == R.id.img_header_bg ||
            viewId == R.id.img_profile_header ||
            viewId == R.id.layout_profile_header_container ||
            viewId == R.id.btn_profile_drawer ||
            viewId == R.id.text_profile_toolbar_title ||
            viewId == R.id.text_header_title ||
            viewId == R.id.text_header_subtitle ||
            viewId == R.id.text_parallel_line_version) {
            if ((viewId == R.id.btn_done || viewId == R.id.btn_apply_theme) && view is MaterialButton) {
                view.setTextColor(Color.WHITE)
            }
            return
        }

        val isColorSwatch = viewId in listOf(
            R.id.colorYellow, R.id.colorGreen, R.id.colorBlue, R.id.colorRed, R.id.colorPurple,
            R.id.color_yellow, R.id.color_green, R.id.color_blue, R.id.color_red, R.id.color_purple
        )
        if (isColorSwatch) return

        if (viewId == R.id.layout_split_divider ||
            viewId == R.id.card_split_pill ||
            viewId == R.id.view_split_divider_line ||
            viewId == R.id.text_split_version_divider ||
            viewId == R.id.img_split_handle ||
            viewId == R.id.btn_close_split_divider) {
            return
        }

        if (viewId == R.id.img_profile ||
            viewId == R.id.img_gold_badge ||
            viewId == R.id.img_silver_badge ||
            viewId == R.id.img_about_logo ||
            viewId == R.id.img_profile_header) {
            if (view is ImageView) {
                view.colorFilter = null
                ImageViewCompat.setImageTintList(view, null)
            }
            return
        }

        if (view is MaterialButton) {
            if (viewId == R.id.btnCancel) {
                val cancelColor = getCancelButtonTextColor(view.context)
                view.setTextColor(cancelColor)
                view.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(cancelColor, 40))
                return
            }
            if (viewId == R.id.btnSave) {
                val primary = getPrimaryColor(view.context)
                view.backgroundTintList = ColorStateList.valueOf(primary)
                view.setTextColor(getContrastingTextColor(primary))
                return
            }
            if (viewId == R.id.btn_reset_all) {
                val ctx = view.context
                val isDark = isCurrentThemeDark(ctx)
                val cardColor = getEffectiveCardColor(ctx) ?: if (isDark) Color.parseColor("#2E2F45") else Color.parseColor("#FCE4EC")
                val primaryColor = getPrimaryColor(ctx)
                val textColor = getContrastingTextColor(cardColor, fontColor)
                val effectiveIcon = iconColor ?: primaryColor
                view.backgroundTintList = ColorStateList.valueOf(cardColor)
                view.setTextColor(textColor)
                view.iconTint = ColorStateList.valueOf(effectiveIcon)
                view.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, if (isDark) 90 else 60))
                view.strokeWidth = (1.5f * ctx.resources.displayMetrics.density).toInt()
                return
            }
            if (viewId == R.id.btn_done || viewId == R.id.btn_apply_theme) {
                view.setTextColor(Color.WHITE)
                return
            }
            val bgTint = view.backgroundTintList?.defaultColor
            if (bgTint != null && bgTint != 0 && bgTint != Color.TRANSPARENT) {
                val contrastColor = getContrastingTextColor(bgTint)
                view.setTextColor(contrastColor)
                if (iconColor != null) {
                    view.iconTint = ColorStateList.valueOf(contrastColor)
                }
                return
            }
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
            if (viewId == R.id.text_split_version_divider) {
                // Special handling for split version label in divider
                view.setTextColor(iconColor ?: fontColor ?: Color.BLACK)
                return
            }
            if (fontColor != null) {
                view.setTextColor(fontColor)
            }
            if (iconColor != null) {
                TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(iconColor))
            }
            if (view is android.widget.EditText) {
                val cardBg = getEffectiveCardColor(view.context) ?: getEffectiveToolbarColor(view.context)
                val textColor = getContrastingTextColor(cardBg, fontColor)
                view.setTextColor(textColor)
                view.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 120))
            }
        } else if (view is TextInputLayout) {
            val primary = getPrimaryColor(view.context)
            val cardBg = getEffectiveCardColor(view.context) ?: getEffectiveToolbarColor(view.context)
            val isDark = isColorDark(cardBg)
            val defaultStroke = if (isDark) Color.parseColor("#40FFFFFF") else Color.parseColor("#30000000")
            val textColor = getContrastingTextColor(cardBg, fontColor)
            val hintColor = ColorUtils.setAlphaComponent(textColor, 180)

            view.setBoxStrokeColorStateList(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(primary, defaultStroke)
            ))
            view.setStartIconTintList(ColorStateList.valueOf(primary))
            view.setEndIconTintList(ColorStateList.valueOf(primary))
            view.defaultHintTextColor = ColorStateList.valueOf(hintColor)
            view.hintTextColor = ColorStateList.valueOf(primary)
        } else if (view is ImageView) {
            if (iconColor != null) {
                if (viewId != R.id.img_header_bg &&
                    viewId != R.id.img_profile &&
                    viewId != R.id.img_gold_badge &&
                    viewId != R.id.img_silver_badge &&
                    viewId != R.id.img_about_logo) {
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

    fun getFontColorForMode(context: Context, isDark: Boolean): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (isDark) {
            val darkVal = prefs.getString(KEY_FONT_COLOR_DARK, null)
            if (darkVal != null) return darkVal
            // Legacy migration: if KEY_FONT_COLOR exists and is a light color, use it
            val legacy = prefs.getString(KEY_FONT_COLOR, "") ?: ""
            if (legacy.isNotBlank() && legacy != "default") {
                try {
                    val c = Color.parseColor(if (legacy.startsWith("#")) legacy else "#$legacy")
                    if (!isColorDark(c)) return legacy
                } catch (_: Exception) {}
            }
            ""
        } else {
            val lightVal = prefs.getString(KEY_FONT_COLOR_LIGHT, null)
            if (lightVal != null) return lightVal
            // Legacy migration: if KEY_FONT_COLOR exists and is a dark color, use it
            val legacy = prefs.getString(KEY_FONT_COLOR, "") ?: ""
            if (legacy.isNotBlank() && legacy != "default") {
                try {
                    val c = Color.parseColor(if (legacy.startsWith("#")) legacy else "#$legacy")
                    if (isColorDark(c)) return legacy
                } catch (_: Exception) {}
            }
            ""
        }
    }

    fun getFontColor(context: Context): String {
        return getFontColorForMode(context, isCurrentThemeDark(context))
    }

    fun setFontColor(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDark = isCurrentThemeDark(context)
        val editor = prefs.edit()
        if (isDark) {
            editor.putString(KEY_FONT_COLOR_DARK, hex)
            editor.putString(KEY_ICON_COLOR_DARK, hex)
        } else {
            editor.putString(KEY_FONT_COLOR_LIGHT, hex)
            editor.putString(KEY_ICON_COLOR_LIGHT, hex)
        }
        editor.putString(KEY_FONT_COLOR, hex)
        editor.putString(KEY_ICON_COLOR, hex)
        editor.apply()
    }

    fun getCancelButtonTextColor(context: Context): Int {
        val cardBg = getEffectiveCardColor(context) ?: getEffectiveToolbarColor(context)
        val isDark = isColorDark(cardBg)
        return if (isDark) Color.parseColor("#CFD8DC") else Color.parseColor("#455A64")
    }

    fun getBookmarkSelectedStrokeColor(context: Context): Int {
        val cardBg = getEffectiveCardColor(context) ?: getEffectiveToolbarColor(context)
        return if (isColorDark(cardBg)) Color.WHITE else Color.BLACK
    }

    fun applyBookmarkDialogTheme(binding: DialogBookmarkBinding, context: Context) {
        val cardBg = getEffectiveCardColor(context) ?: getEffectiveToolbarColor(context)
        val isDark = isColorDark(cardBg)
        val primaryColor = getPrimaryColor(context)
        val fontColor = getEffectiveFontColor(context)
        val textColor = getContrastingTextColor(cardBg, fontColor)
        val hintColor = ColorUtils.setAlphaComponent(textColor, 170)
        val outlineColor = if (isDark) Color.parseColor("#35FFFFFF") else Color.parseColor("#25000000")
        val cancelColor = getCancelButtonTextColor(context)

        // 1. Root and Card
        binding.root.setBackgroundColor(Color.TRANSPARENT)
        binding.cardBookmarkDialog.setCardBackgroundColor(cardBg)
        binding.cardBookmarkDialog.strokeColor = outlineColor
        binding.cardBookmarkDialog.strokeWidth = (1f * context.resources.displayMetrics.density).toInt()

        // 2. Reference & Header Action
        binding.textBookmarkReference.setTextColor(primaryColor)
        binding.btnOpenAllBookmarks.setTextColor(primaryColor)
        binding.btnOpenAllBookmarks.iconTint = ColorStateList.valueOf(primaryColor)

        // 3. Inputs
        val strokeColorStates = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf()
            ),
            intArrayOf(primaryColor, outlineColor)
        )
        binding.layoutBookmarkTitle.setBoxStrokeColorStateList(strokeColorStates)
        binding.layoutBookmarkTitle.setStartIconTintList(ColorStateList.valueOf(primaryColor))
        binding.layoutBookmarkTitle.setEndIconTintList(ColorStateList.valueOf(primaryColor))
        binding.layoutBookmarkTitle.defaultHintTextColor = ColorStateList.valueOf(hintColor)
        binding.layoutBookmarkTitle.hintTextColor = ColorStateList.valueOf(primaryColor)
        binding.editBookmarkTitle.setTextColor(textColor)
        binding.editBookmarkTitle.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 120))

        binding.layoutBookmarkNote.setBoxStrokeColorStateList(strokeColorStates)
        binding.layoutBookmarkNote.defaultHintTextColor = ColorStateList.valueOf(hintColor)
        binding.layoutBookmarkNote.hintTextColor = ColorStateList.valueOf(primaryColor)
        binding.editBookmarkNote.setTextColor(textColor)
        binding.editBookmarkNote.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 120))

        // 4. Color label & Swatches
        binding.textColorLabel.setTextColor(ColorUtils.setAlphaComponent(textColor, 180))

        binding.colorYellow.setCardBackgroundColor(Color.parseColor(BOOKMARK_YELLOW))
        binding.colorGreen.setCardBackgroundColor(Color.parseColor(BOOKMARK_GREEN))
        binding.colorBlue.setCardBackgroundColor(Color.parseColor(BOOKMARK_BLUE))
        binding.colorRed.setCardBackgroundColor(Color.parseColor(BOOKMARK_RED))
        binding.colorPurple.setCardBackgroundColor(Color.parseColor(BOOKMARK_PURPLE))

        // 5. Buttons
        binding.btnCancel.setTextColor(cancelColor)
        binding.btnCancel.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(cancelColor, 40))

        binding.btnSave.backgroundTintList = ColorStateList.valueOf(primaryColor)
        binding.btnSave.setTextColor(getContrastingTextColor(primaryColor))
    }

    fun applyBookmarkActionPopupTheme(binding: DialogBookmarkActionsBinding, context: Context) {
        val cardBg = getEffectiveCardColor(context) ?: getEffectiveToolbarColor(context)
        val isDark = isColorDark(cardBg)
        val primaryColor = getPrimaryColor(context)
        val fontColor = getEffectiveFontColor(context)
        val textColor = getContrastingTextColor(cardBg, fontColor)

        binding.root.setBackgroundColor(Color.TRANSPARENT)
        binding.textActionReference.setTextColor(primaryColor)
        binding.textActionSnippet.setTextColor(ColorUtils.setAlphaComponent(textColor, 180))

        binding.colorYellow.setCardBackgroundColor(Color.parseColor(BOOKMARK_YELLOW))
        binding.colorGreen.setCardBackgroundColor(Color.parseColor(BOOKMARK_GREEN))
        binding.colorBlue.setCardBackgroundColor(Color.parseColor(BOOKMARK_BLUE))
        binding.colorRed.setCardBackgroundColor(Color.parseColor(BOOKMARK_RED))
        binding.colorPurple.setCardBackgroundColor(Color.parseColor(BOOKMARK_PURPLE))

        binding.btnEditBookmark.setTextColor(primaryColor)
        binding.btnEditBookmark.iconTint = ColorStateList.valueOf(primaryColor)
        binding.btnEditBookmark.backgroundTintList = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(primaryColor, if (isDark) 40 else 25)
        )
    }
}
