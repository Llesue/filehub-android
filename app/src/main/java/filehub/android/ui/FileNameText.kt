package filehub.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

private val COMPOSITE_SUFFIXES = listOf(
    ".tar.gz",
    ".tar.bz2",
    ".tar.xz",
    ".tar.zst",
    ".tar.zstd",
)

private val KNOWN_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "ico", "tif", "tiff", "heic",
    "heif", "jxl", "mp4", "mov", "mkv", "webm", "avi", "m4v", "mpg", "mpeg", "3gp",
    "3g2", "wmv", "flv", "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "oga",
    "wma", "aiff", "amr", "pdf", "doc", "docx", "rtf", "odt", "docm", "dotm", "dotx",
    "wps", "caj", "xls", "xlsx", "xlsb", "xlsm", "csv", "tsv", "ods", "et", "ppt",
    "pptx", "pptm", "potm", "potx", "ppsx", "odp", "dps", "key", "db", "sqlite",
    "sqlite3", "mdb", "accdb", "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst",
    "cab", "tgz", "kt", "kts", "java", "rs", "go", "py", "js", "ts", "tsx", "jsx",
    "c", "h", "cpp", "hpp", "cs", "rb", "php", "sh", "zsh", "sql", "json", "yaml",
    "yml", "toml", "xml", "ini", "conf", "cfg", "properties", "env", "bat", "cmd",
    "ps1", "psm1", "vbs", "reg", "ahk", "md", "markdown",
    "txt", "log", "html", "css", "scss", "vue", "svelte", "lua", "pl", "swift", "dart",
    "apk", "aab", "epub", "mobi", "azw", "azw3", "fb2", "djvu", "ttf", "otf", "woff",
    "woff2", "eot", "iso", "img", "dmg", "vhd", "vhdx", "vmdk", "psd", "ai", "xcf",
    "sketch", "fig", "afdesign", "afphoto", "torrent", "srt", "ass", "ssa", "vtt", "sub",
)

private val NUMERIC_SUFFIX_WHITELIST = setOf("7z", "3gp", "3g2")

/**
 * Splits a display name into a body and a suffix that is worth keeping
 * visible. Returns an empty suffix whenever the trailing dot segment is not a
 * believable extension, e.g. `v1.2.3`, `photo.2026`, `.gitignore`, `file.`.
 */
fun splitFileName(name: String): Pair<String, String> {
    if (name.isEmpty()) return name to ""
    val lower = name.lowercase()
    for (suffix in COMPOSITE_SUFFIXES) {
        if (lower.endsWith(suffix) && name.length > suffix.length) {
            return name.dropLast(suffix.length) to name.takeLast(suffix.length)
        }
    }
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return name to ""
    val suffix = name.substring(dot + 1)
    if (suffix.isEmpty() || suffix.length > 12) return name to ""
    if (suffix.all(Char::isDigit)) return name to ""
    val looksLikeExtension = suffix.first().isLetter() &&
        suffix.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    val known = suffix.lowercase() in KNOWN_EXTENSIONS ||
        suffix.lowercase() in NUMERIC_SUFFIX_WHITELIST
    val singleDot = name.indexOf('.') == dot
    // A multi-dot name whose last segment is not a known extension is more
    // likely a name like `my.file.name` than a real suffix.
    if (!known && (!looksLikeExtension || !singleDot)) {
        return name to ""
    }
    return name.substring(0, dot) to name.substring(dot)
}

@Composable
fun FileNameText(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val (body, suffix) = splitFileName(name)
    if (suffix.isEmpty()) {
        Text(
            text = name,
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = body,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = suffix,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}
