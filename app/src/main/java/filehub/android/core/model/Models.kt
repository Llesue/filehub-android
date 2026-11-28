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
    val addresses: List<String> = emptyList(),
    val iconUrl: String? = null,
    val avatarVersion: Long? = null,
    val lastRootId: Long? = null,
    val lastPath: String = "",
    val serverVersion: String? = null,
)

data class FileRoot(
    val id: Long,
    val alias: String,
    val readOnly: Boolean,
    val kind: String?,
    val mountId: Long?,
    val online: Boolean? = null,
    val remoteOwnerName: String? = null,
    val remoteAvatarVersion: Long = 0,
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
    val serverVersion: String? = null,
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

enum class BrowserViewMode(val storageValue: String) {
    List("list"),
    Grid("grid"),
}

enum class FileKind {
    Folder,
    Image,
    Video,
    Audio,
    Pdf,
    Text,
    Document,
    Sheet,
    Slide,
    Database,
    Archive,
    Code,
    Apk,
    Installer,
    App,
    Library,
    Shortcut,
    Config,
    Ebook,
    Font,
    DiskImage,
    Design,
    Torrent,
    File,
}

@Serializable
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
    val isMarkdown: Boolean
        get() = !isDir && (name.endsWith(".md", true) || name.endsWith(".markdown", true))
}

data class SearchResult(
    val rootId: Long,
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val mimeType: String,
    val hasPreview: Boolean,
) {
    fun toEntry(): FileEntry = FileEntry(
        name = name,
        isDir = isDir,
        isSymlink = false,
        size = size,
        modified = modified,
        mimeType = mimeType,
        hasPreview = hasPreview,
        symlinkState = null,
    )
}

data class FileProperties(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mimeType: String,
    val created: Long?,
    val modified: Long?,
    val mode: String?,
    val image: ImageProperties?,
)

data class ImageProperties(
    val width: Int?,
    val height: Int?,
    val format: String?,
    val exif: Map<String, String>,
)

data class DirectoryListing(
    val entries: List<FileEntry>,
    val nextCursor: String?,
    val readOnly: Boolean,
    val readOnlyReason: String?,
    val stale: Boolean,
)

data class ShareInfo(
    val id: Long,
    val kind: String,
    val itemCount: Int,
    val realPath: String,
    val isDir: Boolean,
    val hasPassword: Boolean,
    val expiresAt: Long?,
    val expired: Boolean,
    val readOnly: Boolean,
    val revoked: Boolean,
    val createdAt: Long,
    val visitCount: Int,
)

data class ShareCreated(
    val id: Long,
    val token: String,
    val url: String,
    val expiresAt: Long?,
)

data class PublicShareEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val mimeType: String,
    val hasPreview: Boolean,
)

data class PublicShareMeta(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val readOnly: Boolean,
    val entries: List<PublicShareEntry>,
    val nextCursor: String?,
    val collection: Boolean,
)

private val KNOWN_CODE_NAMES = setOf("dockerfile", "makefile", "cmakelists.txt", "rakefile")
private val KNOWN_TEXT_NAMES = setOf("license", "licence", "readme")

internal fun detectKind(name: String, mimeType: String, isDir: Boolean): FileKind {
    if (isDir) return FileKind.Folder
    val mime = mimeType.lowercase()
    val ext = name.substringAfterLast('.', "").lowercase()
    val lowerName = name.lowercase()
    if (lowerName in KNOWN_CODE_NAMES) return FileKind.Code
    if (lowerName in KNOWN_TEXT_NAMES) return FileKind.Text
    return when {
        mime == "application/vnd.android.package-archive" || ext == "apk" -> FileKind.Apk
        ext in setOf("msi", "msix", "appx", "msu", "pkg", "deb", "rpm") -> FileKind.Installer
        ext in setOf("exe", "com", "scr") -> FileKind.App
        ext in setOf("dll", "sys", "drv", "ocx", "so", "dylib") -> FileKind.Library
        ext in setOf("lnk", "url", "webloc", "desktop") -> FileKind.Shortcut
        ext in setOf("inf", "ini", "cfg", "conf", "env", "reg", "properties") -> FileKind.Config
        mime.startsWith("image/") -> FileKind.Image
        ext in setOf(
            "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "ico", "tif", "tiff",
            "heic", "heif", "jxl", "svg", "icns", "exr", "hdr", "tga", "cr2", "nef",
            "arw", "dng",
        ) -> FileKind.Image
        mime.startsWith("video/") -> FileKind.Video
        ext in setOf(
            "mp4", "mov", "mkv", "webm", "avi", "m4v", "mpg", "mpeg", "m2ts", "mts",
            "wmv", "flv", "3gp", "ogv", "vob", "asf", "f4v", "rm", "rmvb",
        ) -> FileKind.Video
        mime.startsWith("audio/") -> FileKind.Audio
        ext in setOf(
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "oga", "wma", "aiff",
            "amr", "ape", "m4b",
        ) -> FileKind.Audio
        mime == "application/pdf" || ext in setOf("pdf", "ofd") -> FileKind.Pdf
        ext in setOf("epub", "mobi", "azw", "azw3", "fb2", "djvu") -> FileKind.Ebook
        ext in setOf("ttf", "otf", "fon", "woff", "woff2", "eot") -> FileKind.Font
        ext in setOf("iso", "img", "dmg", "vhd", "vhdx", "vmdk", "wim", "esd") -> FileKind.DiskImage
        ext in setOf(
            "psd", "ai", "xd", "sketch", "fig", "eps", "xcf", "afdesign", "afphoto",
        ) -> FileKind.Design
        ext == "torrent" -> FileKind.Torrent
        ext in setOf("doc", "docx", "rtf", "odt", "docm", "dotm", "dotx", "wps", "caj") -> FileKind.Document
        ext in setOf("xls", "xlsx", "xlsb", "xlsm", "xltm", "xltx", "csv", "tsv", "ods", "et") -> FileKind.Sheet
        ext in setOf("ppt", "pptx", "pptm", "potm", "potx", "ppsx", "odp", "dps", "key") -> FileKind.Slide
        ext in setOf("db", "sqlite", "sqlite3", "mdb", "accdb") -> FileKind.Database
        mime.startsWith("application/msword") -> FileKind.Document
        mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") -> FileKind.Document
        mime.startsWith("application/vnd.ms-excel") -> FileKind.Sheet
        mime.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") -> FileKind.Sheet
        mime.startsWith("application/vnd.ms-powerpoint") -> FileKind.Slide
        mime.startsWith("application/vnd.openxmlformats-officedocument.presentationml") -> FileKind.Slide
        ext in setOf(
            "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst", "cab", "tgz", "tbz2",
            "txz", "aab",
        ) -> FileKind.Archive
        ext in setOf(
            "kt", "kts", "java", "rs", "go", "py", "js", "ts", "tsx", "jsx",
            "c", "h", "cpp", "hpp", "cs", "rb", "php", "sh", "zsh", "sql",
            "json", "yaml", "yml", "toml", "xml", "bat", "cmd", "ps1", "psm1",
            "vbs", "wsf", "ahk", "css", "html", "vue", "svelte", "lua", "mjs", "cjs",
        ) -> FileKind.Code
        mime.startsWith("text/") -> FileKind.Text
        ext in setOf("md", "markdown", "txt", "log", "csv", "srt", "ass", "ssa", "vtt", "sub") -> FileKind.Text
        else -> FileKind.File
    }
}
