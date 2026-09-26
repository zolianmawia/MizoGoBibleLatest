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
    val title: String = "",
    val note: String = "",
    val color: String = "#FFF59D", // Default soft pastel yellow (dal deuh)
    val timestamp: Long = System.currentTimeMillis()
)
