package com.qbitcore.booklip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qbitcore.booklip.data.cloud.OAuthRedirectBridge
import com.qbitcore.booklip.navigation.BooklipNavHost
import com.qbitcore.booklip.ui.theme.BooklipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BooklipTheme { BooklipNavHost() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back in front with a cloud sign-in still pending = the user closed the sign-in page.
        OAuthRedirectBridge.onHostResumed()
    }
}
