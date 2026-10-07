package com.qbitcore.booklip.data.cloud

// MARK: - Cloud Service Configuration
//
// The client IDs below are the ones the iOS app is registered with
// (iOS: Booklip/Cloud/CloudConfig.swift). All three flows are OAuth 2.0
// authorization-code + PKCE with a custom-scheme redirect, caught by
// OAuthRedirectActivity (see the intent-filters in AndroidManifest.xml — a
// redirect scheme changed here must be changed there too).
//
// Dropbox — the redirect booklip://auth/dropbox is already registered for the
//   iOS app, and a custom-scheme redirect is not platform specific.
//
// OneDrive — same Azure app registration and the same redirect,
//   booklip://auth/onedrive ("Mobile and desktop applications" platform).
//
// Google Drive — this is the iOS-type client, used with its reversed-client-id
//   redirect. Google accepts that redirect from any installed app, so sign-in
//   works, but the consent screen is tied to the iOS registration. Before a
//   Play Store release, create an "Android" OAuth client (package
//   com.qbitcore.booklip + the release SHA-1 from `./gradlew signingReport`)
//   in the same Google Cloud project and put its ID and redirect here.
object CloudConfig {
    const val DROPBOX_CLIENT_ID = "btx9o6htbdzt7uo"
    const val DROPBOX_REDIRECT_URI = "booklip://auth/dropbox"

    const val GOOGLE_CLIENT_ID = "157712219209-5bgl7747tqfi85q082ch6si7gcth7hi8.apps.googleusercontent.com"
    const val GOOGLE_REDIRECT_SCHEME = "com.googleusercontent.apps.157712219209-5bgl7747tqfi85q082ch6si7gcth7hi8"
    const val GOOGLE_REDIRECT_URI = "$GOOGLE_REDIRECT_SCHEME:/oauth2redirect"

    const val ONEDRIVE_CLIENT_ID = "c7773f3f-0038-432b-aac5-e283a03fc09a"
    const val ONEDRIVE_REDIRECT_URI = "booklip://auth/onedrive"
}
