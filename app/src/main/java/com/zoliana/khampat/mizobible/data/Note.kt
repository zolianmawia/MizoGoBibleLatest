package com.zoliana.khampat.mizobible.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val verseId: Int,
    val book: String,
    val chapter: Int,
    val verse: String,
    val text: String,        // Hehi user note ziah kha a ni
    val bibleText: String = "", // Hehi bible verse content kha a ni
    val color: String,
    val version: String = "MGB",
    val timestamp: Long = System.currentTimeMillis()
)
