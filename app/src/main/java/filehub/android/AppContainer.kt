package filehub.android

import android.content.Context
import filehub.android.data.ClientUpdateChecker
import filehub.android.data.DirectoryCacheRepository
import filehub.android.data.BrowserStateRepository
import filehub.android.data.FilesRepository
import filehub.android.data.ShareRepository
import filehub.android.data.SessionRepository
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.SettingsStore
import filehub.android.data.local.TokenVault
import filehub.android.data.media.MediaPlayerFactory
import filehub.android.data.media.PreviewDownloader
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.playback.AudioPlayerController
import filehub.android.data.transfer.TransferRepository
import filehub.android.data.transfer.TransferStore
import androidx.room.Room
import filehub.android.data.sync.FolderSyncRepository
import filehub.android.data.sync.PhotoSyncRepository
import filehub.android.data.sync.SyncDatabase
import filehub.android.core.model.ServerProfile

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val profileStore: ProfileStore = ProfileStore(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)
    val tokenVault: TokenVault = TokenVault()
    val sessionRepository: SessionRepository = SessionRepository(appContext, profileStore, tokenVault)
    val filesRepository: FilesRepository = FilesRepository(appContext, sessionRepository)
    val shareRepository: ShareRepository = ShareRepository(appContext, sessionRepository)
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
        )
        .fallbackToDestructiveMigration()
        .build()
    val directoryCacheRepository: DirectoryCacheRepository = DirectoryCacheRepository(syncDatabase.directoryCacheDao())
    val clientUpdateChecker: ClientUpdateChecker = ClientUpdateChecker(appContext)
    val browserStateRepository: BrowserStateRepository = BrowserStateRepository(syncDatabase.browserStateDao())
    val photoSyncRepository: PhotoSyncRepository = PhotoSyncRepository(appContext, syncDatabase.photoSyncDao())
    val folderSyncRepository: FolderSyncRepository = FolderSyncRepository(appContext, syncDatabase.folderSyncDao())

    suspend fun profileAndToken(profileId: String): Pair<ServerProfile, String>? {
        val profile = profileStore.read().profiles.firstOrNull { it.id == profileId } ?: return null
        val token = profileStore.readToken(profileId)?.let(tokenVault::decrypt) ?: return null
        return profile to token
    }
}
