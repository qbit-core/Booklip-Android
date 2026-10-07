package com.qbitcore.booklip.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.data.cloud.CloudProvider

private val CloudProvider.tint: Color
    get() = when (this) {
        CloudProvider.DROPBOX -> Color(0xFF5856D6)
        CloudProvider.ONE_DRIVE -> Color(0xFF007AFF)
        CloudProvider.GOOGLE_DRIVE -> Color(0xFFFF3B30)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudConnectScreen(viewModel: CloudViewModel, onClose: () -> Unit, onBrowse: (CloudProvider) -> Unit) {
    val connected by viewModel.connected.collectAsState()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Storage") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Same order as the iOS app.
            listOf(CloudProvider.DROPBOX, CloudProvider.ONE_DRIVE, CloudProvider.GOOGLE_DRIVE).forEach { provider ->
                val isConnected = connected[provider] == true
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, tint = provider.tint, modifier = Modifier.size(30.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(provider.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (isConnected) "Connected" else "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isConnected) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (viewModel.connecting == provider) {
                                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Button(
                                    onClick = { if (isConnected) onBrowse(provider) else viewModel.connect(provider, context) },
                                    enabled = viewModel.connecting == null,
                                ) { Text(if (isConnected) "Browse" else "Connect") }
                            }
                        }
                        viewModel.connectErrors[provider]?.let { error ->
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        // On its own line so it never crowds the primary button.
                        if (isConnected) {
                            TextButton(onClick = { viewModel.disconnect(provider) }, modifier = Modifier.align(Alignment.End)) {
                                Text("Sign Out", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Text(
                "Browse a connected service and tap any supported book (.txt .epub .pdf .md) to import it. " +
                    "Use Select to pick several files, or whole folders — a folder is imported as a library folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}
