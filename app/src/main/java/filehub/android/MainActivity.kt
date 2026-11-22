package filehub.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.viewModels
import filehub.android.ui.AppViewModel
import filehub.android.ui.FileHubApp
import filehub.android.ui.theme.FileHubTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
}
