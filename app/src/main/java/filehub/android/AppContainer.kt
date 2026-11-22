package filehub.android

import android.content.Context
import filehub.android.data.FilesRepository
import filehub.android.data.SessionRepository
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.TokenVault
import filehub.android.data.media.MediaPlayerFactory
import filehub.android.data.media.PreviewDownloader
import filehub.android.data.media.ThumbnailLoaderFactory

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val profileStore: ProfileStore = ProfileStore(appContext)
    val tokenVault: TokenVault = TokenVault()
    val sessionRepository: SessionRepository = SessionRepository(profileStore, tokenVault)
    val filesRepository: FilesRepository = FilesRepository(sessionRepository)
    val thumbnailLoaderFactory: ThumbnailLoaderFactory = ThumbnailLoaderFactory(appContext)
    val mediaPlayerFactory: MediaPlayerFactory = MediaPlayerFactory(appContext)
    val previewDownloader: PreviewDownloader = PreviewDownloader(appContext.cacheDir)
}
