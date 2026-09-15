package com.qbitcore.booklip.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cloudTokenDataStore by preferencesDataStore(name = "cloud_tokens")

/**
 * Tokens are stored in plain DataStore preferences, same trust model as the
 * iOS app's UserDefaults token storage (see Booklip's CLAUDE.md "Privacy"
 * section) — not a secret store, but this app never talks to a backend of
 * its own, so the only thing at risk is read-only access to the user's own
 * cloud files, on their own device.
 */
class CloudTokenStore(private val context: Context) {
    private fun keys(provider: CloudProvider) = Triple(
        stringPreferencesKey("${provider.name}_access"),
        stringPreferencesKey("${provider.name}_refresh"),
        longPreferencesKey("${provider.name}_expires"),
    )

    fun observe(provider: CloudProvider): Flow<OAuthToken?> {
        val (accessKey, refreshKey, expiresKey) = keys(provider)
        return context.cloudTokenDataStore.data.map { prefs ->
            val access = prefs[accessKey] ?: return@map null
            OAuthToken(
                accessToken = access,
                refreshToken = prefs[refreshKey],
                expiresAtEpochMillis = prefs[expiresKey] ?: 0L,
            )
        }
    }

    suspend fun save(provider: CloudProvider, token: OAuthToken) {
        val (accessKey, refreshKey, expiresKey) = keys(provider)
        context.cloudTokenDataStore.edit { prefs ->
            prefs[accessKey] = token.accessToken
            token.refreshToken?.let { prefs[refreshKey] = it }
            prefs[expiresKey] = token.expiresAtEpochMillis
        }
    }

    suspend fun clear(provider: CloudProvider) {
        val (accessKey, refreshKey, expiresKey) = keys(provider)
        context.cloudTokenDataStore.edit { prefs ->
            prefs.remove(accessKey)
            prefs.remove(refreshKey)
            prefs.remove(expiresKey)
        }
    }
}
