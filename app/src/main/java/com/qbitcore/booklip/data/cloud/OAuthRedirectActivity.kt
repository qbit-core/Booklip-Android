package com.qbitcore.booklip.data.cloud

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.qbitcore.booklip.MainActivity

/**
 * No UI of its own — the manifest registers it for the OAuth redirect
 * schemes, the OS launches it when the sign-in page redirects there, and it
 * hands the redirect Uri to [OAuthRedirectBridge].
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { OAuthRedirectBridge.complete(it) }
        // Return to the app and close the sign-in tab sitting above it.
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
