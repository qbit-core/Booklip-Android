package com.qbitcore.booklip.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Highlight

@Database(
    entities = [Book::class, BookFolder::class, Bookmark::class, Highlight::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BooklipDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
}
