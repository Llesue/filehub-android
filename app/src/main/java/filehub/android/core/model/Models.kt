package filehub.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerProfile(
    val id: String,
    val baseUrl: String,
    val displayName: String,
    val username: String,
    val userId: Long,
    val tokenId: Long,
    val tokenExpiresAt: Long?,
    val createdAt: Long,
    val lastRootId: Long? = null,
    val lastPath: String = "",
)

data class FileRoot(
    val id: Long,
    val alias: String,
    val readOnly: Boolean,
    val kind: String?,
    val mountId: Long?,
) {
    val isRemote: Boolean get() = kind == "remote"
}

data class Branding(
    val name: String,
    val iconUrl: String?,
)

data class LoginResult(
    val profile: ServerProfile,
    val token: String,
    val roots: List<FileRoot>,
)

enum class FileSort(val apiValue: String) {
    Name("name"),
    Size("size"),
    Modified("modified"),
}

enum class SortOrder(val apiValue: String) {
    Asc("asc"),
    Desc("desc"),
}

enum class FileKind {
    Folder,
    Image,
    Video,
    Audio,
    Pdf,
    Text,
    Archive,
    Code,
    File,
}

data class FileEntry(
    val name: String,
    val isDir: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val modified: Long,
    val mimeType: String,
    val hasPreview: Boolean,
    val symlinkState: String?,
) {
    val kind: FileKind by lazy { detectKind(name, mimeType, isDir) }
}

data class DirectoryListing(
    val entries: List<FileEntry>,
    val nextCursor: String?,
    val readOnly: Boolean,
    val readOnlyReason: String?,
    val stale: Boolean,
)

private fun detectKind(name: String, mimeType: String, isDir: Boolean): FileKind {
    if (isDir) return FileKind.Folder
    val mime = mimeType.lowercase()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") -> FileKind.Image
        mime.startsWith("video/") -> FileKind.Video
        mime.startsWith("audio/") -> FileKind.Audio
        mime == "application/pdf" || ext == "pdf" -> FileKind.Pdf
        ext in setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz") -> FileKind.Archive
        ext in setOf(
            "kt", "kts", "java", "rs", "go", "py", "js", "ts", "tsx", "jsx",
            "c", "h", "cpp", "hpp", "cs", "rb", "php", "sh", "zsh", "sql",
            "json", "yaml", "yml", "toml", "xml",
        ) -> FileKind.Code
        mime.startsWith("text/") -> FileKind.Text
        ext in setOf("md", "markdown", "txt", "log", "csv") -> FileKind.Text
        else -> FileKind.File
    }
}
