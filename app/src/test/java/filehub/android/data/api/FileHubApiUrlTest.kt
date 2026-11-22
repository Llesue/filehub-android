package filehub.android.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileHubApiUrlTest {

    @Test
    fun addsHttpSchemeAndRemovesTrailingSlash() {
        assertEquals(
            "http://nas.local:8080",
            FileHubApi.normalizeBaseUrl("nas.local:8080/"),
        )
    }

    @Test
    fun keepsHttpsAndStripsApiPrefix() {
        assertEquals(
            "https://fh.example.com",
            FileHubApi.normalizeBaseUrl("https://fh.example.com/api/v1/"),
        )
    }

    @Test
    fun keepsOptionalSubPath() {
        assertEquals(
            "https://example.com/filehub",
            FileHubApi.normalizeBaseUrl("https://example.com/filehub/"),
        )
    }

    @Test
    fun rejectsBlank() {
        assertThrows(FileHubApiException::class.java) {
            FileHubApi.normalizeBaseUrl("   ")
        }
    }
}
