package filehub.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.ServerConnectionStatus
import filehub.android.data.api.PublicLink
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
    val remoteFolderPickerViewModel: RemoteFolderPickerViewModel = viewModel(
        factory = RemoteFolderPickerViewModel.factory(application),
    )
    val shareViewModel: ShareViewModel = viewModel(factory = ShareViewModel.factory(application))
    val audioController = remember { application.container.audioPlayerController }
    val audioState by audioController.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(MainTab.Servers) }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val browserState by filesViewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val notificationDestination by NotificationNavigator.destination.collectAsStateWithLifecycle()
    val appImageLoader by viewModel.imageLoader.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val language = when {
        AppCompatDelegate.getApplicationLocales().isEmpty -> "system"
        AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("zh") -> "zh"
        else -> "en"
    }
    val onLanguageChange: (String) -> Unit = { value ->
        AppCompatDelegate.setApplicationLocales(
            when (value) {
                "zh" -> LocaleListCompat.forLanguageTags("zh-CN")
                "en" -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.getEmptyLocaleList()
            },
        )
    }
    val transfers by transferViewModel.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val documentOpen = browserState.previewEntry?.let { entry ->
        !entry.isDir && (
            entry.kind == filehub.android.core.model.FileKind.Text ||
                entry.kind == filehub.android.core.model.FileKind.Code ||
                entry.isMarkdown ||
                browserState.previewAsText
            )
    } == true

    LaunchedEffect(session.error) {
        val message = session.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearSessionError()
    }

    LaunchedEffect(notificationDestination) {
        val destination = notificationDestination ?: return@LaunchedEffect
        NotificationNavigator.destination.value = null
        destination.profileId?.let(viewModel::activateProfile)
        navController.navigate(destination.route) { launchSingleTop = true }
    }

    LaunchedEffect(session.active?.id) {
        if (session.active == null) audioController.stop()
    }

    LaunchedEffect(connection?.status, connection?.profileId) {
        val current = connection ?: return@LaunchedEffect
        if (current.status != ServerConnectionStatus.Success) return@LaunchedEffect
        delay(450)
        if (viewModel.connection.value?.profileId == current.profileId) {
            if (tab == MainTab.Servers) tab = MainTab.Browse
            viewModel.clearConnection()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main",
        enterTransition = {
            slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 5 } +
                fadeIn(tween(200, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 12 } +
                fadeOut(tween(160))
        },
        popEnterTransition = {
            slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 12 } +
                fadeIn(tween(200, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 5 } +
                fadeOut(tween(160))
        },
    ) {
        composable("main") {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!documentOpen) Column {
                        AudioPlayerBar(
                            state = audioState,
                            onTogglePlay = audioController::togglePlayPause,
                            onNext = audioController::next,
                            onPrevious = audioController::previous,
                            onSeek = audioController::seekTo,
                            onExpand = { audioController.setExpanded(true) },
                            onCollapse = { audioController.setExpanded(false) },
                            onClose = audioController::stop,
                        )
                        transfers.firstOrNull {
                            it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING
                        }?.let { active ->
                            TransferPill(
                                name = active.name,
                                progress = active.progress,
                                onClick = { navController.navigate("transfers") },
                            )
                        }
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .height(58.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MainTab.entries.forEach { item ->
                                    val selected = tab == item
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .selectable(
                                                selected = selected,
                                                role = Role.Tab,
                                                onClick = { tab = item },
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 48.dp, height = 28.dp)
                                                .background(
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        androidx.compose.ui.graphics.Color.Transparent
                                                    },
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                item.icon,
                                                contentDescription = null,
                                                tint = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(item.labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
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
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(
                                tween(200, easing = FastOutSlowInEasing),
                            ) { direction * it / 12 } +
                                fadeIn(tween(180, easing = FastOutSlowInEasing)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        tween(160, easing = FastOutSlowInEasing),
                                    ) { -direction * it / 24 } +
                                        fadeOut(tween(140, easing = FastOutSlowInEasing)),
                                )
                        },
                        label = "main-tab",
                        modifier = Modifier.fillMaxSize(),
                    ) { currentTab ->
                    when (currentTab) {
                        MainTab.Servers -> ServersScreen(
                            state = session,
                            connection = connection,
                            onAddServer = { navController.navigate("addServer") },
                            onSelect = viewModel::connectProfile,
                            onRemove = viewModel::removeProfile,
                            onEdit = { id -> navController.navigate("editServer/$id") },
                        )
                        MainTab.Browse -> BrowseScreen(
                            state = session,
                            filesViewModel = filesViewModel,
                            onAddServer = { navController.navigate("addServer") },
                            onRetry = viewModel::refreshRoots,
                            onSearch = { rootId -> navController.navigate("search/${rootId ?: -1}") },
                            onPauseAudio = audioController::pauseForVideo,
                            onResumeAudio = audioController::resumeAfterVideo,
                        )
                        MainTab.My -> MyScreen(
                            state = session,
                            imageLoader = viewModel.imageLoader.collectAsStateWithLifecycle().value,
                            onLogout = viewModel::logout,
                            onRemove = { session.active?.let { viewModel.removeProfile(it.id) } },
                            onRefresh = viewModel::refreshRoots,
                            onTransfers = { navController.navigate("transfers") },
                            onPhotoSync = { navController.navigate("photoSync") },
                            onFolderSync = { navController.navigate("folderSync") },
                            onShares = { navController.navigate("shares") },
                            usage = viewModel.usage.collectAsStateWithLifecycle().value,
                            serverVersion = viewModel.serverVersion.collectAsStateWithLifecycle().value,
                            onLoadUsage = viewModel::loadUsage,
                            onLoadServerVersion = viewModel::loadServerVersion,
                            theme = theme,
                            onThemeChange = viewModel::setTheme,
                            language = language,
                            onLanguageChange = onLanguageChange,
                        )
                    }
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
                imageLoader = appImageLoader,
                onOpenResult = { result ->
                    val active = session.active
                    val root = session.roots.firstOrNull { it.id == result.rootId }
                    if (active != null && root != null) {
                        if (result.isDir) {
                            filesViewModel.open(active, root, result.path)
                        } else {
                            val parent = result.path.substringBeforeLast('/', "")
                            filesViewModel.open(active, root, parent)
                            filesViewModel.showPreview(result.toEntry(), pinImages = true)
                        }
                        tab = MainTab.Browse
                    }
                    searchViewModel.reset()
                    navController.popBackStack()
                },
            )
        }
        composable("shares") {
            SharesScreen(
                viewModel = shareViewModel,
                faviconUrl = session.active?.iconUrl,
                onBack = { navController.popBackStack() },
                onOpenPublic = { link ->
                    navController.navigate(
                        "publicShare/${Uri.encode(link.baseUrl)}/${Uri.encode(link.token)}",
                    )
                },
            )
        }
        composable("publicShare/{base}/{token}") { entry ->
            val base = entry.arguments?.getString("base")
            val token = entry.arguments?.getString("token")
            if (base != null && token != null) {
                val publicViewModel: PublicShareViewModel = viewModel(
                    factory = PublicShareViewModel.factory(
                        LocalContext.current.applicationContext as android.app.Application,
                        PublicLink(base, token),
                    ),
                )
                PublicShareScreen(
                    viewModel = publicViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("folderSync") {
            FolderSyncScreen(
                viewModel = folderSyncViewModel,
                remoteFolderPicker = remoteFolderPickerViewModel,
                session = session,
                onBack = { navController.popBackStack() },
            )
        }
        composable("photoSync") {
            PhotoSyncScreen(
                viewModel = photoSyncViewModel,
                folderPicker = remoteFolderPickerViewModel,
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
        composable("editServer/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            if (id != null) {
                ServerEditScreen(
                    session = session,
                    profileId = id,
                    onSave = { name, addresses ->
                        viewModel.updateProfile(id, name, addresses)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
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
