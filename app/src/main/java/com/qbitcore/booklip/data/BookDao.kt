package com.qbitcore.booklip.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qbitcore.booklip.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): Book?

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun get(ids: List<String>): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: Book)

    // Column-level updates: a whole-row @Update from a stale copy of the book
    // would silently undo a concurrent change (e.g. a move while reading).
    @Query("UPDATE books SET progress = :progress, progressUpdated = :updated WHERE id = :id")
    suspend fun setProgress(id: String, progress: Double, updated: Long)

    @Query("UPDATE books SET progress = :progress, charIndex = :charIndex, progressUpdated = :updated WHERE id = :id")
    suspend fun setProgress(id: String, progress: Double, charIndex: Int, updated: Long)

    @Query("UPDATE books SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setFolder(ids: List<String>, folderId: String?)

    @Query("UPDATE books SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)

    @Query("UPDATE books SET coverFileName = :cover WHERE id = :id")
    suspend fun setCover(id: String, cover: String?)

    @Delete
    suspend fun delete(book: Book)
}
