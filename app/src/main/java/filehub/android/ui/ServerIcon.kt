package filehub.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import filehub.android.R
import filehub.android.core.model.ServerProfile

@Composable
internal fun ServerIcon(
    profile: ServerProfile?,
    size: Dp,
    corner: Dp,
    imageLoader: ImageLoader? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (profile == null) {
            Icon(
                painter = painterResource(R.drawable.ic_file_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.64f),
            )
        } else {
            Text(
                text = profile.displayName.take(1).uppercase().ifBlank { "F" },
                style = if (size >= 40.dp) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            profile.iconUrl?.let { icon -> ServerFavicon(icon, imageLoader) }
        }
    }
}

@Composable
private fun ServerFavicon(icon: String, imageLoader: ImageLoader?) {
    if (imageLoader == null) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        AsyncImage(
            model = icon,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
