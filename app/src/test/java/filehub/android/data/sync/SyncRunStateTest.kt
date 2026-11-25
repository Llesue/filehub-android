package filehub.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRunStateTest {

    @Test
    fun `stale active run is reported as interrupted`() {
        val run = run(phase = SyncPhase.SYNCING, heartbeatAt = 1_000)
        assertEquals(SyncPhase.INTERRUPTED, run.effectivePhase(nowSeconds = 1_500))
    }

    @Test
    fun `recent heartbeat keeps the active phase`() {
        val run = run(phase = SyncPhase.SYNCING, heartbeatAt = 1_000)
        assertEquals(SyncPhase.SYNCING, run.effectivePhase(nowSeconds = 1_060))
    }

    @Test
    fun `terminal phases are never rewritten`() {
        val run = run(phase = SyncPhase.COMPLETED, heartbeatAt = 1_000)
        assertEquals(SyncPhase.COMPLETED, run.effectivePhase(nowSeconds = 99_999))
    }

    @Test
    fun `run without heartbeat keeps its phase`() {
        val run = run(phase = SyncPhase.QUEUED, heartbeatAt = 0)
        assertEquals(SyncPhase.QUEUED, run.effectivePhase(nowSeconds = 99_999))
    }

    @Test
    fun `stop is not treated as an active phase`() {
        assertEquals(false, SyncPhase.STOPPED.isActive)
        assertEquals(true, SyncPhase.STOPPING.isActive)
        assertEquals(false, SyncPhase.PARTIAL.isActive)
    }

    private fun run(phase: SyncPhase, heartbeatAt: Long) = SyncRunEntity(
        profileId = "p",
        kind = SyncRunKind.PHOTO.value,
        runId = "r",
        phase = phase.name,
        heartbeatAt = heartbeatAt,
    )
}
