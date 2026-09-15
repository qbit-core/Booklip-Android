package com.qbitcore.booklip

import android.app.Application
import androidx.room.Room
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.data.BooklipDatabase
import com.qbitcore.booklip.data.FileStore
import com.qbitcore.booklip.data.ReadingStatsRepository
import com.qbitcore.booklip.data.cloud.CloudRepository
import com.qbitcore.booklip.data.cloud.CloudTokenStore
import com.qbitcore.booklip.settings.SettingsRepository

class BooklipApplication : Application() {
    lateinit var repository: BookRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var statsRepository: ReadingStatsRepository
        private set
    lateinit var cloudRepository: CloudRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, BooklipDatabase::class.java, "booklip.db")
            // Pre-release app, no migrations written yet — a schema bump just
            // recreates the tables instead of crashing on missing migrations.
            .fallbackToDestructiveMigration()
            .build()
        val fileStore = FileStore(this)
        repository = BookRepository(this, database, fileStore)
        settingsRepository = SettingsRepository(this)
        statsRepository = ReadingStatsRepository(this)
        cloudRepository = CloudRepository(this, CloudTokenStore(this), repository)
    }
}
