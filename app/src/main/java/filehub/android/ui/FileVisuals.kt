package filehub.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.ui.theme.FileHubFileBlue
import filehub.android.ui.theme.FileHubFileBrown
import filehub.android.ui.theme.FileHubFileCyan
import filehub.android.ui.theme.FileHubFileDoc
import filehub.android.ui.theme.FileHubFileFolder
import filehub.android.ui.theme.FileHubFileGray
import filehub.android.ui.theme.FileHubFileGreen
import filehub.android.ui.theme.FileHubFileSheet
import filehub.android.ui.theme.FileHubFileSlide
import filehub.android.ui.theme.FileHubFileOrange
import filehub.android.ui.theme.FileHubFilePurple
import filehub.android.ui.theme.FileHubFileRed
import filehub.android.ui.theme.FileHubFileTeal

fun fileIconRes(kind: FileKind): Int = when (kind) {
    FileKind.Folder -> R.drawable.ic_file_folder
    FileKind.Image -> R.drawable.ic_file_image
    FileKind.Video -> R.drawable.ic_file_video
    FileKind.Audio -> R.drawable.ic_file_audio
    FileKind.Pdf -> R.drawable.ic_file_pdf
    FileKind.Document -> R.drawable.ic_file_document
    FileKind.Sheet -> R.drawable.ic_file_sheet
    FileKind.Slide -> R.drawable.ic_file_slide
    FileKind.Database -> R.drawable.ic_file_database
    FileKind.Archive -> R.drawable.ic_file_archive
    FileKind.Code -> R.drawable.ic_file_code
    FileKind.Apk -> R.drawable.ic_file_apk
    FileKind.Ebook,
    FileKind.Font,
    FileKind.Torrent,
    FileKind.Text,
    FileKind.File,
    -> R.drawable.ic_file_document
    FileKind.DiskImage -> R.drawable.ic_file_archive
    FileKind.Design -> R.drawable.ic_file_image
}

@Composable
fun fileIconColor(kind: FileKind): Color = when (kind) {
    FileKind.Folder -> FileHubFileFolder
    FileKind.Image -> FileHubFilePurple
    FileKind.Video -> FileHubFileOrange
    FileKind.Audio -> FileHubFileBlue
    FileKind.Pdf -> FileHubFileRed
    FileKind.Document -> FileHubFileDoc
    FileKind.Sheet -> FileHubFileSheet
    FileKind.Slide -> FileHubFileSlide
    FileKind.Database -> FileHubFileSheet
    FileKind.Archive -> FileHubFileBrown
    FileKind.Code -> FileHubFileCyan
    FileKind.Apk -> FileHubFileGreen
    FileKind.Ebook -> FileHubFileBrown
    FileKind.Font -> FileHubFilePurple
    FileKind.DiskImage -> FileHubFileGray
    FileKind.Design -> FileHubFilePurple
    FileKind.Torrent -> FileHubFileTeal
    FileKind.Text, FileKind.File -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun fileKindLabel(kind: FileKind): String = when (kind) {
    FileKind.Folder -> stringResource(R.string.file_type_label_folder)
    FileKind.Image -> stringResource(R.string.file_type_label_image)
    FileKind.Video -> stringResource(R.string.file_type_label_video)
    FileKind.Audio -> stringResource(R.string.file_type_label_audio)
    FileKind.Pdf -> stringResource(R.string.file_type_label_pdf)
    FileKind.Text -> stringResource(R.string.file_type_label_text)
    FileKind.Document -> stringResource(R.string.file_type_label_document)
    FileKind.Sheet -> stringResource(R.string.file_type_label_sheet)
    FileKind.Slide -> stringResource(R.string.file_type_label_slide)
    FileKind.Database -> stringResource(R.string.file_type_label_database)
    FileKind.Archive -> stringResource(R.string.file_type_label_archive)
    FileKind.Code -> stringResource(R.string.file_type_label_code)
    FileKind.Apk -> stringResource(R.string.file_type_label_apk)
    FileKind.Ebook -> stringResource(R.string.file_type_label_ebook)
    FileKind.Font -> stringResource(R.string.file_type_label_font)
    FileKind.DiskImage -> stringResource(R.string.file_type_label_disk_image)
    FileKind.Design -> stringResource(R.string.file_type_label_design)
    FileKind.Torrent -> stringResource(R.string.file_type_label_torrent)
    FileKind.File -> stringResource(R.string.file_type_label_file)
}
