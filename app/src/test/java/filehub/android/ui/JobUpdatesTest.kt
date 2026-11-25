package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobUpdatesTest {

    private val job = ActiveJob(
        id = "job-1",
        label = "archive.zip",
        status = "queued",
        progress = 0,
        total = 0,
        bytesDone = 0,
        bytesTotal = 0,
    )

    @Test
    fun keepsLabelAndBytesWhenEventOnlyCarriesItems() {
        val updated = mergeJobUpdate(
            existing = job.copy(bytesDone = 50, bytesTotal = 100),
            status = "running",
            progress = 3,
            total = 10,
        )

        assertEquals("archive.zip", updated.label)
        assertEquals(50L, updated.bytesDone)
        assertEquals(100L, updated.bytesTotal)
        assertEquals(3, updated.progress)
        assertEquals(10, updated.total)
    }

    @Test
    fun blankStatusKeepsThePreviousStatus() {
        val updated = mergeJobUpdate(
            existing = job.copy(status = "running"),
            status = "",
            progress = 4,
            total = 9,
        )

        assertEquals("running", updated.status)
    }

    @Test
    fun pollingBytesOverrideOnlyWhenProvided() {
        val updated = mergeJobUpdate(
            existing = job.copy(bytesDone = 1, bytesTotal = 2),
            status = "running",
            progress = 2,
            total = 4,
            bytesDone = 30,
            bytesTotal = 40,
        )

        assertEquals(30L, updated.bytesDone)
        assertEquals(40L, updated.bytesTotal)
    }

    @Test
    fun onlyTerminalStatusesStopPolling() {
        assertFalse(isTerminalJobStatus("queued"))
        assertFalse(isTerminalJobStatus("running"))
        assertTrue(isTerminalJobStatus("completed"))
        assertTrue(isTerminalJobStatus("failed"))
        assertTrue(isTerminalJobStatus("cancelled"))
    }
}
