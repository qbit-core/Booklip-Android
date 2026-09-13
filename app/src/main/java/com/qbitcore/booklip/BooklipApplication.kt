package com.qbitcore.booklip

import android.app.Application
import androidx.room.Room
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.data.BooklipDatabase
import com.qbitcore.booklip.data.FileStore
import com.qbitcore.booklip.settings.SettingsRepository

class BooklipApplication : Application() {
    lateinit var repository: BookRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, BooklipDatabase::class.java, "booklip.db").build()
        val fileStore = FileStore(this)
        repository = BookRepository(this, database, fileStore)
        settingsRepository = SettingsRepository(this)
    }
}
