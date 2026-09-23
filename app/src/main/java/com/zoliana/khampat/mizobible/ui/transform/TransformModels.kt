package com.zoliana.khampat.mizobible.ui.transform

data class FontSettings(
    val fontSize: Float = 18f,
    val fontFamily: String = "Times New Roman",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.0f
)

data class NavHistoryItem(
    val book: String,
    val chapter: Int
)
