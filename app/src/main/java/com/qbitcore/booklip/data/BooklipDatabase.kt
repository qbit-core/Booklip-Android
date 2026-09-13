package com.qbitcore.booklip.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder

@Database(entities = [Book::class, BookFolder::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BooklipDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
}
