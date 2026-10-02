package com.zoliana.khampat.mizobible.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Bookmark::class, Pin::class, Note::class, ReadingLog::class, HourlyReadingLog::class],
    version = 15,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        private const val TAG = "UserDatabase"

        @Volatile
        private var INSTANCE: UserDatabase? = null

        private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
            return try {
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use {
                    it.moveToFirst()
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
            val columns = mutableSetOf<String>()
            try {
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) {
                            columns.add(cursor.getString(nameIdx))
                        }
                    }
                }
            } catch (_: Exception) {}
            return columns
        }

        private fun safeMigrateUserDatabase(db: SupportSQLiteDatabase) {
            try {
                // Drop any temporary tables that might have been left over
                db.execSQL("DROP TABLE IF EXISTS `bookmarks_new`")
                db.execSQL("DROP TABLE IF EXISTS `pins_new`")
                db.execSQL("DROP TABLE IF EXISTS `notes_new`")
                db.execSQL("DROP TABLE IF EXISTS `reading_logs_new`")
                db.execSQL("DROP TABLE IF EXISTS `hourly_reading_logs_new`")

                // 1. Migrate bookmarks
                val bookmarksExists = tableExists(db, "bookmarks")
                db.execSQL("""
                    CREATE TABLE `bookmarks_new` (
                        `verseId` INTEGER NOT NULL,
                        `book` TEXT NOT NULL,
                        `chapter` INTEGER NOT NULL,
                        `verse` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`verseId`, `version`)
                    )
                """.trimIndent())

                if (bookmarksExists) {
                    val cols = getTableColumns(db, "bookmarks")
                    val verseIdExpr = if ("verseId" in cols) "COALESCE(`verseId`, 0)" else "0"
                    val bookExpr = if ("book" in cols) "COALESCE(`book`, '')" else "''"
                    val chapterExpr = if ("chapter" in cols) "COALESCE(`chapter`, 0)" else "0"
                    val verseExpr = if ("verse" in cols) "COALESCE(`verse`, '')" else "''"
                    val textExpr = if ("text" in cols) "COALESCE(`text`, '')" else "''"
                    val versionExpr = if ("version" in cols) "COALESCE(NULLIF(TRIM(`version`), ''), 'MzOV')" else "'MzOV'"
                    val titleExpr = if ("title" in cols) "COALESCE(`title`, '')" else "''"
                    val noteExpr = if ("note" in cols) "COALESCE(`note`, '')" else "''"
                    val colorExpr = if ("color" in cols) "COALESCE(NULLIF(TRIM(`color`), ''), '#FFF59D')" else "'#FFF59D'"
                    val timestampExpr = if ("timestamp" in cols) "COALESCE(`timestamp`, ${System.currentTimeMillis()})" else "${System.currentTimeMillis()}"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `bookmarks_new` (`verseId`, `book`, `chapter`, `verse`, `text`, `version`, `title`, `note`, `color`, `timestamp`)
                        SELECT $verseIdExpr, $bookExpr, $chapterExpr, $verseExpr, $textExpr, $versionExpr, $titleExpr, $noteExpr, $colorExpr, $timestampExpr
                        FROM `bookmarks`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `bookmarks`")
                }
                db.execSQL("ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`")

                // 2. Migrate pins
                val pinsExists = tableExists(db, "pins")
                db.execSQL("""
                    CREATE TABLE `pins_new` (
                        `verseId` INTEGER NOT NULL,
                        `book` TEXT NOT NULL,
                        `chapter` INTEGER NOT NULL,
                        `verse` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`verseId`, `version`)
                    )
                """.trimIndent())

                if (pinsExists) {
                    val cols = getTableColumns(db, "pins")
                    val verseIdExpr = if ("verseId" in cols) "COALESCE(`verseId`, 0)" else "0"
                    val bookExpr = if ("book" in cols) "COALESCE(`book`, '')" else "''"
                    val chapterExpr = if ("chapter" in cols) "COALESCE(`chapter`, 0)" else "0"
                    val verseExpr = if ("verse" in cols) "COALESCE(`verse`, '')" else "''"
                    val textExpr = if ("text" in cols) "COALESCE(`text`, '')" else "''"
                    val colorExpr = if ("color" in cols) "COALESCE(NULLIF(TRIM(`color`), ''), '#FFD740')" else "'#FFD740'"
                    val versionExpr = if ("version" in cols) "COALESCE(NULLIF(TRIM(`version`), ''), 'MzOV')" else "'MzOV'"
                    val timestampExpr = if ("timestamp" in cols) "COALESCE(`timestamp`, ${System.currentTimeMillis()})" else "${System.currentTimeMillis()}"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `pins_new` (`verseId`, `book`, `chapter`, `verse`, `text`, `color`, `version`, `timestamp`)
                        SELECT $verseIdExpr, $bookExpr, $chapterExpr, $verseExpr, $textExpr, $colorExpr, $versionExpr, $timestampExpr
                        FROM `pins`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `pins`")
                }
                db.execSQL("ALTER TABLE `pins_new` RENAME TO `pins`")

                // 3. Migrate notes
                val notesExists = tableExists(db, "notes")
                db.execSQL("""
                    CREATE TABLE `notes_new` (
                        `verseId` INTEGER NOT NULL,
                        `book` TEXT NOT NULL,
                        `chapter` INTEGER NOT NULL,
                        `verse` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `bibleText` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`verseId`)
                    )
                """.trimIndent())

                if (notesExists) {
                    val cols = getTableColumns(db, "notes")
                    val verseIdExpr = if ("verseId" in cols) "COALESCE(`verseId`, 0)" else "0"
                    val bookExpr = if ("book" in cols) "COALESCE(`book`, '')" else "''"
                    val chapterExpr = if ("chapter" in cols) "COALESCE(`chapter`, 0)" else "0"
                    val verseExpr = if ("verse" in cols) "COALESCE(`verse`, '')" else "''"
                    val textExpr = if ("text" in cols) "COALESCE(`text`, '')" else "''"
                    val bibleTextExpr = if ("bibleText" in cols) "COALESCE(`bibleText`, '')" else "''"
                    val colorExpr = if ("color" in cols) "COALESCE(NULLIF(TRIM(`color`), ''), '#FF5252')" else "'#FF5252'"
                    val versionExpr = if ("version" in cols) "COALESCE(NULLIF(TRIM(`version`), ''), 'MGB')" else "'MGB'"
                    val timestampExpr = if ("timestamp" in cols) "COALESCE(`timestamp`, ${System.currentTimeMillis()})" else "${System.currentTimeMillis()}"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `notes_new` (`verseId`, `book`, `chapter`, `verse`, `text`, `bibleText`, `color`, `version`, `timestamp`)
                        SELECT $verseIdExpr, $bookExpr, $chapterExpr, $verseExpr, $textExpr, $bibleTextExpr, $colorExpr, $versionExpr, $timestampExpr
                        FROM `notes`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `notes`")
                }
                db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")

                // 4. Migrate reading_logs
                val readingLogsExists = tableExists(db, "reading_logs")
                db.execSQL("""
                    CREATE TABLE `reading_logs_new` (
                        `date` TEXT NOT NULL,
                        `versesRead` INTEGER NOT NULL,
                        `secondsRead` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())

                if (readingLogsExists) {
                    val cols = getTableColumns(db, "reading_logs")
                    val dateExpr = if ("date" in cols) "COALESCE(`date`, '')" else "''"
                    val versesReadExpr = if ("versesRead" in cols) "COALESCE(`versesRead`, 0)" else "0"
                    val secondsReadExpr = if ("secondsRead" in cols) "COALESCE(`secondsRead`, 0)" else "0"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `reading_logs_new` (`date`, `versesRead`, `secondsRead`)
                        SELECT $dateExpr, $versesReadExpr, $secondsReadExpr
                        FROM `reading_logs`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `reading_logs`")
                }
                db.execSQL("ALTER TABLE `reading_logs_new` RENAME TO `reading_logs`")

                // 5. Migrate hourly_reading_logs
                val hourlyReadingLogsExists = tableExists(db, "hourly_reading_logs")
                db.execSQL("""
                    CREATE TABLE `hourly_reading_logs_new` (
                        `date` TEXT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `secondsRead` INTEGER NOT NULL,
                        PRIMARY KEY(`date`, `hour`)
                    )
                """.trimIndent())

                if (hourlyReadingLogsExists) {
                    val cols = getTableColumns(db, "hourly_reading_logs")
                    val dateExpr = if ("date" in cols) "COALESCE(`date`, '')" else "''"
                    val hourExpr = if ("hour" in cols) "COALESCE(`hour`, 0)" else "0"
                    val secondsReadExpr = if ("secondsRead" in cols) "COALESCE(`secondsRead`, 0)" else "0"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `hourly_reading_logs_new` (`date`, `hour`, `secondsRead`)
                        SELECT $dateExpr, $hourExpr, $secondsReadExpr
                        FROM `hourly_reading_logs`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `hourly_reading_logs`")
                }
                db.execSQL("ALTER TABLE `hourly_reading_logs_new` RENAME TO `hourly_reading_logs`")

            } catch (e: Exception) {
                Log.e(TAG, "Error in safeMigrateUserDatabase, recreating clean tables: ${e.message}", e)
                db.execSQL("DROP TABLE IF EXISTS `bookmarks_new`")
                db.execSQL("DROP TABLE IF EXISTS `pins_new`")
                db.execSQL("DROP TABLE IF EXISTS `notes_new`")
                db.execSQL("DROP TABLE IF EXISTS `reading_logs_new`")
                db.execSQL("DROP TABLE IF EXISTS `hourly_reading_logs_new`")
                db.execSQL("DROP TABLE IF EXISTS `bookmarks`")
                db.execSQL("DROP TABLE IF EXISTS `pins`")
                db.execSQL("DROP TABLE IF EXISTS `notes`")
                db.execSQL("DROP TABLE IF EXISTS `reading_logs`")
                db.execSQL("DROP TABLE IF EXISTS `hourly_reading_logs`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`verseId` INTEGER NOT NULL, `book` TEXT NOT NULL, `chapter` INTEGER NOT NULL, `verse` TEXT NOT NULL, `text` TEXT NOT NULL, `version` TEXT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `color` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`verseId`, `version`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pins` (`verseId` INTEGER NOT NULL, `book` TEXT NOT NULL, `chapter` INTEGER NOT NULL, `verse` TEXT NOT NULL, `text` TEXT NOT NULL, `color` TEXT NOT NULL, `version` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`verseId`, `version`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`verseId` INTEGER NOT NULL, `book` TEXT NOT NULL, `chapter` INTEGER NOT NULL, `verse` TEXT NOT NULL, `text` TEXT NOT NULL, `bibleText` TEXT NOT NULL, `color` TEXT NOT NULL, `version` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`verseId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reading_logs` (`date` TEXT NOT NULL, `versesRead` INTEGER NOT NULL, `secondsRead` INTEGER NOT NULL, PRIMARY KEY(`date`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_reading_logs` (`date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `secondsRead` INTEGER NOT NULL, PRIMARY KEY(`date`, `hour`))")
            }
        }

        private val ALL_MIGRATIONS = (1..14).map { startVersion ->
            object : Migration(startVersion, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    safeMigrateUserDatabase(db)
                }
            }
        }.toTypedArray()

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "user_data.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
