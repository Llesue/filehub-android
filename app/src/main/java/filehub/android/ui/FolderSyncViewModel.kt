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
import filehub.android.data.sync.FolderSyncEntryEntity
import filehub.android.data.SessionRepository
import filehub.android.data.sync.FolderSyncRepository
import filehub.android.data.sync.SyncLogEntity
import filehub.android.data.sync.SyncRunEntity
import filehub.android.data.sync.SyncRunKind
import filehub.android.data.sync.SyncTargetOverlapException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderSyncUiState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val pending: Int = 0,
    val conflicts: Int = 0,
    val queued: Boolean = false,
    val overlap: Boolean = false,
)

class FolderSyncViewModel(
    private val application: Application,
    private val repository: FolderSyncRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val config: StateFlow<FolderSyncConfigEntity?> = sessionRepository.state
        .map { it.active?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.configFlow(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _state = MutableStateFlow(FolderSyncUiState())
    val state: StateFlow<FolderSyncUiState> = _state.asStateFlow()

    private val _conflicts = MutableStateFlow<List<FolderSyncEntryEntity>>(emptyList())
    val conflicts: StateFlow<List<FolderSyncEntryEntity>> = _conflicts.asStateFlow()

    val logs: StateFlow<List<SyncLogEntity>> = sessionRepository.state
        .map { it.active?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                repository.logs.flow(id, SyncRunKind.FOLDER)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val run: StateFlow<SyncRunEntity?> = sessionRepository.state
        .map { it.active?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.runs.flow(id, SyncRunKind.FOLDER)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun refreshStats() {
        val id = sessionRepository.state.value.active?.id ?: return
        viewModelScope.launch {
            val pending = repository.pendingCount(id)
            val conflicts = repository.conflictCount(id)
            _state.value = _state.value.copy(pending = pending, conflicts = conflicts)
        }
    }

    fun loadConflicts() {
        val id = sessionRepository.state.value.active?.id ?: return
        viewModelScope.launch {
            _conflicts.value = repository.entries(id).filter { it.status == "conflict" }
        }
    }

    fun resolveConflict(entry: FolderSyncEntryEntity, keepLocal: Boolean) {
        val id = sessionRepository.state.value.active?.id ?: return
        viewModelScope.launch {
            repository.resolveConflict(id, entry, keepLocal)
                .onSuccess {
                    _conflicts.value = repository.entries(id).filter { it.status == "conflict" }
                    refreshStats()
                    repository.syncNow(id)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        error = error.message
                            ?: application.getString(R.string.folder_sync_conflict_delete_failed),
                    )
                }
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
        allowOverlap: Boolean = false,
    ) {
        if (_state.value.saving) return
        if (localTreeUri.isBlank()) {
            _state.value = _state.value.copy(error = application.getString(R.string.folder_sync_no_folder))
            return
        }
        _state.value = _state.value.copy(
            saving = true,
            error = null,
            saved = false,
            queued = false,
            overlap = false,
        )
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
                    allowOverlap = allowOverlap,
                )
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
                refreshStats()
            }.onFailure { error ->
                if (error is SyncTargetOverlapException) {
                    _state.value = _state.value.copy(saving = false, overlap = true)
                } else {
                    _state.value = _state.value.copy(
                        saving = false,
                        error = error.message
                            ?: application.getString(R.string.folder_sync_save_failed),
                    )
                }
            }
        }
    }

    fun clearLogs() {
        sessionRepository.state.value.active?.id?.let { id ->
            viewModelScope.launch { repository.logs.clear(id, SyncRunKind.FOLDER) }
        }
    }

    fun dismissOverlap() {
        _state.value = _state.value.copy(overlap = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun requestStop(cancel: Boolean) {
        sessionRepository.state.value.active?.id?.let { id ->
            viewModelScope.launch { repository.requestStop(id, cancel) }
        }
    }

    fun syncNow() {
        sessionRepository.state.value.active?.id?.let { id ->
            viewModelScope.launch {
                repository.syncNow(id)
                _state.update { it.copy(queued = true) }
            }
        }
    }

    fun fullSync() {
        sessionRepository.state.value.active?.id?.let { id ->
            viewModelScope.launch {
                repository.syncNow(id, fullScan = true)
                _state.update { it.copy(queued = true) }
            }
        }
    }

    fun clearQueued() {
        _state.update { it.copy(queued = false) }
    }

    fun setEnabled(enabled: Boolean) {
        sessionRepository.state.value.active?.id?.let { id ->
            viewModelScope.launch { repository.setEnabled(id, enabled) }
        }
    }

    fun resetSaved() {
        _state.value = _state.value.copy(saved = false)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                FolderSyncViewModel(app, app.container.folderSyncRepository, app.container.sessionRepository)
            }
        }
    }
}
