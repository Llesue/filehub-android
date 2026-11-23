package filehub.android.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FileHubApiDirSizeTest {
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
    fun queuedLocalScanHasNullSizeAndJobId() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"queued","size":null,"indexed":false,"partial":false,
                   "stale":false,"refreshing":true,"error":null,"job_id":"abc"}""",
            ),
        )
        val result = api().dirSize(1, "archive")
        assertEquals("queued", result.status)
        assertNull(result.size)
    }

    @Test
    fun runningLocalScanParses() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"running","size":null,"indexed":false,"partial":false,
                   "stale":false,"refreshing":true,"error":null}""",
            ),
        )
        val result = api().dirSize(1, "archive")
        assertEquals("running", result.status)
        assertNull(result.size)
    }

    @Test
    fun readyLocalScanCarriesSize() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ready","size":123456,"indexed":false,"partial":true,
                   "stale":false,"refreshing":false,"error":null}""",
            ),
        )
        val result = api().dirSize(1, "archive")
        assertEquals("ready", result.status)
        assertEquals(123456L, result.size)
        assertEquals(true, result.partial)
    }
}
