package filehub.android.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScheduleTest {
    @Test
    fun scheduleIntervalsMatchTheUiOptions() {
        assertNull(scheduleIntervalMinutes("manual"))
        assertEquals(15L, scheduleIntervalMinutes("15m"))
        assertEquals(60L, scheduleIntervalMinutes("hourly"))
        assertEquals(1_440L, scheduleIntervalMinutes("daily"))
        assertEquals(10_080L, scheduleIntervalMinutes("weekly"))
    }

    @Test
    fun manualAndUnknownSchedulesNeverBecomeOverdue() {
        assertFalse(isScheduleOverdue(lastRunAt = null, schedule = "15m", now = 10_000))
        assertFalse(isScheduleOverdue(lastRunAt = 1, schedule = "manual", now = 10_000))
        assertFalse(isScheduleOverdue(lastRunAt = 1, schedule = "unknown", now = 10_000))
    }

    @Test
    fun overdueUsesTheConfiguredInterval() {
        assertTrue(isScheduleOverdue(lastRunAt = 0, schedule = "15m", now = 15 * 60 + 1))
        assertFalse(isScheduleOverdue(lastRunAt = 0, schedule = "15m", now = 15 * 60))
        assertTrue(isScheduleOverdue(lastRunAt = 0, schedule = "hourly", now = 60 * 60 + 1))
    }
}
