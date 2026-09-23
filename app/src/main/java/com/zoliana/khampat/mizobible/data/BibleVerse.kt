package com.zoliana.khampat.mizobible.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mizogobible",
    indices = [
        Index(value = ["type", "book", "chapter", "verse"]),
        Index(value = ["book"]),
        Index(value = ["chapter"]),
        Index(value = ["normalized_text"]),
        Index(value = ["searchText"])
    ]
)
data class BibleVerse(
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null,
    var type: String?,
    var book: String?,
    var chapter: Int?,
    var verse: String?,
    var text: String?,
    @ColumnInfo(name = "normalized_text")
    var normalizedText: String?,
    var searchText: String? = null
)
