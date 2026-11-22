package filehub.android

import android.content.Context
import filehub.android.data.FilesRepository
import filehub.android.data.SessionRepository
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.TokenVault
import filehub.android.data.media.MediaPlayerFactory
import filehub.android.data.media.PreviewDownloader
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.transfer.TransferRepository
import filehub.android.data.transfer.TransferStore
import filehub.android.core.model.ServerProfile

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val profileStore: ProfileStore = ProfileStore(appContext)
    val tokenVault: TokenVault = TokenVault()
    val sessionRepository: SessionRepository = SessionRepository(profileStore, tokenVault)
    val filesRepository: FilesRepository = FilesRepository(sessionRepository)
    val thumbnailLoaderFactory: ThumbnailLoaderFactory = ThumbnailLoaderFactory(appContext)
    val mediaPlayerFactory: MediaPlayerFactory = MediaPlayerFactory(appContext)
    val previewDownloader: PreviewDownloader = PreviewDownloader(appContext.cacheDir)
    val transferStore: TransferStore = TransferStore(appContext)
    val transferRepository: TransferRepository = TransferRepository(appContext, transferStore)

    suspend fun profileAndToken(profileId: String): Pair<ServerProfile, String>? {
        val profile = profileStore.read().profiles.firstOrNull { it.id == profileId } ?: return null
        val token = profileStore.readToken(profileId)?.let(tokenVault::decrypt) ?: return null
        return profile to token
    }
}
