package filehub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TimeFormatTest {
    private val now = 1_700_000_000_000L

    @Test
    fun justNow() {
        val result = relativeTimeOf(now, now - 30_000)
        assertEquals(RelativeUnit.JustNow, result.unit)
        assertFalse(result.future)
    }

    @Test
    fun minutes() {
        val result = relativeTimeOf(now, now - 5 * 60_000)
        assertEquals(RelativeUnit.Minutes, result.unit)
        assertEquals(5, result.amount)
    }

    @Test
    fun hours() {
        val result = relativeTimeOf(now, now - 3 * 3_600_000)
        assertEquals(RelativeUnit.Hours, result.unit)
        assertEquals(3, result.amount)
    }

    @Test
    fun days() {
        val result = relativeTimeOf(now, now - 3L * 86_400_000)
        assertEquals(RelativeUnit.Days, result.unit)
        assertEquals(3, result.amount)
    }

    @Test
    fun weeks() {
        val result = relativeTimeOf(now, now - 14L * 86_400_000)
        assertEquals(RelativeUnit.Weeks, result.unit)
        assertEquals(2, result.amount)
    }

    @Test
    fun months() {
        val result = relativeTimeOf(now, now - 330L * 86_400_000)
        assertEquals(RelativeUnit.Months, result.unit)
        assertEquals(11, result.amount)
    }

    @Test
    fun years() {
        assertEquals(1, relativeTimeOf(now, now - 365L * 86_400_000).amount)
        assertEquals(3, relativeTimeOf(now, now - 3 * 365L * 86_400_000).amount)
        assertEquals(10, relativeTimeOf(now, now - 10 * 365L * 86_400_000).amount)
    }

    @Test
    fun absoluteTimeUsesRequestedTimeZone() {
        val shanghai = TimeZone.getTimeZone("Asia/Shanghai")
        assertEquals(
            "2023-11-15 06:13:20",
            absoluteTimeText(1_700_000_000L, shanghai, Locale.US),
        )
        assertEquals("—", absoluteTimeText(null, shanghai, Locale.US))
        assertEquals("—", absoluteTimeText(0L, shanghai, Locale.US))
    }

    @Test
    fun future() {
        val result = relativeTimeOf(now, now + 5 * 60_000)
        assertTrue(result.future)
        assertEquals(RelativeUnit.Minutes, result.unit)
        assertEquals(5, result.amount)
    }

    @Test
    fun oneDayIsYesterdayOrTomorrow() {
        assertEquals(1, relativeTimeOf(now, now - 86_400_000L).amount)
        assertEquals(RelativeUnit.Days, relativeTimeOf(now, now - 86_400_000L).unit)
        assertEquals(1, relativeTimeOf(now, now + 86_400_000L).amount)
        assertTrue(relativeTimeOf(now, now + 86_400_000L).future)
    }
}
