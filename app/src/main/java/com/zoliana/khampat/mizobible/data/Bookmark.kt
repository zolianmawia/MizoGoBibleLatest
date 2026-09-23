package com.zoliana.khampat.mizobible.data

import androidx.room.Entity

@Entity(tableName = "bookmarks", primaryKeys = ["verseId", "version"])
data class Bookmark(
    val verseId: Int = 0,
    val book: String = "",
    val chapter: Int = 0,
    val verse: String = "",
    val text: String = "",
    val version: String = "MzOV",
    val note: String = "",
    val color: String = "#FFD740", // Default gold/yellow
    val timestamp: Long = System.currentTimeMillis()
)
