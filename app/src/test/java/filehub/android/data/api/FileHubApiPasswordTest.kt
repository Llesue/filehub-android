package filehub.android.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileHubApiPasswordTest {

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
    fun `forced password change stops before creating a device token`() = runBlocking {
        server.enqueue(json("""{"initialized":true}"""))
        server.enqueue(json("""{"name":"Test"}"""))
        server.enqueue(
            json(
                """{"user":{"id":7,"username":"temp","must_change_password":true},"csrf_token":"csrf"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val error = runCatching {
            FileHubApi.loginAndCreateToken(
                baseUrl = server.url("/").toString().trimEnd('/'),
                username = "temp",
                password = "initial",
                deviceName = "test-device",
            )
        }.exceptionOrNull()

        assertTrue(error is PasswordChangeRequiredException)
        assertEquals(server.url("/").toString().trimEnd('/'), (error as PasswordChangeRequiredException).serverUrl)
        val paths = buildList {
            repeat(server.requestCount) { add(server.takeRequest().path.orEmpty()) }
        }
        assertTrue(paths.any { it.startsWith("/api/v1/setup/status") })
        assertTrue(paths.any { it.startsWith("/api/v1/login") })
        assertTrue(paths.none { it.startsWith("/api/v1/auth/tokens") })
    }


    @Test
    fun `forced password change is submitted before the token is created`() = runBlocking {
        server.enqueue(json("""{"initialized":true}"""))
        server.enqueue(json("""{"name":"Test"}"""))
        server.enqueue(
            json(
                """{"user":{"id":7,"username":"temp","must_change_password":true},"csrf_token":"csrf"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204)) // forced change
        server.enqueue(json("""{"id":3,"token":"device-token","scope":"write"}"""))
        server.enqueue(MockResponse().setResponseCode(204)) // logout
        server.enqueue(json("""{"user":{"id":7,"username":"temp","must_change_password":false}}"""))
        server.enqueue(json("""{"roots":[{"id":1,"alias":"Files"}]}"""))
        server.enqueue(json("""{"preferences":{"avatar_version":0}}"""))

        val login = FileHubApi.loginAndCreateToken(
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "temp",
            password = "initial",
            newPassword = "a-new-password",
            deviceName = "test-device",
        )

        assertEquals("device-token", login.token)
        val requests = buildList {
            repeat(server.requestCount) { add(server.takeRequest()) }
        }
        val forced = requests.first { it.path.orEmpty().startsWith("/api/v1/me/password/forced") }
        assertTrue(forced.body.readUtf8().contains("a-new-password"))
        val tokenRequest = requests.first { it.path.orEmpty().startsWith("/api/v1/auth/tokens") }
        assertTrue(
            requests.indexOf(forced) < requests.indexOf(tokenRequest),
        )
    }

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
