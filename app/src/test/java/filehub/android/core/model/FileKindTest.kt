package filehub.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileKindTest {
    @Test
    fun windowsScriptsAreCode() {
        assertEquals(FileKind.Code, detectKind("test.bat", "", false))
        assertEquals(FileKind.Code, detectKind("deploy.CMD", "", false))
        assertEquals(FileKind.Code, detectKind("setup.ps1", "", false))
    }

    @Test
    fun knownNamesAreDetected() {
        assertEquals(FileKind.Code, detectKind("Dockerfile", "", false))
        assertEquals(FileKind.Text, detectKind("README", "", false))
        assertEquals(FileKind.File, detectKind(".gitignore", "", false))
    }

    @Test
    fun webCategoriesAreDetected() {
        assertEquals(FileKind.App, detectKind("setup.exe", "", false))
        assertEquals(FileKind.Library, detectKind("driver.sys", "", false))
        assertEquals(FileKind.Installer, detectKind("package.msi", "", false))
        assertEquals(FileKind.Apk, detectKind("app.apk", "", false))
        assertEquals(FileKind.Shortcut, detectKind("site.url", "", false))
        assertEquals(FileKind.Config, detectKind("app.ini", "", false))
        assertEquals(FileKind.Config, detectKind(".env", "", false))
        assertEquals(FileKind.Config, detectKind("app.properties", "", false))
        assertEquals(FileKind.Image, detectKind("photo.svg", "", false))
        assertEquals(FileKind.Audio, detectKind("book.m4b", "", false))
        assertEquals(FileKind.Pdf, detectKind("scan.ofd", "", false))
        assertEquals(FileKind.DiskImage, detectKind("image.wim", "", false))
        assertEquals(FileKind.Design, detectKind("mockup.xd", "", false))
        assertEquals(FileKind.Sheet, detectKind("table.csv", "", false))
        assertEquals(FileKind.Slide, detectKind("deck.pptx", "", false))
        assertEquals(FileKind.Document, detectKind("letter.docx", "", false))
        assertEquals(FileKind.Database, detectKind("cache.db", "", false))
        assertEquals(FileKind.Archive, detectKind("bundle.aab", "", false))
        assertEquals(FileKind.Torrent, detectKind("linux.torrent", "", false))
        assertEquals(FileKind.File, detectKind("unknown.bin", "", false))
    }
}
