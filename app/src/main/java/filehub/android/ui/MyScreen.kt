package filehub.android.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import filehub.android.BuildConfig
import filehub.android.R
import filehub.android.data.SessionState
import java.text.DateFormat
import java.util.Date

@Composable
fun MyScreen(
    state: SessionState,
    imageLoader: ImageLoader?,
    onLogout: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
    onTransfers: () -> Unit,
    onPhotoSync: () -> Unit,
    onFolderSync: () -> Unit,
    onShares: () -> Unit,
    theme: String,
    onThemeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    val profile = state.active
    var confirmRemove by remember { mutableStateOf(false) }
    val avatarUrl = profile?.avatarVersion?.takeIf { it > 0 }?.let { version ->
        "${profile.baseUrl.trimEnd('/')}/api/v1/me/avatar?v=$version"
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
                value = expiresAt?.let {
                    stringResource(R.string.my_expiry_value, formatExpiry(it))
                } ?: "—",
            )
            if (expiresAt != null) {
                val remainingDays = remainingDays(expiresAt)
                Text(
                    text = if (remainingDays > 0) {
                        stringResource(R.string.my_expiry_days, remainingDays)
                    } else if (expiresAt * 1000 > System.currentTimeMillis()) {
                        stringResource(R.string.my_expiry_today)
                    } else {
                        stringResource(R.string.my_expiry_expired)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expiresAt * 1000 > System.currentTimeMillis()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = stringResource(R.string.my_token_expiry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow(stringResource(R.string.my_security_note), "")
        }

        AppearanceCard(
            theme = theme,
            onThemeChange = onThemeChange,
            language = language,
            onLanguageChange = onLanguageChange,
        )
        InfoCard(title = stringResource(R.string.my_version_format, BuildConfig.VERSION_NAME)) {
            Text(
                text = stringResource(R.string.my_version_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
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
        ) {
            Text(stringResource(R.string.my_logout))
        }
        TextButton(
            onClick = { confirmRemove = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.servers_remove),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.servers_remove_title)) },
            text = { Text(stringResource(R.string.servers_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    },
                ) {
                    Text(stringResource(R.string.common_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
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
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
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

private fun formatExpiry(epochSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1000))

private fun remainingDays(epochSeconds: Long): Long {
    val remainingMillis = epochSeconds * 1000 - System.currentTimeMillis()
    if (remainingMillis <= 0) return 0
    return (remainingMillis + 86_399_999) / 86_400_000
}
