package com.qbitcore.booklip.data.cloud

import android.app.Activity
import android.os.Bundle

/**
 * No UI of its own — the manifest registers it for the booklip:// scheme, the
 * OS launches it when a Custom Tab navigates there at the end of an OAuth
 * flow, and it just hands the redirect Uri to [OAuthRedirectBridge] and closes.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { OAuthRedirectBridge.complete(it) }
        finish()
    }
}
