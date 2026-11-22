package filehub.android.ui

import kotlinx.coroutines.flow.MutableStateFlow

data class NotificationDestination(
    val route: String,
    val profileId: String?,
)

/** Receives notification destinations from MainActivity without owning navigation. */
object NotificationNavigator {
    val destination = MutableStateFlow<NotificationDestination?>(null)
}
