package filehub.android.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.InputStream

/**
 * Opens media with the original EXIF metadata when the platform allows it.
 *
 * Android 10+ redacts location fields from MediaStore streams unless the app
 * holds ACCESS_MEDIA_LOCATION and asks for the original item. SAF and file
 * URIs are passed through unchanged.
 */
internal object MediaStreams {

    fun hasLocationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    fun open(context: Context, uri: Uri): InputStream {
        val mediaUri = toMediaUri(context, uri)
        val base = mediaUri ?: uri
        val resolved = originalUri(context, base)
        val stream = runCatching { context.contentResolver.openInputStream(resolved) }
            .getOrNull()
            ?: runCatching { context.contentResolver.openInputStream(base) }.getOrNull()
            ?: runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        return stream ?: error("cannot open media uri")
    }

    fun originalUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        if (uri.authority != MediaStore.AUTHORITY) return uri
        if (!hasLocationPermission(context)) return uri
        return setRequireOriginal(uri)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setRequireOriginal(uri: Uri): Uri =
        runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)

    fun hashVersion(context: Context, uri: Uri): Int {
        val mediaUri = toMediaUri(context, uri) ?: uri
        return if (requiresOriginal(context, mediaUri.authority)) PHOTO_HASH_VERSION else 0
    }

    /** Converts a SAF document URI to its MediaStore equivalent when possible. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaUri(context: Context, uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        if (authority != "com.android.externalstorage.documents" &&
            authority != "com.android.providers.media.documents"
        ) {
            return null
        }
        return runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
    }

    private fun toMediaUri(context: Context, uri: Uri): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaUri(context, uri)
        } else {
            null
        }

    internal fun requiresOriginal(context: Context, authority: String?): Boolean =
        requiresOriginal(
            authority = authority,
            sdkInt = Build.VERSION.SDK_INT,
            hasLocationPermission = hasLocationPermission(context),
        )

    internal fun requiresOriginal(
        authority: String?,
        sdkInt: Int,
        hasLocationPermission: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.Q &&
        authority == MediaStore.AUTHORITY &&
        hasLocationPermission
}
