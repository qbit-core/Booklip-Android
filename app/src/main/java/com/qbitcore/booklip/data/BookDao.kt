package com.qbitcore.booklip.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qbitcore.booklip.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): Book?

    @Query("SELECT * FROM books WHERE folderId = :folderId")
    suspend fun inFolder(folderId: String): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: Book)

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)
}
