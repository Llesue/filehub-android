package filehub.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.transfer.TransferStatus

enum class MainTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Servers(R.string.tab_servers, Icons.Filled.Home),
    Browse(R.string.tab_browse, Icons.AutoMirrored.Filled.List),
    My(R.string.tab_my, Icons.Filled.Settings),
}

@Composable
private fun TransferPill(
    name: String,
    progress: Float,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun FileHubApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as FileHubApplication
    val filesViewModel: FilesViewModel = viewModel(factory = FilesViewModel.factory(application))
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(application))
    val transferViewModel: TransferViewModel = viewModel(factory = TransferViewModel.factory(application))
    val photoSyncViewModel: PhotoSyncViewModel = viewModel(factory = PhotoSyncViewModel.factory(application))
    val folderSyncViewModel: FolderSyncViewModel = viewModel(factory = FolderSyncViewModel.factory(application))
    var tab by rememberSaveable { mutableStateOf(MainTab.Servers) }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val transfers by transferViewModel.items.collectAsStateWithLifecycle()
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
                    Column {
                        transfers.firstOrNull {
                            it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING
                        }?.let { active ->
                            TransferPill(
                                name = active.name,
                                progress = active.progress,
                                onClick = { navController.navigate("transfers") },
                            )
                        }
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
                            onSearch = { rootId -> navController.navigate("search/${rootId ?: -1}") },
                        )
                        MainTab.My -> MyScreen(
                            state = session,
                            onLogout = viewModel::logout,
                            onRemove = { session.active?.let { viewModel.removeProfile(it.id) } },
                            onRefresh = viewModel::refreshRoots,
                            onTransfers = { navController.navigate("transfers") },
                            onPhotoSync = { navController.navigate("photoSync") },
                            onFolderSync = { navController.navigate("folderSync") },
                        )
                    }
                }
            }
        }
        composable("search/{rootId}") { entry ->
            val rootId = entry.arguments?.getString("rootId")?.toLongOrNull()?.takeIf { it >= 0 }
            val scopeAlias = rootId?.let { id -> session.roots.firstOrNull { it.id == id }?.alias }
            SearchScreen(
                viewModel = searchViewModel,
                scopeRootId = rootId,
                scopeRootAlias = scopeAlias,
                rootAliases = session.roots.associate { it.id to it.alias },
                onBack = {
                    searchViewModel.reset()
                    navController.popBackStack()
                },
                onOpenResult = { result ->
                    val active = session.active
                    val root = session.roots.firstOrNull { it.id == result.rootId }
                    if (active != null && root != null) {
                        if (result.isDir) {
                            filesViewModel.open(active, root, result.path)
                        } else {
                            val parent = result.path.substringBeforeLast('/', "")
                            filesViewModel.open(active, root, parent)
                            filesViewModel.showPreview(result.toEntry())
                        }
                        tab = MainTab.Browse
                    }
                    searchViewModel.reset()
                    navController.popBackStack()
                },
            )
        }
        composable("folderSync") {
            FolderSyncScreen(
                viewModel = folderSyncViewModel,
                session = session,
                onBack = { navController.popBackStack() },
            )
        }
        composable("photoSync") {
            PhotoSyncScreen(
                viewModel = photoSyncViewModel,
                session = session,
                onBack = { navController.popBackStack() },
            )
        }
        composable("transfers") {
            TransfersScreen(
                viewModel = transferViewModel,
                onBack = { navController.popBackStack() },
            )
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
