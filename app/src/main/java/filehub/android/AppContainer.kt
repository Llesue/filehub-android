package filehub.android

import android.content.Context
import filehub.android.data.SessionRepository
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.TokenVault

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val profileStore: ProfileStore = ProfileStore(appContext)
    val tokenVault: TokenVault = TokenVault()
    val sessionRepository: SessionRepository = SessionRepository(profileStore, tokenVault)
}
