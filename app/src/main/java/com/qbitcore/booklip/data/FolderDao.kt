package com.qbitcore.booklip.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qbitcore.booklip.model.BookFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY dateCreated ASC")
    fun observeAll(): Flow<List<BookFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: BookFolder)

    @Delete
    suspend fun delete(folder: BookFolder)
}
