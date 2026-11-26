package filehub.android

import android.content.Context
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import filehub.android.core.model.ServerProfile
import filehub.android.data.BrowserStateRepository
import filehub.android.data.ClientUpdateChecker
import filehub.android.data.DirectoryCacheRepository
import filehub.android.data.FilesRepository
import filehub.android.data.SessionRepository
import filehub.android.data.ShareRepository
import filehub.android.data.events.EventStreamClient
import filehub.android.data.events.StreamTarget
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.SettingsStore
import filehub.android.data.local.TokenVault
import filehub.android.data.media.MediaPlayerFactory
import filehub.android.data.media.PreviewDownloader
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.playback.AudioPlayerController
import filehub.android.data.sync.FolderSyncRepository
import filehub.android.data.sync.PhotoSyncRepository
import filehub.android.data.sync.SyncDatabase
import filehub.android.data.sync.SyncTargetGuard
import filehub.android.data.transfer.TransferRepository
import filehub.android.data.transfer.TransferStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val profileStore: ProfileStore = ProfileStore(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val tokenVault: TokenVault = TokenVault()
    val sessionRepository: SessionRepository = SessionRepository(appContext, profileStore, tokenVault)
    val filesRepository: FilesRepository = FilesRepository(appContext, sessionRepository)
    val shareRepository: ShareRepository = ShareRepository(appContext, sessionRepository)
    val eventStreamClient: EventStreamClient = EventStreamClient(
        scope = appScope,
        onUnauthorized = { sessionRepository.reportUnauthorized() },
        log = { message -> if (BuildConfig.DEBUG) Log.d("FileHubSse", message) },
    )
    val thumbnailLoaderFactory: ThumbnailLoaderFactory = ThumbnailLoaderFactory(appContext)
    val mediaPlayerFactory: MediaPlayerFactory = MediaPlayerFactory(appContext)
    val previewDownloader: PreviewDownloader = PreviewDownloader(appContext.cacheDir)
    val transferStore: TransferStore = TransferStore(appContext)
    val transferRepository: TransferRepository = TransferRepository(appContext, transferStore)
    val audioPlayerController: AudioPlayerController = AudioPlayerController(appContext, sessionRepository)
    val syncDatabase: SyncDatabase = Room.databaseBuilder(appContext, SyncDatabase::class.java, "filehub-sync.db")
        .addMigrations(
            SyncDatabase.MIGRATION_3_4,
            SyncDatabase.MIGRATION_4_5,
            SyncDatabase.MIGRATION_5_6,
            SyncDatabase.MIGRATION_6_7,
            SyncDatabase.MIGRATION_7_8,
            SyncDatabase.MIGRATION_8_9,
            SyncDatabase.MIGRATION_9_10,
            SyncDatabase.MIGRATION_10_11,
            SyncDatabase.MIGRATION_11_12,
            SyncDatabase.MIGRATION_12_13,
        )
        .fallbackToDestructiveMigration()
        .build()
    val directoryCacheRepository: DirectoryCacheRepository = DirectoryCacheRepository(syncDatabase.directoryCacheDao())
    val clientUpdateChecker: ClientUpdateChecker = ClientUpdateChecker(appContext)
    val browserStateRepository: BrowserStateRepository = BrowserStateRepository(syncDatabase.browserStateDao())
    private val syncTargetGuard: SyncTargetGuard = SyncTargetGuard(
        syncDatabase.photoSyncDao(),
        syncDatabase.folderSyncDao(),
    )
    val photoSyncRepository: PhotoSyncRepository = PhotoSyncRepository(
        appContext,
        syncDatabase.photoSyncDao(),
        syncDatabase.syncRunDao(),
        syncDatabase.syncLogDao(),
        syncTargetGuard,
    )
    val folderSyncRepository: FolderSyncRepository = FolderSyncRepository(
        appContext,
        syncDatabase.folderSyncDao(),
        syncDatabase.syncRunDao(),
        syncDatabase.syncLogDao(),
        syncTargetGuard,
    )

    init {
        appScope.launch {
            sessionRepository.state.collect { state ->
                val active = state.active
                val token = active?.let { sessionRepository.tokenFor(it) }
                eventStreamClient.updateTarget(
                    if (active != null && token != null) {
                        StreamTarget(active.id, active.baseUrl, token)
                    } else {
                        null
                    },
                )
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(eventStreamClient)
    }

    suspend fun profileAndToken(profileId: String): Pair<ServerProfile, String>? {
        val profile = profileStore.read().profiles.firstOrNull { it.id == profileId } ?: return null
        val token = profileStore.readToken(profileId)?.let(tokenVault::decrypt) ?: return null
        return profile to token
    }
}
