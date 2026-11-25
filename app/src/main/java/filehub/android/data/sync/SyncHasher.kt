package filehub.android.data.sync

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Streaming SHA-256 over media or SAF content, with no whole-file buffering. */
internal object SyncHasher {

    suspend fun sha256(context: Context, uri: String): String? = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@withContext null
        input.use { digest(it) }
    }

    suspend fun sha256Saf(context: Context, uri: String): String? = withContext(Dispatchers.IO) {
        val input = SafScanner.openInput(context, Uri.parse(uri))
        input.use { digest(it) }
    }

    internal suspend fun digest(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read <= 0) break
            md.update(buffer, 0, read)
        }
        return md.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
