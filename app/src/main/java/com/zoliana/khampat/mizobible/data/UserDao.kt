package com.zoliana.khampat.mizobible.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // --- BOOKMARK QUERIES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksSync(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE verseId = :verseId AND version = :version LIMIT 1")
    fun getBookmark(verseId: Int, version: String): Flow<Bookmark?>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE verseId = :verseId AND version = :version)")
    fun isBookmarked(verseId: Int, version: String): Flow<Boolean>

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    @Transaction
    suspend fun standardizeBookmarkVersions() {
        val all = getAllBookmarksSync()
        val badVersions = listOf("pericope", "percope", "mizogobible", "verse", "mgb", "mizo bible", "mizov", "mizo go bible", "pericope bible", "pericope_bible")

        val mzovIds = all.filter { it.version.lowercase().trim() == "mzov" }.map { it.verseId }.toMutableSet()
        val badOnes = all.filter { it.version.lowercase().trim() in badVersions && it.version.lowercase().trim() != "mzov" }

        badOnes.forEach { bad ->
            deleteBookmark(bad)
            if (!mzovIds.contains(bad.verseId)) {
                insertBookmark(bad.copy(version = "MzOV"))
                mzovIds.add(bad.verseId)
            }
        }
    }

    // --- PIN QUERIES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPin(pin: Pin)

    @Delete
    suspend fun deletePin(pin: Pin)

    @Query("SELECT * FROM pins ORDER BY timestamp DESC")
    fun getAllPins(): Flow<List<Pin>>

    @Query("SELECT * FROM pins")
    suspend fun getAllPinsSync(): List<Pin>

    @Query("SELECT * FROM pins WHERE verseId = :verseId AND version = :version LIMIT 1")
    fun getPin(verseId: Int, version: String): Flow<Pin?>

    @Query("SELECT EXISTS(SELECT 1 FROM pins WHERE verseId = :verseId AND version = :version)")
    fun isPinned(verseId: Int, version: String): Flow<Boolean>

    @Query("DELETE FROM pins")
    suspend fun deleteAllPins()

    @Transaction
    suspend fun standardizePinVersions() {
        val all = getAllPinsSync()
        val badVersions = listOf("pericope", "percope", "mizogobible", "verse", "mgb", "mizo bible", "mizov", "mizo go bible", "pericope bible", "pericope_bible")

        val mzovIds = all.filter { it.version.lowercase().trim() == "mzov" }.map { it.verseId }.toMutableSet()
        val badOnes = all.filter { it.version.lowercase().trim() in badVersions && it.version.lowercase().trim() != "mzov" }

        badOnes.forEach { bad ->
            deletePin(bad)
            if (!mzovIds.contains(bad.verseId)) {
                insertPin(bad.copy(version = "MzOV"))
                mzovIds.add(bad.verseId)
            }
        }
    }

    // --- NOTE QUERIES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE verseId = :verseId LIMIT 1")
    fun getNoteForVerse(verseId: Int): Flow<Note?>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("UPDATE notes SET version = 'MzOV' WHERE LOWER(TRIM(version)) IN ('pericope', 'percope', 'mizogobible', 'verse', 'mgb', 'mizo bible', 'mizov', 'mizo go bible', 'pericope bible', 'pericope_bible')")
    suspend fun standardizeNoteVersions()

    // --- READING LOG QUERIES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingLog(log: ReadingLog)

    @Query("SELECT * FROM reading_logs WHERE date = :date LIMIT 1")
    suspend fun getReadingLogByDate(date: String): ReadingLog?

    @Query("SELECT * FROM reading_logs ORDER BY date DESC LIMIT 30")
    fun getAllReadingLogs(): Flow<List<ReadingLog>>

    @Transaction
    suspend fun incrementVersesRead(date: String) {
        val existing = getReadingLogByDate(date)
        if (existing != null) {
            insertReadingLog(existing.copy(versesRead = existing.versesRead + 1))
        } else {
            insertReadingLog(ReadingLog(date, 1, 0))
        }
    }

    @Transaction
    suspend fun addReadingTime(date: String, seconds: Long) {
        val existing = getReadingLogByDate(date)
        if (existing != null) {
            insertReadingLog(existing.copy(secondsRead = existing.secondsRead + seconds))
        } else {
            insertReadingLog(ReadingLog(date, 0, seconds))
        }
    }

    // --- HOURLY LOGS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyLog(log: HourlyReadingLog)

    @Query("SELECT * FROM hourly_reading_logs WHERE date = :date AND hour = :hour LIMIT 1")
    suspend fun getHourlyLog(date: String, hour: Int): HourlyReadingLog?

    @Query("SELECT * FROM hourly_reading_logs WHERE date = :date ORDER BY hour ASC")
    fun getHourlyLogsForDate(date: String): Flow<List<HourlyReadingLog>>

    @Transaction
    suspend fun addHourlyReadingTime(date: String, hour: Int, seconds: Long) {
        val existing = getHourlyLog(date, hour)
        if (existing != null) {
            insertHourlyLog(existing.copy(secondsRead = existing.secondsRead + seconds))
        } else {
            insertHourlyLog(HourlyReadingLog(date, hour, seconds))
        }
    }

    @Transaction
    suspend fun clearAllUserData() {
        deleteAllBookmarks()
        deleteAllPins()
        deleteAllNotes()
    }
}
