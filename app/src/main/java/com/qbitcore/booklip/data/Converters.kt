package com.qbitcore.booklip.data

import androidx.room.TypeConverter
import com.qbitcore.booklip.model.BookFormat

class Converters {
    @TypeConverter
    fun fromBookFormat(format: BookFormat): String = format.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)
}
