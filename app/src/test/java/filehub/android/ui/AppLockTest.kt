package filehub.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    @Test
    fun relocksAtTheOneMinuteBoundary() {
        assertTrue(shouldRelock(1_000L, 1_000L + APP_LOCK_TIMEOUT_MS))
    }

    @Test
    fun staysUnlockedBeforeTheTimeout() {
        assertFalse(shouldRelock(1_000L, 1_000L + APP_LOCK_TIMEOUT_MS - 1))
    }

    @Test
    fun ignoresMissingBackgroundTimestamp() {
        assertFalse(shouldRelock(0L, 10 * APP_LOCK_TIMEOUT_MS))
    }
}
