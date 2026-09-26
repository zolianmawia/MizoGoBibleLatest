package com.zoliana.khampat.mizobible.data

import kotlinx.coroutines.flow.Flow

class BibleRepository(val bibleDao: BibleDao, val userDao: UserDao) {

    // BIBLE TEXT (From BibleDatabase)
    fun getVerses(version: String, bookName: String, chapter: Int): Flow<List<BibleVerse>> =
        bibleDao.getVerses(version, bookName, chapter)

    suspend fun getVersesSync(version: String, bookName: String, chapter: Int): List<BibleVerse> =
        bibleDao.getVersesSync(version, bookName, chapter)

    fun getVersesByBookAndChapter(bookName: String, chapter: Int): Flow<List<BibleVerse>> =
        bibleDao.getVersesByBookAndChapter(bookName, chapter)

    fun getAllBooks(version: String): Flow<List<String>> = bibleDao.getAllBooks(version)

    fun getAllVersions(): Flow<List<String>> = bibleDao.getAllVersions()

    fun getChapterCount(version: String, bookName: String): Flow<Int?> = bibleDao.getChapterCount(version, bookName)

    suspend fun getChapterCountSync(version: String, bookName: String): Int? = 
        bibleDao.getChapterCountSync(version, bookName)

    suspend fun getVerseCountByVersion(version: String): Int =
        bibleDao.getVerseCountByVersion(version)

    suspend fun getVerseCountSync(version: String, bookName: String, chapter: Int): Int =
        bibleDao.getVerseCountSync(version, bookName, chapter)

    fun searchBible(version: String, query: String, queryWithoutSpace: String, bookName: String?, bookFilterList: List<String>?): Flow<List<BibleVerse>> {
        return if (bookFilterList.isNullOrEmpty()) {
            bibleDao.searchBibleWithoutBookFilter(version, query, queryWithoutSpace, bookName)
        } else {
            bibleDao.searchBibleWithBookFilter(version, query, queryWithoutSpace, bookName, bookFilterList)
        }
    }

    fun searchBroad(version: String, query: String, queryWithoutSpace: String, fuzzyPart: String, bookName: String?, bookFilterList: List<String>?): Flow<List<BibleVerse>> {
        return if (bookFilterList.isNullOrEmpty()) {
            bibleDao.searchBroadWithoutBookFilter(version, query, queryWithoutSpace, fuzzyPart, bookName)
        } else {
            bibleDao.searchBroadWithBookFilter(version, query, queryWithoutSpace, fuzzyPart, bookName, bookFilterList)
        }
    }

    suspend fun searchVersesRaw(query: androidx.sqlite.db.SupportSQLiteQuery): List<BibleVerse> =
        bibleDao.searchVersesRaw(query)

    fun searchByBookChapterVerse(version: String, bookName: String, bookPattern: String, chapter: Int, verse: String): Flow<List<BibleVerse>> =
        bibleDao.searchByBookChapterVerse(version, bookName, bookPattern, chapter, verse)

    fun searchByBookAndChapter(version: String, bookName: String, bookPattern: String, chapter: Int): Flow<List<BibleVerse>> =
        bibleDao.searchByBookAndChapter(version, bookName, bookPattern, chapter)

    fun searchByBookOnly(version: String, bookName: String, bookPattern: String): Flow<List<BibleVerse>> =
        bibleDao.searchByBookOnly(version, bookName, bookPattern)

    fun searchByReference(version: String, chapter: Int, verse: String): Flow<List<BibleVerse>> =
        bibleDao.searchByReference(version, chapter, verse)

    suspend fun getVerseId(version: String, bookName: String, chapter: Int, verse: String): Int? =
        bibleDao.getVerseId(version, bookName, chapter, verse)

    suspend fun insertVerses(verses: List<BibleVerse>) = bibleDao.insertVerses(verses)

    suspend fun deleteVersion(versionCode: String) = bibleDao.deleteVersion(versionCode)
    
    suspend fun deleteMizoBible() = bibleDao.deleteMizoBible()
    
    suspend fun getTotalVerseCount(): Int = bibleDao.getTotalVerseCount()
    suspend fun getVerseByOffset(offset: Int): BibleVerse? = bibleDao.getVerseByOffset(offset)

    // --- USER DATA (From UserDatabase) ---
    suspend fun insertBookmark(bookmark: Bookmark) = userDao.insertBookmark(bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark) = userDao.deleteBookmark(bookmark)
    suspend fun deleteAllBookmarks() = userDao.deleteAllBookmarks()
    fun getAllBookmarks(): Flow<List<Bookmark>> = userDao.getAllBookmarks()
    fun getBookmark(verseId: Int, version: String): Flow<Bookmark?> = userDao.getBookmark(verseId, version)

    // --- PIN FUNCTIONS ---
    suspend fun insertPin(pin: Pin) = userDao.insertPin(pin)
    suspend fun deletePin(pin: Pin) = userDao.deletePin(pin)
    suspend fun deleteAllPins() = userDao.deleteAllPins()
    fun getAllPins(): Flow<List<Pin>> = userDao.getAllPins()
    fun getPin(verseId: Int, version: String): Flow<Pin?> = userDao.getPin(verseId, version)

    // --- NOTE FUNCTIONS ---
    suspend fun insertNote(note: Note) = userDao.insertNote(note)
    suspend fun deleteNote(note: Note) = userDao.deleteNote(note)
    suspend fun deleteAllNotes() = userDao.deleteAllNotes()
    fun getAllNotes(): Flow<List<Note>> = userDao.getAllNotes()
    fun getNoteForVerse(verseId: Int): Flow<Note?> = userDao.getNoteForVerse(verseId)

    // --- READING LOGS ---
    suspend fun incrementVersesRead(date: String) = userDao.incrementVersesRead(date)
    suspend fun addReadingTime(date: String, seconds: Long) = userDao.addReadingTime(date, seconds)
    fun getAllReadingLogs(): Flow<List<ReadingLog>> = userDao.getAllReadingLogs()

    // --- HOURLY LOGS ---
    fun getHourlyLogsForDate(date: String): Flow<List<HourlyReadingLog>> = userDao.getHourlyLogsForDate(date)
    suspend fun addHourlyReadingTime(date: String, hour: Int, seconds: Long) = userDao.addHourlyReadingTime(date, hour, seconds)

    suspend fun clearAllUserData() = userDao.clearAllUserData()
}
