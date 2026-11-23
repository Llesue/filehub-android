package filehub.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientUpdateCheckerTest {
    @Test
    fun comparesNumericSegments() {
        assertTrue(ClientUpdateChecker.isNewer("0.1.2", "0.1.1"))
        assertTrue(ClientUpdateChecker.isNewer("0.10.0", "0.9.9"))
        assertTrue(ClientUpdateChecker.isNewer("1.0", "0.99.99"))
        assertFalse(ClientUpdateChecker.isNewer("0.1.1", "0.1.1"))
        assertFalse(ClientUpdateChecker.isNewer("0.1.0", "0.1.1"))
    }

    @Test
    fun ignoresPrefixesAndSuffixes() {
        assertTrue(ClientUpdateChecker.isNewer("v0.1.2", "0.1.1-debug"))
        assertFalse(ClientUpdateChecker.isNewer("v0.1.1", "0.1.1"))
        assertFalse(ClientUpdateChecker.isNewer("0.1.2-beta", "0.1.2"))
    }

    @Test
    fun padsMissingSegmentsWithZero() {
        assertFalse(ClientUpdateChecker.isNewer("1.0", "1.0.0"))
        assertTrue(ClientUpdateChecker.isNewer("1.0.1", "1.0"))
    }
}
