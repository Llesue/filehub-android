package filehub.android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.viewModels
import filehub.android.ui.AppViewModel
import filehub.android.data.sync.NotificationDest
import filehub.android.ui.FileHubApp
import filehub.android.ui.NotificationDestination
import filehub.android.ui.NotificationNavigator
import filehub.android.ui.theme.FileHubTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationNavigator.destination.value = intent?.toNotificationDestination()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            FileHubTheme(
                darkTheme = when (theme) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                },
            ) {
                FileHubApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationNavigator.destination.value = intent.toNotificationDestination()
    }

    private fun Intent.toNotificationDestination(): NotificationDestination? {
        val route = getStringExtra(NotificationDest.EXTRA) ?: return null
        return NotificationDestination(
            route = route,
            profileId = getStringExtra(NotificationDest.EXTRA_PROFILE)?.takeIf { it.isNotBlank() },
        )
    }
}
