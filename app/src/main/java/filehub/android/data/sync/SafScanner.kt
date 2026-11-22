package filehub.android.data.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

data class LocalNode(
    val relativePath: String,
    val uri: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
)

object SafScanner {

    fun scan(context: Context, treeUri: Uri): Result<List<LocalNode>> = runCatching {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("cannot open selected folder")
        val result = mutableListOf<LocalNode>()
        walk(root, "", result, 0)
        result
    }

    private fun walk(dir: DocumentFile, prefix: String, out: MutableList<LocalNode>, depth: Int) {
        if (depth > 32 || out.size > 20_000) return
        dir.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            if (name.startsWith(".")) return@forEach
            val relative = if (prefix.isEmpty()) name else "$prefix/$name"
            val mtime = child.lastModified().coerceAtLeast(0) / 1000
            out += LocalNode(
                relativePath = relative,
                uri = child.uri.toString(),
                name = name,
                isDir = child.isDirectory,
                size = if (child.isDirectory) 0 else child.length(),
                mtime = mtime,
            )
            if (child.isDirectory) walk(child, relative, out, depth + 1)
        }
    }

    fun findFile(context: Context, treeUri: Uri, relativePath: String): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        segments.forEachIndexed { index, segment ->
            val child = current.findFile(segment) ?: return null
            if (index < segments.lastIndex && !child.isDirectory) return null
            current = child
        }
        return current
    }

    fun findOrCreateDir(context: Context, treeUri: Uri, relativePath: String): Result<DocumentFile> = runCatching {
        var current = DocumentFile.fromTreeUri(context, treeUri) ?: error("cannot open selected folder")
        relativePath.split('/').filter { it.isNotEmpty() }.forEach { segment ->
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment) ?: error("cannot create $segment")
                existing.isDirectory -> existing
                else -> error("$segment is not a directory")
            }
        }
        current
    }

    fun findOrCreateFile(
        context: Context,
        treeUri: Uri,
        relativePath: String,
        mimeType: String,
    ): Result<DocumentFile> = runCatching {
        val parentPath = relativePath.substringBeforeLast('/', "")
        val name = relativePath.substringAfterLast('/')
        val parent = findOrCreateDir(context, treeUri, parentPath).getOrThrow()
        val existing = parent.findFile(name)
        if (existing != null && existing.isFile) {
            existing
        } else {
            parent.createFile(mimeType.ifBlank { "application/octet-stream" }, name)
                ?: error("cannot create $name")
        }
    }

    fun openInput(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("cannot read $uri")

    fun openOutput(context: Context, uri: Uri, append: Boolean = false): OutputStream =
        context.contentResolver.openOutputStream(uri, if (append) "wa" else "wt")
            ?: error("cannot write $uri")

    fun delete(context: Context, uri: String): Boolean =
        DocumentFile.fromSingleUri(context, Uri.parse(uri))?.delete() ?: false
}
