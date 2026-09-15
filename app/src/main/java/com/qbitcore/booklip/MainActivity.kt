package com.qbitcore.booklip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qbitcore.booklip.navigation.BooklipNavHost
import com.qbitcore.booklip.ui.theme.BooklipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BooklipApplication
        setContent {
            BooklipTheme {
                BooklipNavHost(
                    repository = app.repository,
                    settingsRepository = app.settingsRepository,
                    statsRepository = app.statsRepository,
                    cloudRepository = app.cloudRepository,
                )
            }
        }
    }
}
