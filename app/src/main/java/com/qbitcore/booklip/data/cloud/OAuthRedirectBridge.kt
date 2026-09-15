package com.qbitcore.booklip.data.cloud

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the redirect Uri captured by [OAuthRedirectActivity] (launched by
 * the OS when the Custom Tab navigates to our booklip:// scheme) back to the
 * suspend [OAuthClient.authorize] call that is awaiting it.
 */
object OAuthRedirectBridge {
    @Volatile private var pending: CompletableDeferred<Uri>? = null

    fun begin(): CompletableDeferred<Uri> {
        val deferred = CompletableDeferred<Uri>()
        pending = deferred
        return deferred
    }

    fun complete(uri: Uri) {
        pending?.complete(uri)
        pending = null
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
