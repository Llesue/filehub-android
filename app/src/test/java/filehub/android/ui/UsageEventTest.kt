package filehub.android.ui

import filehub.android.data.api.UsageDto
import filehub.android.data.events.UsageUpdatedPayload
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventTest {

    @Test
    fun eventUpdatesBytesAndClearsComputingState() {
        val current = UsageDto(
            quotaBytes = 1000,
            usedBytes = 200,
            entryCount = 7,
            maxEntries = 50,
            disks = listOf(JsonPrimitive("disk-a")),
            computedAt = 10,
            stale = true,
            computing = true,
        )

        val merged = mergeUsageEvent(
            current,
            UsageUpdatedPayload(usedBytes = 300, quotaBytes = 2000, computedAt = 20),
        )

        assertEquals(300L, merged.usedBytes)
        assertEquals(2000L, merged.quotaBytes)
        assertEquals(20L, merged.computedAt)
        assertEquals(7L, merged.entryCount)
        assertEquals(50L, merged.maxEntries)
        assertEquals("disk-a", (merged.disks.single() as JsonPrimitive).content)
        assertFalse(merged.computing)
        assertFalse(merged.stale)
    }

    @Test
    fun missingFieldsKeepTheCurrentValues() {
        val current = UsageDto(quotaBytes = 1000, usedBytes = 200, computedAt = 10)

        val merged = mergeUsageEvent(current, UsageUpdatedPayload(usedBytes = 250))

        assertEquals(250L, merged.usedBytes)
        assertEquals(1000L, merged.quotaBytes)
        assertEquals(10L, merged.computedAt)
    }

    @Test
    fun firstEventPopulatesWhatItHas() {
        val merged = mergeUsageEvent(null, UsageUpdatedPayload(usedBytes = 5, quotaBytes = 9))

        assertEquals(5L, merged.usedBytes)
        assertEquals(9L, merged.quotaBytes)
        assertTrue(merged.computing.not())
    }
}
