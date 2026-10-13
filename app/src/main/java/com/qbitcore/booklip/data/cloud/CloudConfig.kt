package com.qbitcore.booklip.data.cloud

// MARK: - Cloud Service Configuration
//
// All three flows are OAuth 2.0 authorization-code + PKCE with a custom-scheme
// redirect, caught by OAuthRedirectActivity. Every redirect scheme used here
// has an intent-filter in AndroidManifest.xml — change one, change the other.
//
// Dropbox — same app key as the iOS app; the Android redirect
//   booklip://auth/dropbox/android is registered next to the iOS one.
//
// OneDrive — same Azure app registration as iOS, with an "Android" platform
//   added. Azure's Android redirect is msauth://<package>/<signature hash>,
//   where the hash is the Base64 SHA-1 of the signing certificate
//   (URL-encoded here, exactly as Azure lists it). This app only needs the
//   string to match what is registered — it does not use MSAL's broker — so
//   the same redirect works for debug and release builds alike.
//
// Google Drive — the "Android" OAuth client of the same Google Cloud project.
//   The redirect is the client ID reversed, so the scheme below (and in the
//   manifest) must always be derived from GOOGLE_CLIENT_ID. Custom-scheme
//   redirects are off by default for Android clients: enable "Custom URI
//   scheme" under the client's advanced settings in the Google Cloud console.
object CloudConfig {
    const val DROPBOX_CLIENT_ID = "btx9o6htbdzt7uo"
    const val DROPBOX_REDIRECT_URI = "booklip://auth/dropbox/android"

    const val GOOGLE_CLIENT_ID = "157712219209-8819d5dhcqlu5k0i5n1miji33l1tnirt.apps.googleusercontent.com"
    const val GOOGLE_REDIRECT_SCHEME = "com.googleusercontent.apps.157712219209-8819d5dhcqlu5k0i5n1miji33l1tnirt"
    const val GOOGLE_REDIRECT_URI = "$GOOGLE_REDIRECT_SCHEME:/oauth2redirect"

    const val ONEDRIVE_CLIENT_ID = "c7773f3f-0038-432b-aac5-e283a03fc09a"
    const val ONEDRIVE_REDIRECT_URI = "msauth://com.qbitcore.booklip/gYfucgrOlZ3FLWgYctqk1bCxZbo%3D"
}
