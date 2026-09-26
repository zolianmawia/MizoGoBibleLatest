package com.zoliana.khampat.mizobible.ui.transform

data class FontSettings(
    val fontSize: Float = 18f,
    val fontFamily: String = "Serif",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.3f,
    val fontColor: String = ""
)

data class NavHistoryItem(
    val book: String,
    val chapter: Int
)
