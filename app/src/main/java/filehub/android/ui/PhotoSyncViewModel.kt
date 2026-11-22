package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.core.model.FileRoot
import filehub.android.core.model.ServerProfile
import filehub.android.data.sync.MediaBucket
import filehub.android.data.sync.MediaScanner
import filehub.android.data.sync.PhotoSyncConfigEntity
import filehub.android.data.SessionRepository
import filehub.android.data.sync.PhotoSyncRepository
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

data class PhotoSyncUiState(
    val buckets: List<MediaBucket> = emptyList(),
    val loadingBuckets: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val queued: Boolean = false,
)

class PhotoSyncViewModel(
    private val application: Application,
    private val repository: PhotoSyncRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val config: StateFlow<PhotoSyncConfigEntity?> = sessionRepository.state
        .map { it.active?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.configFlow(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _state = MutableStateFlow(PhotoSyncUiState())
    val state: StateFlow<PhotoSyncUiState> = _state.asStateFlow()

    fun refreshBuckets(includeVideos: Boolean) {
        if (_state.value.loadingBuckets) return
        _state.value = _state.value.copy(loadingBuckets = true, error = null)
        viewModelScope.launch {
            val buckets = withContext(Dispatchers.IO) {
                MediaScanner.buckets(application, includeVideos)
            }
            _state.value = _state.value.copy(buckets = buckets, loadingBuckets = false)
        }
    }

    fun save(
        profile: ServerProfile,
        root: FileRoot,
        remotePath: String,
        albums: Set<Long>?,
        includeVideos: Boolean,
        photosWifiOnly: Boolean,
        videosWifiOnly: Boolean,
        chargingOnly: Boolean,
        albumSubfolders: Boolean,
        schedule: String,
        resetEntries: Boolean = false,
    ) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null, saved = false, queued = false)
        viewModelScope.launch {
            runCatching {
                repository.save(
                    PhotoSyncConfigEntity(
                        enabled = true,
                        profileId = profile.id,
                        serverName = profile.displayName,
                        rootId = root.id,
                        rootAlias = root.alias,
                        remotePath = remotePath.trim('/'),
                        albumsJson = repository.encodeAlbums(albums),
                        includeVideos = includeVideos,
                        wifiOnly = photosWifiOnly && videosWifiOnly,
                        photosWifiOnly = photosWifiOnly,
                        videosWifiOnly = videosWifiOnly,
                        chargingOnly = chargingOnly,
                        albumSubfolders = albumSubfolders,
                        schedule = schedule,
                    ),
                    resetEntries = resetEntries,
                )
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = error.message ?: application.getString(filehub.android.R.string.photo_sync_save_failed),
                )
            }
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
                PhotoSyncViewModel(app, app.container.photoSyncRepository, app.container.sessionRepository)
            }
        }
    }
}
