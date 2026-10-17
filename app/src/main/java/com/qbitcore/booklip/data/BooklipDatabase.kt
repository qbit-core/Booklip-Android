package com.qbitcore.booklip.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Highlight

@Database(
    entities = [Book::class, BookFolder::class, Bookmark::class, Highlight::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BooklipDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        /**
         * v2 stored positions as paragraph indices and highlights as whole
         * paragraphs. v3 uses character offsets (as iOS does): text books keep
         * their `progress` but lose the paragraph index, and the old
         * paragraph highlights cannot be mapped to a range, so they are dropped.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE books SET charIndex = ${Book.UNKNOWN_POSITION} WHERE format != 'PDF'")
                db.execSQL("DROP TABLE IF EXISTS highlights")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS highlights (" +
                        "id TEXT NOT NULL, bookId TEXT NOT NULL, location INTEGER NOT NULL, " +
                        "length INTEGER NOT NULL, color TEXT NOT NULL, snippet TEXT NOT NULL, " +
                        "progress REAL NOT NULL, date INTEGER NOT NULL, PRIMARY KEY(id))"
                )
            }
        }

        /** v4 adds the detected book language (null until detected). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN language TEXT")
            }
        }
    }
}
