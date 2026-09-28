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
    version = 14,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        private const val TAG = "UserDatabase"

        @Volatile
        private var INSTANCE: UserDatabase? = null

        private fun safeMigrateUserDatabase(db: SupportSQLiteDatabase) {
            try {
                // 1. Migrate bookmarks
                val cursorBookmarks = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'")
                val bookmarksExists = cursorBookmarks.use { it.moveToFirst() }

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bookmarks_new` (
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
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(bookmarks)").use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            if (nameIdx >= 0) {
                                existingColumns.add(c.getString(nameIdx))
                            }
                        }
                    }
                    val verseIdExpr = if ("verseId" in existingColumns) "`verseId`" else "0"
                    val bookExpr = if ("book" in existingColumns) "`book`" else "''"
                    val chapterExpr = if ("chapter" in existingColumns) "`chapter`" else "0"
                    val verseExpr = if ("verse" in existingColumns) "`verse`" else "''"
                    val textExpr = if ("text" in existingColumns) "`text`" else "''"
                    val versionExpr = if ("version" in existingColumns) "`version`" else "'MzOV'"
                    val titleExpr = if ("title" in existingColumns) "`title`" else "''"
                    val noteExpr = if ("note" in existingColumns) "`note`" else "''"
                    val colorExpr = if ("color" in existingColumns) "`color`" else "'#FFF59D'"
                    val timestampExpr = if ("timestamp" in existingColumns) "`timestamp`" else "${System.currentTimeMillis()}"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `bookmarks_new` (`verseId`, `book`, `chapter`, `verse`, `text`, `version`, `title`, `note`, `color`, `timestamp`)
                        SELECT $verseIdExpr, $bookExpr, $chapterExpr, $verseExpr, $textExpr, $versionExpr, $titleExpr, $noteExpr, $colorExpr, $timestampExpr
                        FROM `bookmarks`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `bookmarks`")
                }
                db.execSQL("ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`")

                // 2. Migrate pins
                val cursorPins = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='pins'")
                val pinsExists = cursorPins.use { it.moveToFirst() }

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pins_new` (
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
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(pins)").use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            if (nameIdx >= 0) {
                                existingColumns.add(c.getString(nameIdx))
                            }
                        }
                    }
                    val verseIdExpr = if ("verseId" in existingColumns) "`verseId`" else "0"
                    val bookExpr = if ("book" in existingColumns) "`book`" else "''"
                    val chapterExpr = if ("chapter" in existingColumns) "`chapter`" else "0"
                    val verseExpr = if ("verse" in existingColumns) "`verse`" else "''"
                    val textExpr = if ("text" in existingColumns) "`text`" else "''"
                    val colorExpr = if ("color" in existingColumns) "`color`" else "'#FFD740'"
                    val versionExpr = if ("version" in existingColumns) "`version`" else "'MzOV'"
                    val timestampExpr = if ("timestamp" in existingColumns) "`timestamp`" else "${System.currentTimeMillis()}"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `pins_new` (`verseId`, `book`, `chapter`, `verse`, `text`, `color`, `version`, `timestamp`)
                        SELECT $verseIdExpr, $bookExpr, $chapterExpr, $verseExpr, $textExpr, $colorExpr, $versionExpr, $timestampExpr
                        FROM `pins`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `pins`")
                }
                db.execSQL("ALTER TABLE `pins_new` RENAME TO `pins`")

                // 3. Migrate notes
                val cursorNotes = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='notes'")
                val notesExists = cursorNotes.use { it.moveToFirst() }

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes_new` (
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
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(notes)").use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            if (nameIdx >= 0) {
                                existingColumns.add(c.getString(nameIdx))
                            }
                        }
                    }
                    val verseIdExpr = if ("verseId" in existingColumns) "`verseId`" else "0"
                    val bookExpr = if ("book" in existingColumns) "`book`" else "''"
                    val chapterExpr = if ("chapter" in existingColumns) "`chapter`" else "0"
                    val verseExpr = if ("verse" in existingColumns) "`verse`" else "''"
                    val textExpr = if ("text" in existingColumns) "`text`" else "''"
                    val bibleTextExpr = if ("bibleText" in existingColumns) "`bibleText`" else "''"
                    val colorExpr = if ("color" in existingColumns) "`color`" else "'#FF5252'"
                    val versionExpr = if ("version" in existingColumns) "`version`" else "'MGB'"
                    val timestampExpr = if ("timestamp" in existingColumns) "`timestamp`" else "${System.currentTimeMillis()}"

                    db.execSQL("""
                        INSERT OR REPLACE INTO `notes_new` (`verseId`, `book`, `chapter`, `verse`, `text`, `bibleText`, `color`, `version`, `timestamp`)
                        SELECT $verseIdExpr, $bookExpr, $chapterExpr, $verseExpr, $textExpr, $bibleTextExpr, $colorExpr, $versionExpr, $timestampExpr
                        FROM `notes`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `notes`")
                }
                db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")

                // 4. reading_logs & hourly_reading_logs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reading_logs` (
                        `date` TEXT NOT NULL,
                        `versesRead` INTEGER NOT NULL,
                        `secondsRead` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `hourly_reading_logs` (
                        `date` TEXT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `secondsRead` INTEGER NOT NULL,
                        PRIMARY KEY(`date`, `hour`)
                    )
                """.trimIndent())
            } catch (e: Exception) {
                Log.e(TAG, "Error in safeMigrateUserDatabase, recreating clean tables: ${e.message}", e)
                db.execSQL("DROP TABLE IF EXISTS `bookmarks_new`")
                db.execSQL("DROP TABLE IF EXISTS `pins_new`")
                db.execSQL("DROP TABLE IF EXISTS `notes_new`")
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

        private val ALL_MIGRATIONS = (1..13).map { startVersion ->
            object : Migration(startVersion, 14) {
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
