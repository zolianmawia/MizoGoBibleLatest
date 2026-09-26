package com.zoliana.khampat.mizobible.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleDao {
    @RawQuery
    suspend fun searchVersesRaw(query: SupportSQLiteQuery): List<BibleVerse>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND book = :bookName AND chapter = :chapterNumber 
        ORDER BY id ASC
    """
    )
    fun getVerses(version: String, bookName: String, chapterNumber: Int): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND book = :bookName AND chapter = :chapterNumber 
        ORDER BY id ASC
    """
    )
    suspend fun getVersesSync(
        version: String,
        bookName: String,
        chapterNumber: Int
    ): List<BibleVerse>

    @Query(
        """
        SELECT book FROM mizogobible 
        WHERE LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))
        GROUP BY book
        ORDER BY MIN(id) ASC
    """
    )
    fun getAllBooks(version: String): Flow<List<String>>

    @Query("SELECT DISTINCT type FROM mizogobible WHERE type IS NOT NULL")
    fun getAllVersions(): Flow<List<String>>

    @Query(
        """
        SELECT MAX(chapter) FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND book = :bookName
    """
    )
    suspend fun getChapterCountSync(version: String, bookName: String): Int?

    @Query(
        """
        SELECT MAX(chapter) FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND book = :bookName
    """
    )
    fun getChapterCount(version: String, bookName: String): Flow<Int?>

    @Query(
        """
        SELECT COUNT(*) FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
    """
    )
    suspend fun getVerseCountByVersion(version: String): Int

    @Query(
        """
        SELECT MAX(CAST(verse AS INTEGER)) FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND book = :bookName AND chapter = :chapter
    """
    )
    suspend fun getVerseCountSync(version: String, bookName: String, chapter: Int): Int

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (:bookName IS NULL OR LOWER(TRIM(book)) = LOWER(TRIM(:bookName)))
        AND (
            text LIKE '%' || :query || '%' 
            OR normalized_text LIKE '%' || :query || '%'
            OR searchText LIKE '%' || :queryWithoutSpace || '%'
            OR (book LIKE :query || '%' AND :bookName IS NULL)
        ) 
        ORDER BY id ASC
        LIMIT 1000
    """
    )
    fun searchBibleWithoutBookFilter(
        version: String,
        query: String,
        queryWithoutSpace: String,
        bookName: String?
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (:bookName IS NULL OR LOWER(TRIM(book)) = LOWER(TRIM(:bookName)))
        AND book IN (:bookFilterList)
        AND (
            text LIKE '%' || :query || '%' 
            OR normalized_text LIKE '%' || :query || '%'
            OR searchText LIKE '%' || :queryWithoutSpace || '%'
            OR (book LIKE :query || '%' AND :bookName IS NULL)
        ) 
        ORDER BY id ASC
        LIMIT 1000
    """
    )
    fun searchBibleWithBookFilter(
        version: String,
        query: String,
        queryWithoutSpace: String,
        bookName: String?,
        bookFilterList: List<String>
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (:bookName IS NULL OR LOWER(TRIM(book)) = LOWER(TRIM(:bookName)))
        AND (
            text LIKE '%' || :query || '%' 
            OR normalized_text LIKE '%' || :query || '%'
            OR searchText LIKE '%' || :queryWithoutSpace || '%'
            OR normalized_text LIKE '%' || :fuzzyPart || '%'
            OR (book LIKE :query || '%' AND :bookName IS NULL)
        ) 
        ORDER BY id ASC
        LIMIT 1000
    """
    )
    fun searchBroadWithoutBookFilter(
        version: String,
        query: String,
        queryWithoutSpace: String,
        fuzzyPart: String,
        bookName: String?
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (:bookName IS NULL OR LOWER(TRIM(book)) = LOWER(TRIM(:bookName)))
        AND book IN (:bookFilterList)
        AND (
            text LIKE '%' || :query || '%' 
            OR normalized_text LIKE '%' || :query || '%'
            OR searchText LIKE '%' || :queryWithoutSpace || '%'
            OR normalized_text LIKE '%' || :fuzzyPart || '%'
            OR (book LIKE :query || '%' AND :bookName IS NULL)
        ) 
        ORDER BY id ASC
        LIMIT 1000
    """
    )
    fun searchBroadWithBookFilter(
        version: String,
        query: String,
        queryWithoutSpace: String,
        fuzzyPart: String,
        bookName: String?,
        bookFilterList: List<String>
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (LOWER(TRIM(book)) = LOWER(TRIM(:bookName)) OR book LIKE :bookPattern || '%')
        AND chapter = :chapter AND verse = :verse
        ORDER BY id ASC
        LIMIT 50
    """
    )
    fun searchByBookChapterVerse(
        version: String,
        bookName: String,
        bookPattern: String,
        chapter: Int,
        verse: String
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (LOWER(TRIM(book)) = LOWER(TRIM(:bookName)) OR book LIKE :bookPattern || '%')
        AND chapter = :chapter
        ORDER BY id ASC
        LIMIT 100
    """
    )
    fun searchByBookAndChapter(
        version: String,
        bookName: String,
        bookPattern: String,
        chapter: Int
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND (LOWER(TRIM(book)) = LOWER(TRIM(:bookName)) OR book LIKE :bookPattern || '%')
        ORDER BY id ASC
        LIMIT 100
    """
    )
    fun searchByBookOnly(
        version: String,
        bookName: String,
        bookPattern: String
    ): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT * FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND chapter = :chapter AND verse = :verse
        ORDER BY id ASC
        LIMIT 100
    """
    )
    fun searchByReference(version: String, chapter: Int, verse: String): Flow<List<BibleVerse>>

    @Query(
        """
        SELECT id FROM mizogobible 
        WHERE (LOWER(TRIM(type)) = LOWER(TRIM(:version)) 
        OR (LOWER(TRIM(:version)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
            AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible')))) 
        AND book = :bookName AND chapter = :chapter AND verse = :verse
        LIMIT 1
    """
    )
    suspend fun getVerseId(version: String, bookName: String, chapter: Int, verse: String): Int?

    @Query("SELECT book FROM mizogobible LIMIT 1")
    suspend fun getFirstBookName(): String?

    @Query("SELECT * FROM mizogobible WHERE book = :bookName AND chapter = :chapterNumber ORDER BY id ASC")
    fun getVersesByBookAndChapter(bookName: String, chapterNumber: Int): Flow<List<BibleVerse>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVerses(verses: List<BibleVerse>)

    @Query(
        """
        UPDATE mizogobible SET type = 'MzOV' 
        WHERE LOWER(TRIM(type)) IN ('pericope', 'percope', 'mizogobible', 'verse', 'mgb', 'mizo bible', 'mizov', 'pericope bible', 'pericope_bible', 'mizo go bible')
        OR LOWER(TRIM(type)) LIKE 'pericope%'
        OR type IS NULL
    """
    )
    suspend fun standardizeMizoTags()

    @Query("DELETE FROM mizogobible WHERE LOWER(TRIM(type)) = LOWER(TRIM(:versionCode))")
    suspend fun deleteVersion(versionCode: String)

    @Query(
        """
        DELETE FROM mizogobible 
        WHERE LOWER(TRIM(type)) IN ('mzov', 'pericope', 'percope', 'mizogobible', 'verse', 'mgb', 'mizo bible', 'mizov', 'pericope bible', 'pericope_bible', 'mizo go bible')
        OR LOWER(TRIM(type)) LIKE 'pericope%'
        OR type IS NULL
    """
    )
    suspend fun deleteMizoBible()

    @Query("SELECT COUNT(*) FROM mizogobible WHERE type IS NOT NULL AND text IS NOT NULL AND text != ''")
    suspend fun getTotalVerseCount(): Int

    @Query("SELECT * FROM mizogobible WHERE type IS NOT NULL AND text IS NOT NULL AND text != '' ORDER BY id ASC LIMIT 1 OFFSET :offset")
    suspend fun getVerseByOffset(offset: Int): BibleVerse?
}
