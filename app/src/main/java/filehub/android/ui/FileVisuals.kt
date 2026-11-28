package filehub.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import filehub.android.R
import filehub.android.core.model.FileKind
import filehub.android.ui.theme.FileHubFileArchive
import filehub.android.ui.theme.FileHubFileArchiveDark
import filehub.android.ui.theme.FileHubFileAudio
import filehub.android.ui.theme.FileHubFileAudioDark
import filehub.android.ui.theme.FileHubFileCode
import filehub.android.ui.theme.FileHubFileCodeDark
import filehub.android.ui.theme.FileHubFileDoc
import filehub.android.ui.theme.FileHubFileDocDark
import filehub.android.ui.theme.FileHubFileFolder
import filehub.android.ui.theme.FileHubFileFolderDark
import filehub.android.ui.theme.FileHubFileImage
import filehub.android.ui.theme.FileHubFileImageDark
import filehub.android.ui.theme.FileHubFilePdf
import filehub.android.ui.theme.FileHubFilePdfDark
import filehub.android.ui.theme.FileHubFileSheet
import filehub.android.ui.theme.FileHubFileSheetDark
import filehub.android.ui.theme.FileHubFileSlide
import filehub.android.ui.theme.FileHubFileSlideDark
import filehub.android.ui.theme.FileHubFileVideoDark
import filehub.android.ui.theme.FileHubFileOrange

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
    FileKind.Apk,
    FileKind.Installer,
    -> R.drawable.ic_file_installer
    FileKind.App -> R.drawable.ic_file_app
    FileKind.Library -> R.drawable.ic_file_library
    FileKind.Shortcut -> R.drawable.ic_file_shortcut
    FileKind.Config -> R.drawable.ic_file_config
    FileKind.Ebook -> R.drawable.ic_file_ebook
    FileKind.Font -> R.drawable.ic_file_font
    FileKind.DiskImage -> R.drawable.ic_file_disk
    FileKind.Design -> R.drawable.ic_file_design
    FileKind.Torrent -> R.drawable.ic_file_file
    FileKind.Text -> R.drawable.ic_file_text
    FileKind.File -> R.drawable.ic_file_file
}

@Composable
fun fileIconColor(kind: FileKind): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    fun pick(light: Color, darkColor: Color): Color = if (dark) darkColor else light
    return when (kind) {
        FileKind.Folder -> pick(FileHubFileFolder, FileHubFileFolderDark)
        FileKind.Image -> pick(FileHubFileImage, FileHubFileImageDark)
        FileKind.Video -> pick(FileHubFileOrange, FileHubFileVideoDark)
        FileKind.Audio -> pick(FileHubFileAudio, FileHubFileAudioDark)
        FileKind.Pdf -> pick(FileHubFilePdf, FileHubFilePdfDark)
        FileKind.Document -> pick(FileHubFileDoc, FileHubFileDocDark)
        FileKind.Sheet -> pick(FileHubFileSheet, FileHubFileSheetDark)
        FileKind.Slide -> pick(FileHubFileSlide, FileHubFileSlideDark)
        FileKind.Database -> pick(FileHubFileSheet, FileHubFileSheetDark)
        FileKind.Archive -> pick(FileHubFileArchive, FileHubFileArchiveDark)
        FileKind.Code -> pick(FileHubFileCode, FileHubFileCodeDark)
        FileKind.Apk,
        FileKind.Installer,
        FileKind.DiskImage,
        -> pick(FileHubFileArchive, FileHubFileArchiveDark)
        FileKind.App -> pick(FileHubFileOrange, FileHubFileVideoDark)
        FileKind.Library,
        FileKind.Config,
        -> pick(FileHubFileCode, FileHubFileCodeDark)
        FileKind.Shortcut,
        FileKind.Font,
        FileKind.Ebook,
        -> pick(FileHubFileDoc, FileHubFileDocDark)
        FileKind.Design -> pick(FileHubFileImage, FileHubFileImageDark)
        FileKind.Torrent,
        FileKind.Text,
        FileKind.File,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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
    FileKind.Installer -> stringResource(R.string.file_type_label_installer)
    FileKind.App -> stringResource(R.string.file_type_label_app)
    FileKind.Library -> stringResource(R.string.file_type_label_library)
    FileKind.Shortcut -> stringResource(R.string.file_type_label_shortcut)
    FileKind.Config -> stringResource(R.string.file_type_label_config)
    FileKind.Ebook -> stringResource(R.string.file_type_label_ebook)
    FileKind.Font -> stringResource(R.string.file_type_label_font)
    FileKind.DiskImage -> stringResource(R.string.file_type_label_disk_image)
    FileKind.Design -> stringResource(R.string.file_type_label_design)
    FileKind.Torrent -> stringResource(R.string.file_type_label_torrent)
    FileKind.File -> stringResource(R.string.file_type_label_file)
}
