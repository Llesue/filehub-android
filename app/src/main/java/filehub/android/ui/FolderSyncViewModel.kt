package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.core.model.FileRoot
import filehub.android.core.model.ServerProfile
import filehub.android.data.sync.FolderSyncConfigEntity
import filehub.android.data.sync.FolderSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderSyncUiState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val pending: Int = 0,
    val conflicts: Int = 0,
)

class FolderSyncViewModel(
    private val application: Application,
    private val repository: FolderSyncRepository,
) : ViewModel() {

    val config: StateFlow<FolderSyncConfigEntity?> = repository.config.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _state = MutableStateFlow(FolderSyncUiState())
    val state: StateFlow<FolderSyncUiState> = _state.asStateFlow()

    fun refreshStats() {
        viewModelScope.launch {
            val pending = repository.pendingCount()
            val conflicts = repository.conflictCount()
            _state.value = _state.value.copy(pending = pending, conflicts = conflicts)
        }
    }

    fun save(
        profile: ServerProfile,
        root: FileRoot,
        remotePath: String,
        localTreeUri: String,
        localName: String,
        mode: String,
        deleteSync: Boolean,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        schedule: String,
    ) {
        if (_state.value.saving) return
        if (localTreeUri.isBlank()) {
            _state.value = _state.value.copy(error = application.getString(R.string.folder_sync_no_folder))
            return
        }
        _state.value = _state.value.copy(saving = true, error = null, saved = false)
        viewModelScope.launch {
            runCatching {
                repository.save(
                    FolderSyncConfigEntity(
                        enabled = true,
                        profileId = profile.id,
                        serverName = profile.displayName,
                        rootId = root.id,
                        rootAlias = root.alias,
                        remotePath = remotePath.trim('/'),
                        localTreeUri = localTreeUri,
                        localName = localName,
                        mode = mode,
                        deleteSync = deleteSync,
                        wifiOnly = wifiOnly,
                        chargingOnly = chargingOnly,
                        schedule = schedule,
                    ),
                )
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
                refreshStats()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = error.message ?: application.getString(R.string.folder_sync_save_failed),
                )
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun resetSaved() {
        _state.value = _state.value.copy(saved = false)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                FolderSyncViewModel(app, app.container.folderSyncRepository)
            }
        }
    }
}
