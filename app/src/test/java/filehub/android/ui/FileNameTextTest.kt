package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameTextTest {
    @Test
    fun keepsCommonSuffixes() {
        assertEquals("photo" to ".jpg", splitFileName("photo.jpg"))
        assertEquals("file" to ".7z", splitFileName("file.7z"))
        assertEquals("file" to ".3gp", splitFileName("file.3gp"))
        assertEquals("archive" to ".tar.gz", splitFileName("archive.tar.gz"))
        assertEquals("termux" to ".apk", splitFileName("termux.apk"))
        assertEquals("bundle" to ".aab", splitFileName("bundle.aab"))
        assertEquals("book" to ".epub", splitFileName("book.epub"))
        assertEquals("disc" to ".iso", splitFileName("disc.iso"))
        assertEquals("design" to ".psd", splitFileName("design.psd"))
        assertEquals("font" to ".ttf", splitFileName("font.ttf"))
        assertEquals("magnet" to ".torrent", splitFileName("magnet.torrent"))
        assertEquals("movie" to ".srt", splitFileName("movie.srt"))
    }

    @Test
    fun doesNotTreatNamesAsSuffixes() {
        assertEquals("v1.2.3" to "", splitFileName("v1.2.3"))
        assertEquals(".gitignore" to "", splitFileName(".gitignore"))
        assertEquals("Dockerfile" to "", splitFileName("Dockerfile"))
        assertEquals("README" to "", splitFileName("README"))
        assertEquals("file.2026" to "", splitFileName("file.2026"))
        assertEquals("file." to "", splitFileName("file."))
        assertEquals("my.file.name" to "", splitFileName("my.file.name"))
    }

    @Test
    fun singleDotUnknownSuffixIsKept() {
        assertEquals("backup" to ".xyz", splitFileName("backup.xyz"))
    }
}
