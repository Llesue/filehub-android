package filehub.android.data.sync

import filehub.android.data.api.FileHubApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDirEnsurerTest {

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

    @Test
    fun existingDirectoryMakesNoMkdirRequest() = runBlocking {
        server.enqueue(json("""{"name":"微信","is_dir":true}"""))

        ensurer().ensure("Phone/Photos/微信")

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/v1/properties"))
    }

    @Test
    fun createsMissingSegmentsAndCachesThePath() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))   // full path
        server.enqueue(MockResponse().setResponseCode(404))   // Phone
        server.enqueue(json("""{"path":"ok"}"""))             // mkdir Phone
        server.enqueue(MockResponse().setResponseCode(404))   // Phone/Photos
        server.enqueue(json("""{"path":"ok"}"""))             // mkdir Phone/Photos
        server.enqueue(json("""{"path":"ok"}"""))             // mkdir final

        val ensurer = ensurer()
        ensurer.ensure("Phone/Photos/微信")
        assertEquals(6, server.requestCount)

        ensurer.ensure("Phone/Photos/微信")
        assertEquals(6, server.requestCount)
    }

    @Test
    fun skipsParentsThatAlreadyExist() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json("""{"name":"Phone","is_dir":true}"""))
        server.enqueue(json("""{"name":"Photos","is_dir":true}"""))
        server.enqueue(json("""{"path":"ok"}"""))

        ensurer().ensure("Phone/Photos/微信")

        assertEquals(4, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
    }

    private fun ensurer() = RemoteDirEnsurer(
        api = FileHubApi(server.url("/").toString(), token = "test-token"),
        rootId = 1,
    )

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
