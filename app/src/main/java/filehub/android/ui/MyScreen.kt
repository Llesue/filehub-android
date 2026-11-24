package filehub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.ImageLoader
import coil3.compose.AsyncImage
import android.text.format.Formatter
import filehub.android.BuildConfig
import filehub.android.R
import filehub.android.data.SessionState
import kotlin.math.roundToInt

@Composable
fun MyScreen(
    state: SessionState,
    imageLoader: ImageLoader?,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onTransfers: () -> Unit,
    onPhotoSync: () -> Unit,
    onFolderSync: () -> Unit,
    onShares: () -> Unit,
    usage: UsageUiState,
    serverVersion: filehub.android.data.api.VersionDto?,
    clientUpdate: ClientUpdateUiState,
    versionError: String?,
    onLoadUsage: () -> Unit,
    onLoadServerVersion: () -> Unit,
    onLoadClientUpdate: () -> Unit,
    theme: String,
    onThemeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    val profile = state.active
    val avatarUrl = profile?.avatarVersion?.takeIf { it > 0 }?.let { version ->
        "${profile.baseUrl.trimEnd('/')}/api/v1/me/avatar?v=$version"
    }

    LaunchedEffect(profile?.id) {
        if (profile != null) {
            onLoadUsage()
            onLoadServerVersion()
            onLoadClientUpdate()
        }
    }

    if (profile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.my_version_format, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browse_no_server_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.my_account),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.username.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    if (avatarUrl != null && imageLoader != null) {
                        AsyncImage(
                            model = avatarUrl,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                QuotaRing(usage)
            }
        }

        if (state.needsLogin) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.servers_needs_login),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        InfoCard(title = stringResource(R.string.my_server)) {
            val expiresAt = profile.tokenExpiresAt
            InfoRow(
                label = stringResource(R.string.my_token_expiry),
                value = tokenValidityText(expiresAt),
            )
            Text(
                text = stringResource(R.string.my_token_expiry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow(stringResource(R.string.my_security_note), "")
        }

        VersionCard(
            serverVersion = serverVersion,
            clientUpdate = clientUpdate,
            versionError = versionError,
        )

        AppearanceCard(
            theme = theme,
            onThemeChange = onThemeChange,
            language = language,
            onLanguageChange = onLanguageChange,
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onTransfers,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.my_transfers))
        }
        OutlinedButton(
            onClick = onPhotoSync,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.photo_sync_screen))
        }
        OutlinedButton(
            onClick = onFolderSync,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.folder_sync_screen))
        }
        OutlinedButton(
            onClick = onShares,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.shares_title))
        }
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_refresh))
        }
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.my_logout))
        }
    }
}

@Composable
private fun QuotaRing(usage: UsageUiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val data = usage.usage
    val used = data?.usedBytes
    val quota = data?.quotaBytes
    val fraction = if (used != null && quota != null && quota > 0) {
        (used.toFloat() / quota).coerceIn(0f, 1f)
    } else {
        null
    }
    val color = if (fraction != null && fraction >= 0.9f) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.outlineVariant
    val percent = if (fraction != null) {
        "${(fraction * 100).roundToInt()}%"
    } else if (data == null) {
        "…"
    } else {
        "∞"
    }
    val detail = if (used != null) {
        val usedText = Formatter.formatShortFileSize(context, used)
        val quotaText = if (quota != null && quota > 0) {
            Formatter.formatShortFileSize(context, quota)
        } else {
            stringResource(R.string.usage_unlimited)
        }
        "$usedText / $quotaText"
    } else {
        null
    }
    val description = if (fraction != null && detail != null) {
        stringResource(R.string.usage_ring_description, (fraction * 100).roundToInt(), detail)
    } else {
        stringResource(R.string.usage_title)
    }
    Column(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                drawCircle(color = track, style = Stroke(width = stroke))
                if (fraction != null) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = fraction * 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = percent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun VersionCard(
    serverVersion: filehub.android.data.api.VersionDto?,
    clientUpdate: ClientUpdateUiState,
    versionError: String?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val release = clientUpdate.release
    val clientNewer = release != null &&
        filehub.android.data.ClientUpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME)
    val serverNewer = serverVersion?.let {
        it.checkEnabled && it.updateAvailable && !it.latest.isNullOrBlank()
    } == true
    var dialog by remember { mutableStateOf(false) }
    var dialogIsClient by remember { mutableStateOf(true) }

    fun open(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                ),
            )
        }
    }

    InfoCard(title = stringResource(R.string.version_title)) {
        VersionRow(
            label = stringResource(R.string.client_version),
            value = BuildConfig.VERSION_NAME,
            chip = if (clientNewer) {
                stringResource(R.string.version_new, release?.version.orEmpty())
            } else {
                null
            },
            onChipClick = {
                dialogIsClient = true
                dialog = true
            },
        )
        VersionRow(
            label = stringResource(R.string.server_version),
            value = serverVersion?.version ?: "—",
            chip = if (serverNewer) {
                stringResource(R.string.version_new, serverVersion.latest.orEmpty())
            } else {
                null
            },
            onChipClick = {
                dialogIsClient = false
                dialog = true
            },
        )
        versionError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (dialog) {
        val current = BuildConfig.VERSION_NAME
        val latest = if (dialogIsClient) release?.version.orEmpty() else serverVersion?.latest.orEmpty()
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = {
                Text(
                    stringResource(
                        if (dialogIsClient) R.string.client_update_title else R.string.server_update_title,
                    ),
                )
            },
            text = {
                Text(stringResource(R.string.client_update_body, current, latest))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = false
                        if (dialogIsClient) {
                            release?.url?.let(::open)
                        } else {
                            open("https://github.com/Llesue/filehub/releases/latest")
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (dialogIsClient) R.string.open_download_page else R.string.open_release_page,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun VersionRow(
    label: String,
    value: String,
    chip: String? = null,
    onChipClick: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
        if (chip != null) {
            Spacer(Modifier.size(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable { onChipClick?.invoke() },
            ) {
                Text(
                    text = chip,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    theme: String,
    onThemeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    var themeMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    InfoCard(title = stringResource(R.string.appearance), spacing = 4.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SettingTile(
                    label = stringResource(R.string.language),
                    value = languageLabel(language),
                    onClick = { languageMenu = true },
                )
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    listOf("system", "zh", "en").forEach { value ->
                        DropdownMenuItem(
                            text = { Text(languageLabel(value)) },
                            onClick = {
                                onLanguageChange(value)
                                languageMenu = false
                            },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                SettingTile(
                    label = stringResource(R.string.theme),
                    value = themeLabel(theme),
                    onClick = { themeMenu = true },
                )
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    listOf("system", "light", "dark").forEach { value ->
                        DropdownMenuItem(
                            text = { Text(themeLabel(value)) },
                            onClick = {
                                onThemeChange(value)
                                themeMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingTile(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun languageLabel(value: String): String = when (value) {
    "zh" -> stringResource(R.string.language_zh)
    "en" -> stringResource(R.string.language_en)
    else -> stringResource(R.string.language_system)
}

@Composable
private fun themeLabel(value: String): String = when (value) {
    "light" -> stringResource(R.string.theme_light)
    "dark" -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

@Composable
private fun InfoCard(
    title: String,
    spacing: Dp = 8.dp,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value.isNotBlank()) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
