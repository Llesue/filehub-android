package filehub.android.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileHubApiRootsTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(): FileHubApi = FileHubApi(server.url("/").toString(), "token")

    @Test
    fun readsOnlineFlagForLocalAndRemoteRoots() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"roots":[
                    {"id":1,"alias":"local","read_only":false,"kind":null,"online":true},
                    {"id":2,"alias":"peer","read_only":true,"kind":"remote","mount_id":9,"online":false}
                ]}""",
            ),
        )
        val roots = api().roots()
        assertEquals(2, roots.size)
        assertEquals(true, roots[0].online)
        assertEquals(false, roots[1].online)
        assertTrue(roots[1].isRemote)
    }

    @Test
    fun oldServerWithoutOnlineFieldIsUnknown() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"roots":[{"id":1,"alias":"local","read_only":false}]}""",
            ),
        )
        val roots = api().roots()
        assertNull(roots.single().online)
        assertFalse(roots.single().isRemote)
    }

    @Test
    fun oldServerErrorIsReportedWithStatus() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"INTERNAL_ERROR"}}"""))
        val error = assertThrows(FileHubApiException::class.java) {
            runBlocking { api().roots() }
        }
        assertEquals(500, error.statusCode)
    }
}
