package com.qbitcore.booklip.data.cloud

// MARK: - Cloud Service Configuration
// Fill these in after registering your apps. Package name for every
// registration below is com.qbitcore.booklip; the redirect scheme "booklip"
// is registered as an intent-filter on OAuthRedirectActivity (AndroidManifest).
//
// Dropbox (the easiest — a custom URL scheme is not OS-specific, so the SAME
// Dropbox app the iOS build uses can serve Android too):
//   1. https://www.dropbox.com/developers/apps → your app → Settings
//   2. OAuth 2 → Redirect URIs → add: booklip://auth/dropbox
//      (if the iOS app already registered exactly this URI, nothing else to do)
//   3. Copy the App key below
//
// Google Drive:
//   1. https://console.cloud.google.com → your project → Credentials
//   2. Create an OAuth client — "Android" type needs your app's SHA-1
//      (./gradlew signingReport) + package name; a "Desktop app" type is
//      simpler and works with the custom-scheme redirect below, at the cost
//      of a less precise audience restriction.
//   3. OAuth consent screen → scope: https://www.googleapis.com/auth/drive.readonly
//   4. The iOS client ID (an "iOS" type) is NOT reusable here — Google
//      validates the redirect_uri against what that specific client type
//      registered, and iOS clients don't carry an Android-compatible one.
//      Register a new client and put its ID + matching redirect below.
//
// Microsoft OneDrive:
//   1. https://portal.azure.com → App registrations → (the same app the iOS
//      build uses is fine) → Authentication → add platform → Android, or
//      "Mobile and desktop applications" → add redirect: booklip://auth/onedrive
//   2. API permissions → Microsoft Graph → Files.Read, offline_access
//   3. The existing Application (client) ID can be reused once the Android
//      redirect above is added to it.

object CloudConfig {
    const val DROPBOX_CLIENT_ID = "YOUR_DROPBOX_APP_KEY"
    const val DROPBOX_REDIRECT_URI = "booklip://auth/dropbox"

    const val GOOGLE_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
    const val GOOGLE_REDIRECT_URI = "booklip://auth/google"

    const val ONEDRIVE_CLIENT_ID = "YOUR_ONEDRIVE_APPLICATION_ID"
    const val ONEDRIVE_REDIRECT_URI = "booklip://auth/onedrive"
}
