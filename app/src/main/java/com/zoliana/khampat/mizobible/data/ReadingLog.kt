package com.zoliana.khampat.mizobible.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_logs")
data class ReadingLog(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val versesRead: Int = 0,
    val secondsRead: Long = 0
)

@Entity(tableName = "hourly_reading_logs", primaryKeys = ["date", "hour"])
data class HourlyReadingLog(
    val date: String, // Format: YYYY-MM-DD
    val hour: Int,    // 0-23
    val secondsRead: Long = 0
)