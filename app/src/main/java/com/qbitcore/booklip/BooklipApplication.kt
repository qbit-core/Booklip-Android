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
import com.qbitcore.booklip.tts.TtsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BooklipApplication : Application() {
    /** For work that must outlive the screen that started it (saving on close, cloud imports). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: BookRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var statsRepository: ReadingStatsRepository
        private set
    lateinit var cloudRepository: CloudRepository
        private set
    lateinit var tts: TtsController
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, BooklipDatabase::class.java, "booklip.db")
            .addMigrations(BooklipDatabase.MIGRATION_2_3)
            // Only for schemas older than the first migration (early development builds).
            .fallbackToDestructiveMigration()
            .build()
        repository = BookRepository(this, database, FileStore(this))
        settingsRepository = SettingsRepository(this)
        statsRepository = ReadingStatsRepository(this)
        cloudRepository = CloudRepository(this, CloudTokenStore(this), repository)
        tts = TtsController(this, settingsRepository)
    }
}
