package filehub.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import filehub.android.FileHubApplication
import filehub.android.R

enum class MainTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Servers(R.string.tab_servers, Icons.Filled.Home),
    Browse(R.string.tab_browse, Icons.AutoMirrored.Filled.List),
    My(R.string.tab_my, Icons.Filled.Settings),
}

@Composable
fun FileHubApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as FileHubApplication
    val filesViewModel: FilesViewModel = viewModel(factory = FilesViewModel.factory(application))
    var tab by rememberSaveable { mutableStateOf(MainTab.Servers) }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(session.error) {
        val message = session.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearSessionError()
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when (tab) {
                        MainTab.Servers -> ServersScreen(
                            state = session,
                            onAddServer = { navController.navigate("addServer") },
                            onSelect = { id ->
                                viewModel.selectProfile(id)
                                tab = MainTab.Browse
                            },
                            onRemove = viewModel::removeProfile,
                        )
                        MainTab.Browse -> BrowseScreen(
                            state = session,
                            filesViewModel = filesViewModel,
                            onAddServer = { navController.navigate("addServer") },
                            onRetry = viewModel::refreshRoots,
                        )
                        MainTab.My -> MyScreen(
                            state = session,
                            onLogout = viewModel::logout,
                            onRemove = { session.active?.let { viewModel.removeProfile(it.id) } },
                            onRefresh = viewModel::refreshRoots,
                        )
                    }
                }
            }
        }
        composable("addServer") {
            AddServerScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.resetLogin()
                    navController.popBackStack()
                },
                onDone = {
                    viewModel.resetLogin()
                    tab = MainTab.Browse
                    navController.popBackStack()
                },
            )
        }
    }
}
