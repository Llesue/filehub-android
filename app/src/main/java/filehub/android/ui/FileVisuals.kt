package filehub.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.ui.theme.FileHubFileBlue
import filehub.android.ui.theme.FileHubFileBrown
import filehub.android.ui.theme.FileHubFileCyan
import filehub.android.ui.theme.FileHubFileDoc
import filehub.android.ui.theme.FileHubFileFolder
import filehub.android.ui.theme.FileHubFileGray
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
    FileKind.Archive -> R.drawable.ic_file_archive
    FileKind.Code -> R.drawable.ic_file_code
    FileKind.Text, FileKind.File -> R.drawable.ic_file_document
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
    FileKind.Archive -> FileHubFileBrown
    FileKind.Code -> FileHubFileCyan
    FileKind.Text, FileKind.File -> MaterialTheme.colorScheme.onSurfaceVariant
}
