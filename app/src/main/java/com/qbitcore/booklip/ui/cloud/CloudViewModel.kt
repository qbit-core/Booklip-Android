package com.qbitcore.booklip.ui.cloud

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.data.cloud.CloudFile
import com.qbitcore.booklip.data.cloud.CloudProvider
import com.qbitcore.booklip.data.cloud.OAuthCancelledException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One entry in the folder breadcrumb; id null means the provider's root. */
data class CloudFolderCrumb(val id: String?, val name: String)

class CloudViewModel(private val app: BooklipApplication) : ViewModel() {
    private val repository = app.cloudRepository

    val connected: StateFlow<Map<CloudProvider, Boolean>> = combine(
        CloudProvider.entries.map { provider -> repository.tokenFlow(provider) }
    ) { tokens -> CloudProvider.entries.zip(tokens.map { it != null }).toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Per-provider sign-in error, shown under that provider's row. */
    var connectErrors by mutableStateOf<Map<CloudProvider, String>>(emptyMap())
        private set
    var connecting by mutableStateOf<CloudProvider?>(null)
        private set

    // Browser
    var activeProvider by mutableStateOf<CloudProvider?>(null)
        private set
    var folderStack by mutableStateOf<List<CloudFolderCrumb>>(emptyList())
        private set
    var files by mutableStateOf<List<CloudFile>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** Why the current folder could not be listed. */
    var loadError by mutableStateOf<String?>(null)
        private set

    // Selection / import
    var isSelecting by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    /** "Importing 2 of 5…" while an import runs. */
    var importProgress by mutableStateOf<String?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    /** Bumped when an import finished cleanly — the browser closes itself. */
    var importsCompleted by mutableStateOf(0)
        private set

    private var loadJob: Job? = null

    val selectableFiles: List<CloudFile> get() = files.filter { it.isSelectable }
    val allSelected: Boolean get() = selectableFiles.isNotEmpty() && selectableFiles.all { it.id in selected }

    fun connect(provider: CloudProvider, activityContext: Context) {
        if (connecting != null) return
        connecting = provider
        connectErrors = connectErrors - provider
        viewModelScope.launch {
            try {
                repository.connect(provider, activityContext)
            } catch (e: OAuthCancelledException) {
                // Closing the sign-in page is not an error.
            } catch (e: Exception) {
                connectErrors = connectErrors + (provider to (e.message ?: "Sign-in failed."))
            }
            connecting = null
        }
    }

    fun disconnect(provider: CloudProvider) {
        viewModelScope.launch { repository.disconnect(provider) }
    }

    fun openProvider(provider: CloudProvider) {
        activeProvider = provider
        folderStack = listOf(CloudFolderCrumb(id = null, name = provider.label))
        selecting(false)
        loadCurrentFolder()
    }

    fun openFolder(file: CloudFile) {
        folderStack = folderStack + CloudFolderCrumb(id = file.id, name = file.name)
        loadCurrentFolder()
    }

    /** Pops one level; returns false when already at the provider root (the caller leaves the browser). */
    fun goBack(): Boolean {
        if (folderStack.size <= 1) return false
        folderStack = folderStack.dropLast(1)
        loadCurrentFolder()
        return true
    }

    fun reload() = loadCurrentFolder()

    private fun loadCurrentFolder() {
        val provider = activeProvider ?: return
        val folderId = folderStack.lastOrNull()?.id
        loadJob?.cancel()
        isLoading = true
        loadError = null
        files = emptyList()
        selected = emptySet()
        loadJob = viewModelScope.launch {
            try {
                files = repository.listFolder(provider, folderId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.message ?: "This folder could not be loaded."
            }
            isLoading = false
        }
    }

    fun selecting(on: Boolean) {
        isSelecting = on
        if (!on) selected = emptySet()
    }

    fun toggle(file: CloudFile) {
        if (!file.isSelectable) return
        selected = if (file.id in selected) selected - file.id else selected + file.id
    }

    fun toggleSelectAll() {
        selected = if (allSelected) emptySet() else selectableFiles.map { it.id }.toSet()
    }

    fun importSingle(file: CloudFile) = runImport(listOf(file))

    /** Loose files land unfiled; a selected folder becomes a library folder of the same name holding every book in it. */
    fun importSelected() = runImport(selectableFiles.filter { it.id in selected })

    private fun runImport(picked: List<CloudFile>) {
        val provider = activeProvider ?: return
        if (picked.isEmpty() || importProgress != null) return
        importProgress = "Preparing…"
        // On the app scope: a long import keeps going if the user leaves this screen.
        app.appScope.launch {
            val jobs = ArrayList<Pair<CloudFile, String?>>()
            var listingFailures = 0
            for (item in picked) {
                if (!item.isFolder) {
                    jobs += item to null
                    continue
                }
                try {
                    // Expanded first so the count in the progress text is final.
                    jobs += repository.collectBooks(provider, item).map { it to item.name }
                } catch (e: Exception) {
                    listingFailures++
                }
            }
            var failures = 0
            var lastError: String? = null
            for ((index, job) in jobs.withIndex()) {
                importProgress = if (jobs.size == 1) "Importing…" else "Importing ${index + 1} of ${jobs.size}…"
                try {
                    repository.downloadAndImport(provider, job.first, job.second)
                } catch (e: Exception) {
                    failures++
                    lastError = e.message
                }
            }
            importProgress = null
            when {
                failures > 0 || listingFailures > 0 -> importError = buildList {
                    if (failures == 1 && jobs.size == 1) add(lastError ?: "The file could not be imported.")
                    else if (failures > 0) add("$failures of ${jobs.size} file(s) failed to import.")
                    if (listingFailures > 0) add("$listingFailures folder(s) could not be read.")
                }.joinToString(" ")
                jobs.isEmpty() -> importError = "The selected folder(s) contain no supported books."
                else -> {
                    selecting(false)
                    importsCompleted++
                }
            }
        }
    }

    fun clearImportError() {
        importError = null
    }

    class Factory(private val app: BooklipApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CloudViewModel(app) as T
    }
}
