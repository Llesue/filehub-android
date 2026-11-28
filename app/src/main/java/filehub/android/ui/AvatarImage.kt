package filehub.android.ui

import android.content.Context
import coil3.request.ImageRequest
import filehub.android.core.model.ServerProfile

internal fun avatarCacheKey(profile: ServerProfile): String =
    "avatar:${profile.id}:${profile.userId}:${profile.username}:${profile.avatarVersion ?: 0}"

internal fun avatarImageRequest(
    context: Context,
    profile: ServerProfile,
    url: String,
): ImageRequest {
    val key = avatarCacheKey(profile)
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .build()
}
