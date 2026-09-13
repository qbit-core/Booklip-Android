package com.qbitcore.booklip.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "folders")
data class BookFolder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dateCreated: Long = System.currentTimeMillis(),
)
