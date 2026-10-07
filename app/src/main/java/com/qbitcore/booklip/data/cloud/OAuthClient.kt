package com.qbitcore.booklip.data.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * OAuth 2.0 Authorization Code + PKCE, using Chrome Custom Tabs for the
 * authorization step (a real browser, not an embedded WebView — required by
 * every provider here) and [OAuthRedirectActivity] to catch the redirect.
 *
 * A Custom Tab gives no signal when the user closes it without signing in;
 * [OAuthRedirectBridge.onHostResumed] detects that case, and [AUTH_TIMEOUT_MS]
 * is only the backstop.
 */
object OAuthClient {
    private const val AUTH_TIMEOUT_MS = 5 * 60_000L

    suspend fun authorize(context: Context, provider: CloudProvider): OAuthToken {
        requireConfigured(provider)
        val verifier = PkceUtil.makeVerifier()
        val challenge = PkceUtil.challenge(verifier)
        val state = UUID.randomUUID().toString()

        val authUri = Uri.parse(provider.authUrl).buildUpon()
            .appendQueryParameter("client_id", provider.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", provider.redirectUri)
            .appendQueryParameter("scope", provider.scopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val deferred = OAuthRedirectBridge.begin()
        val customTabsIntent = CustomTabsIntent.Builder().build()
        // From an Activity the tab opens on top of the app's own task; only a
        // non-activity context needs its own task.
        if (context !is android.app.Activity) customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            customTabsIntent.launchUrl(context, authUri)
        } catch (e: android.content.ActivityNotFoundException) {
            OAuthRedirectBridge.cancel()
            throw OAuthException("No web browser is available to sign in with.")
        }

        val redirect = try {
            withTimeout(AUTH_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            OAuthRedirectBridge.cancel()
            throw OAuthException("Sign-in timed out.")
        }
        redirect.getQueryParameter("error")?.let { error ->
            if (error == "access_denied") throw OAuthCancelledException()
            throw OAuthException(redirect.getQueryParameter("error_description") ?: error)
        }

        if (redirect.getQueryParameter("state") != state) {
            throw OAuthException("Authorization state mismatch — please try again.")
        }
        val code = redirect.getQueryParameter("code")
            ?: throw OAuthException(redirect.getQueryParameter("error_description") ?: "No authorization code returned.")

        return exchangeCode(provider, code, verifier)
    }

    suspend fun exchangeCode(provider: CloudProvider, code: String, verifier: String): OAuthToken =
        withContext(Dispatchers.IO) {
            postForm(
                provider.tokenUrl,
                formBody(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "client_id" to provider.clientId,
                    "redirect_uri" to provider.redirectUri,
                    "code_verifier" to verifier,
                ),
            )
        }

    suspend fun refresh(provider: CloudProvider, token: OAuthToken): OAuthToken = withContext(Dispatchers.IO) {
        val refreshToken = token.refreshToken ?: throw OAuthException("No refresh token — please sign in again.")
        postForm(
            provider.tokenUrl,
            formBody(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to provider.clientId,
            ),
        )
    }

    private fun requireConfigured(provider: CloudProvider) {
        if (provider.clientId.isBlank() || provider.clientId.startsWith("YOUR_")) {
            throw OAuthException("${provider.label} isn't configured yet — add your client ID in CloudConfig.kt.")
        }
    }

    private fun formBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }

    private fun postForm(url: String, body: String): OAuthToken {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw OAuthException("Auth server returned $status: $text")

            val json = JSONObject(text)
            val accessToken = json.optString("access_token")
            if (accessToken.isBlank()) throw OAuthException("Unexpected response from auth server.")
            val expiresIn = json.optLong("expires_in", 3600L)
            return OAuthToken(
                accessToken = accessToken,
                refreshToken = json.optString("refresh_token").ifBlank { null },
                expiresAtEpochMillis = System.currentTimeMillis() + expiresIn * 1000,
            )
        } finally {
            connection.disconnect()
        }
    }
}
