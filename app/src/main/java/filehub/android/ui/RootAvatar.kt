package filehub.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileRoot

@Composable
internal fun RootAvatar(
    root: FileRoot,
    avatarUrl: String?,
    imageLoader: ImageLoader?,
    size: Dp = 36.dp,
) {
    if (!root.isRemote) {
        Icon(
            painter = painterResource(R.drawable.ic_file_folder),
            contentDescription = null,
            tint = fileIconColor(FileKind.Folder),
            modifier = Modifier.size(size),
        )
        return
    }
    val name = root.remoteOwnerName?.takeIf { it.isNotBlank() } ?: root.alias
    if (avatarUrl != null && imageLoader != null) {
        SubcomposeAsyncImage(
            model = avatarUrl,
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            loading = { InitialAvatar(name, size) },
            error = { InitialAvatar(name, size) },
        )
    } else {
        InitialAvatar(name, size)
    }
}

@Composable
internal fun InitialAvatar(name: String, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            style = if (size >= 32.dp) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.labelSmall
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun avatarColor(name: String): Color {
    var hash = 0
    for (char in name) {
        hash = (hash * 31 + char.code) % 360
    }
    return Color.hsl(hash.toFloat(), 0.45f, 0.52f)
}

internal fun remoteAvatarUrl(baseUrl: String?, root: FileRoot): String? {
    if (!root.isRemote || baseUrl.isNullOrBlank()) return null
    if (root.remoteAvatarVersion <= 0) return null
    val mountId = root.mountId ?: return null
    return "${baseUrl.trimEnd('/')}/api/v1/remote/mounts/$mountId/avatar?v=${root.remoteAvatarVersion}"
}
