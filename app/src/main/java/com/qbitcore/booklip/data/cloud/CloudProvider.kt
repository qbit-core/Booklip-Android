package com.qbitcore.booklip.data.cloud

enum class CloudProvider(
    val label: String,
    val authUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
) {
    DROPBOX(
        label = "Dropbox",
        authUrl = "https://www.dropbox.com/oauth2/authorize?token_access_type=offline",
        tokenUrl = "https://api.dropboxapi.com/oauth2/token",
        clientId = CloudConfig.DROPBOX_CLIENT_ID,
        redirectUri = CloudConfig.DROPBOX_REDIRECT_URI,
        scopes = listOf("files.metadata.read", "files.content.read"),
    ),
    GOOGLE_DRIVE(
        label = "Google Drive",
        authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        clientId = CloudConfig.GOOGLE_CLIENT_ID,
        redirectUri = CloudConfig.GOOGLE_REDIRECT_URI,
        scopes = listOf("https://www.googleapis.com/auth/drive.readonly"),
    ),
    ONE_DRIVE(
        label = "OneDrive",
        authUrl = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
        tokenUrl = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
        clientId = CloudConfig.ONEDRIVE_CLIENT_ID,
        redirectUri = CloudConfig.ONEDRIVE_REDIRECT_URI,
        scopes = listOf("Files.Read", "offline_access"),
    ),
}
