package com.qbitcore.booklip.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.data.cloud.CloudFile
import com.qbitcore.booklip.data.cloud.CloudProvider
import com.qbitcore.booklip.data.cloud.CloudRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One entry in the folder breadcrumb; id null means the provider's root. */
data class CloudFolderCrumb(val id: String?, val name: String)

data class CloudUiState(
    val connected: Map<CloudProvider, Boolean> = emptyMap(),
    val activeProvider: CloudProvider? = null,
    val folderStack: List<CloudFolderCrumb> = emptyList(),
    val files: List<CloudFile> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
)

class CloudViewModel(private val repository: CloudRepository) : ViewModel() {
    private val activeProvider = MutableStateFlow<CloudProvider?>(null)
    private val folderStack = MutableStateFlow<List<CloudFolderCrumb>>(emptyList())
    private val files = MutableStateFlow<List<CloudFile>>(emptyList())
    private val isLoading = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val connected: StateFlow<Map<CloudProvider, Boolean>> = combine(
        repository.tokenFlow(CloudProvider.DROPBOX),
        repository.tokenFlow(CloudProvider.GOOGLE_DRIVE),
        repository.tokenFlow(CloudProvider.ONE_DRIVE),
    ) { dropbox, google, oneDrive ->
        mapOf(
            CloudProvider.DROPBOX to (dropbox != null),
            CloudProvider.GOOGLE_DRIVE to (google != null),
            CloudProvider.ONE_DRIVE to (oneDrive != null),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<CloudUiState> = combine(
        connected, activeProvider, folderStack, files,
    ) { connectedMap, provider, stack, fileList ->
        CloudUiState(connected = connectedMap, activeProvider = provider, folderStack = stack, files = fileList)
    }.combine(isLoading) { state, loading -> state.copy(isLoading = loading) }
        .combine(isImporting) { state, importing -> state.copy(isImporting = importing) }
        .combine(errorMessage) { state, error -> state.copy(errorMessage = error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CloudUiState())

    fun connect(provider: CloudProvider) {
        viewModelScope.launch {
            runCatching { repository.connect(provider) }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun disconnect(provider: CloudProvider) {
        viewModelScope.launch { repository.disconnect(provider) }
    }

    fun openProvider(provider: CloudProvider) {
        activeProvider.value = provider
        folderStack.value = listOf(CloudFolderCrumb(id = null, name = provider.label))
        loadCurrentFolder()
    }

    fun openFolder(file: CloudFile) {
        folderStack.value = folderStack.value + CloudFolderCrumb(id = file.id, name = file.name)
        loadCurrentFolder()
    }

    /** Pops one level; returns false when already at the provider root (caller should exit browsing). */
    fun goBack(): Boolean {
        if (folderStack.value.size <= 1) {
            activeProvider.value = null
            folderStack.value = emptyList()
            files.value = emptyList()
            return false
        }
        folderStack.value = folderStack.value.dropLast(1)
        loadCurrentFolder()
        return true
    }

    private fun loadCurrentFolder() {
        val provider = activeProvider.value ?: return
        val folderId = folderStack.value.lastOrNull()?.id
        isLoading.value = true
        viewModelScope.launch {
            val result = runCatching { repository.listFolder(provider, folderId) }
            result.onSuccess { files.value = it }.onFailure { errorMessage.value = it.message }
            isLoading.value = false
        }
    }

    fun importFile(file: CloudFile, folderId: String?, onImported: () -> Unit) {
        val provider = activeProvider.value ?: return
        isImporting.value = true
        viewModelScope.launch {
            runCatching { repository.downloadAndImport(provider, file, folderId) }
                .onSuccess { onImported() }
                .onFailure { errorMessage.value = it.message }
            isImporting.value = false
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}

class CloudViewModelFactory(private val repository: CloudRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CloudViewModel(repository) as T
}
