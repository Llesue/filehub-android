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
        assertEquals(FileKind.Text, detectKind(".gitignore", "", false))
    }
}
