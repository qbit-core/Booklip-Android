package com.qbitcore.booklip.data.cloud

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAtEpochMillis
}

class OAuthException(message: String) : Exception(message)
