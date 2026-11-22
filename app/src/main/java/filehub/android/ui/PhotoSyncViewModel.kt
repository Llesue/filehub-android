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
import filehub.android.data.sync.PhotoSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoSyncUiState(
    val buckets: List<MediaBucket> = emptyList(),
    val loadingBuckets: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class PhotoSyncViewModel(
    private val application: Application,
    private val repository: PhotoSyncRepository,
) : ViewModel() {

    val config: StateFlow<PhotoSyncConfigEntity?> = repository.config.stateIn(
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
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        albumSubfolders: Boolean,
        schedule: String,
    ) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null, saved = false)
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
                        wifiOnly = wifiOnly,
                        chargingOnly = chargingOnly,
                        albumSubfolders = albumSubfolders,
                        schedule = schedule,
                    ),
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
                PhotoSyncViewModel(app, app.container.photoSyncRepository)
            }
        }
    }
}
