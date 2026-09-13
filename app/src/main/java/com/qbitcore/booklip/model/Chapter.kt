package com.qbitcore.booklip.model

/** A table-of-contents entry pointing at a fractional position (0..1) in the book. */
data class Chapter(
    val title: String,
    val progress: Double,
    val level: Int = 0,
)
