package filehub.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import filehub.android.R

/**
 * Single source of truth for the encodings the server accepts. `null` means
 * "let the server detect it".
 */
val EncodingOptions: List<Pair<String?, String>> = listOf(
    null to "Auto",
    "utf-8" to "UTF-8",
    "utf-16le" to "UTF-16LE",
    "utf-16be" to "UTF-16BE",
    "gbk" to "GBK",
    "gb18030" to "GB18030",
    "big5" to "Big5",
    "shift_jis" to "Shift_JIS",
    "euc-kr" to "EUC-KR",
    "windows-1250" to "Windows-1250",
    "windows-1251" to "Windows-1251",
    "windows-1252" to "Windows-1252",
    "iso-8859-1" to "ISO-8859-1",
    "iso-8859-15" to "ISO-8859-15",
)

fun encodingDisplayName(value: String?): String =
    EncodingOptions.firstOrNull { it.first == value }?.second ?: (value ?: "Auto")

@Composable
fun encodingLabel(value: String?): String =
    if (value == null) {
        stringResource(R.string.editor_encoding_auto)
    } else {
        encodingDisplayName(value)
    }
