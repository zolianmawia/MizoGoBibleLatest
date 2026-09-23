package com.zoliana.khampat.mizobible.data

import androidx.room.Entity

@Entity(tableName = "pins", primaryKeys = ["verseId", "version"])
data class Pin(
    val verseId: Int = 0,
    val book: String = "",
    val chapter: Int = 0,
    val verse: String = "",
    val text: String = "",
    val color: String = "#FFD740",
    val version: String = "MzOV",
    val timestamp: Long = System.currentTimeMillis()
)
