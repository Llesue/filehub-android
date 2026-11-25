package filehub.android.data.events

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventStreamClientTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = error("lifecycle is not used by the client")
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun sendsBearerAndAcceptStreamHeaders() = runBlocking {
        server.enqueue(
            sseResponse(
                "id: 1\nevent: usage.updated\ndata: {\"used_bytes\":42}\n\n",
            ),
        )
        val client = client()
        val received = async(Dispatchers.Default) {
            client.events.first { it.name == ServerEventNames.USAGE_UPDATED }
        }
        delay(100)
        client.updateTarget(target("secret-token"))
        client.onStart(owner)

        val event = withTimeout(5_000) { received.await() }
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/api/v1/events", request!!.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
        assertEquals("text/event-stream", request.getHeader("Accept"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
        assertEquals("1", event.id)

        client.shutdown()
    }

    @Test
    fun reconnectsAfterTheStreamEnds() = runBlocking {
        server.enqueue(sseResponse("id: 1\nevent: job.updated\ndata: {\"id\":\"a\"}\n\n"))
        server.enqueue(sseResponse("id: 2\nevent: job.updated\ndata: {\"id\":\"b\"}\n\n"))
        val client = client()
        val received = async(Dispatchers.Default) {
            client.events
                .filter { it.name == ServerEventNames.JOB_UPDATED }
                .take(2)
                .toList()
        }
        delay(100)
        client.updateTarget(target("token"))
        client.onStart(owner)

        val events = withTimeout(8_000) { received.await() }
        assertEquals(listOf("1", "2"), events.map { it.id })
        assertTrue(server.requestCount >= 2)

        client.shutdown()
    }

    @Test
    fun stopsOnUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        var unauthorized = false
        val client = EventStreamClient(
            scope = scope,
            onUnauthorized = { unauthorized = true },
            backoffMs = { 50L },
        )
        val state = async(Dispatchers.Default) {
            client.state.first { it is EventStreamState.Unauthorized }
        }
        delay(100)
        client.updateTarget(target("expired"))
        client.onStart(owner)

        withTimeout(5_000) { state.await() }
        delay(200)
        assertEquals(1, server.requestCount)
        assertTrue(unauthorized)

        client.onStop(owner)
    }

    @Test
    fun marksOldServerUnsupportedWithoutRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val client = client()
        val state = async(Dispatchers.Default) {
            client.state.first { it is EventStreamState.Unsupported }
        }
        delay(100)
        client.updateTarget(target("token"))
        client.onStart(owner)

        withTimeout(5_000) { state.await() }
        delay(200)
        assertEquals(1, server.requestCount)

        client.onStop(owner)
    }

    @Test
    fun retriesAfterServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(sseResponse("id: 3\nevent: resync\ndata: {}\n\n"))
        val client = client()
        val received = async(Dispatchers.Default) {
            client.events.first { it.name == ServerEventNames.RESYNC && it.id == "3" }
        }
        delay(100)
        client.updateTarget(target("token"))
        client.onStart(owner)

        val event = withTimeout(5_000) { received.await() }
        assertEquals("3", event.id)
        assertTrue(server.requestCount >= 2)

        client.shutdown()
    }

    private fun client(): EventStreamClient = EventStreamClient(
        scope = scope,
        backoffMs = { 50L },
    )

    private fun target(token: String): StreamTarget {
        return StreamTarget(
            profileId = "profile-1",
            baseUrl = server.url("/").toString().trimEnd('/'),
            token = token,
        )
    }

    private fun sseResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(body)
}
