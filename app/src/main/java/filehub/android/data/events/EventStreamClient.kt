package filehub.android.data.events

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Foreground-only SSE connection for the active server.
 *
 * The client owns exactly one connection and reconnects on its own. It never
 * runs in the background and never retries a target that answered 401/404, so
 * polling remains the fallback for old servers and for expired tokens.
 */
class EventStreamClient(
    private val scope: CoroutineScope,
    private val onUnauthorized: () -> Unit = {},
    private val httpClient: OkHttpClient = buildHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val backoffMs: (Int) -> Long = ::defaultBackoffMs,
    private val log: (String) -> Unit = {},
) : DefaultLifecycleObserver {
    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow<EventStreamState>(EventStreamState.Idle)
    val state: StateFlow<EventStreamState> = _state.asStateFlow()

    @Volatile
    private var foreground = false

    @Volatile
    private var target: StreamTarget? = null

    @Volatile
    private var blockedTarget: StreamTarget? = null

    @Volatile
    private var activeCall: Call? = null

    private var connectionJob: Job? = null

    fun updateTarget(next: StreamTarget?) {
        if (next == target) return
        target = next
        if (blockedTarget != null && blockedTarget != next) blockedTarget = null
        restart()
    }

    fun isConnected(profileId: String): Boolean {
        val current = _state.value
        return current is EventStreamState.Connected && current.profileId == profileId
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        restart()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        stopConnection()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        foreground = false
        stopConnection()
    }

    fun shutdown() {
        foreground = false
        target = null
        stopConnection()
    }

    private fun restart() {
        connectionJob?.cancel()
        activeCall?.cancel()
        connectionJob = null
        activeCall = null

        val current = target
        if (!foreground || current == null || current == blockedTarget) {
            _state.value = EventStreamState.Idle
            return
        }
        _state.value = EventStreamState.Connecting
        connectionJob = scope.launch { runConnectionLoop(current) }
    }

    private fun stopConnection() {
        connectionJob?.cancel()
        activeCall?.cancel()
        connectionJob = null
        activeCall = null
        _state.value = EventStreamState.Idle
    }

    private suspend fun runConnectionLoop(initial: StreamTarget) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            if (!foreground || target != initial) return

            val call = httpClient.newCall(buildRequest(initial))
            activeCall = call
            var connectedAt = 0L
            var failure: Throwable? = null
            var shouldReconnect = true
            try {
                val response = call.execute()
                shouldReconnect = response.use { body ->
                    stream(body, call, initial) { startedAt -> connectedAt = startedAt }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure = error
            } finally {
                if (activeCall === call) activeCall = null
            }

            if (!currentCoroutineContext().isActive) return
            if (!foreground || target != initial) return
            if (!shouldReconnect) return

            if (connectedAt > 0 && now() - connectedAt >= STABLE_CONNECTION_MS) {
                attempt = 0
            }
            val retryAfter = (failure as? SseHttpException)?.retryAfterMs
            val delayMs = retryAfter ?: backoffMs(attempt)
            attempt = min(attempt + 1, MAX_ATTEMPT)
            _state.value = EventStreamState.Reconnecting(initial.profileId, attempt, delayMs)
            log("reconnecting attempt=$attempt delay=$delayMs")
            delay(delayMs)
        }
    }

    private suspend fun stream(
        response: Response,
        call: Call,
        target: StreamTarget,
        onConnected: (Long) -> Unit,
    ): Boolean {
        when (response.code) {
            HTTP_UNAUTHORIZED -> {
                blockedTarget = target
                _state.value = EventStreamState.Unauthorized(target.profileId)
                log("unauthorized")
                onUnauthorized()
                return false
            }
            HTTP_NOT_FOUND, HTTP_METHOD_NOT_ALLOWED -> {
                blockedTarget = target
                _state.value = EventStreamState.Unsupported(target.profileId)
                log("unsupported http=${response.code}")
                return false
            }
        }
        if (!response.isSuccessful) {
            throw SseHttpException(
                code = response.code,
                retryAfterMs = response.header("Retry-After")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.times(1000)
                    ?.takeIf { it > 0 },
            )
        }

        val connectedAt = now()
        onConnected(connectedAt)
        _state.value = EventStreamState.Connected(target.profileId, connectedAt)
        log("connected")
        _events.tryEmit(
            ServerEvent(
                profileId = target.profileId,
                id = null,
                name = ServerEventNames.RESYNC,
                data = null,
                rawData = null,
                receivedAt = connectedAt,
            ),
        )

        val lastActivity = AtomicLong(connectedAt)
        val watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (now() - lastActivity.get() >= IDLE_TIMEOUT_MS) {
                    // Closing the call is the only way to interrupt a blocking
                    // read on the shared source.
                    call.cancel()
                    return@launch
                }
            }
        }
        try {
            val source = response.body.source()
            val parser = SseParser()
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: return true
                lastActivity.set(now())
                val frame = parser.consumeLine(line) ?: continue
                emit(target, frame)
            }
            return true
        } finally {
            watchdog.cancel()
        }
    }

    private fun emit(target: StreamTarget, frame: SseEventFrame) {
        val data = if (frame.data.isBlank()) {
            null
        } else {
            runCatching { json.parseToJsonElement(frame.data) }.getOrNull() as? JsonObject
        }
        _events.tryEmit(
            ServerEvent(
                profileId = target.profileId,
                id = frame.id,
                name = frame.name,
                data = data,
                rawData = frame.data.takeIf { it.isNotBlank() },
                receivedAt = now(),
            ),
        )
        log("event=${frame.name} id=${frame.id ?: "-"}")
    }

    private fun buildRequest(target: StreamTarget): Request {
        val base = target.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid server URL")
        val url = base.newBuilder()
            .addPathSegments("api/v1/events")
            .build()
        return Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Authorization", "Bearer ${target.token}")
            .build()
    }

    private class SseHttpException(
        val code: Int,
        val retryAfterMs: Long?,
    ) : IOException("SSE request failed with HTTP $code")

    private companion object {
        const val EVENT_BUFFER = 256
        const val WATCHDOG_INTERVAL_MS = 5_000L
        const val IDLE_TIMEOUT_MS = 60_000L
        const val STABLE_CONNECTION_MS = 30_000L
        const val MAX_ATTEMPT = 5
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)

        fun defaultBackoffMs(attempt: Int): Long {
            val base = BACKOFF_MS[attempt.coerceIn(0, BACKOFF_MS.lastIndex)]
            val jitter = (base * 0.2).toLong()
            if (jitter <= 0) return base
            return base + Random.nextLong(-jitter, jitter + 1)
        }

        fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
