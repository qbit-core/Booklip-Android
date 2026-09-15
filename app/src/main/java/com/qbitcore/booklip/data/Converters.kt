package com.qbitcore.booklip.data

import androidx.room.TypeConverter
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.model.HighlightColor

class Converters {
    @TypeConverter
    fun fromBookFormat(format: BookFormat): String = format.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun fromHighlightColor(color: HighlightColor): String = color.name

    @TypeConverter
    fun toHighlightColor(value: String): HighlightColor = HighlightColor.valueOf(value)
}
