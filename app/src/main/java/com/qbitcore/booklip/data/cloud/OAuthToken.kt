package com.qbitcore.booklip.data.cloud

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAtEpochMillis - 60_000
}

class OAuthException(message: String) : Exception(message)

/** The user closed the sign-in page — not an error worth showing. */
class OAuthCancelledException : Exception("Sign-in was cancelled.")
