package com.qbitcore.booklip.data.cloud

import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the redirect Uri captured by [OAuthRedirectActivity] (launched by
 * the OS when the Custom Tab navigates to one of our redirect schemes) back
 * to the suspended [OAuthClient.authorize] call that is awaiting it.
 */
object OAuthRedirectBridge {
    @Volatile private var pending: CompletableDeferred<Uri>? = null
    private val main = Handler(Looper.getMainLooper())

    fun begin(): CompletableDeferred<Uri> {
        pending?.completeExceptionally(OAuthCancelledException())
        return CompletableDeferred<Uri>().also { pending = it }
    }

    fun complete(uri: Uri) {
        pending?.complete(uri)
        pending = null
    }

    fun cancel() {
        pending?.completeExceptionally(OAuthCancelledException())
        pending = null
    }

    /**
     * Custom Tabs report nothing when the user just closes the sign-in page.
     * But the app's own activity coming back to the foreground with a flow
     * still pending means exactly that — a successful sign-in arrives through
     * [complete] first. The short delay covers the redirect activity being
     * delivered a moment after the resume.
     */
    fun onHostResumed() {
        val waiting = pending ?: return
        main.postDelayed({ if (pending === waiting) cancel() }, 1200)
    }
}
