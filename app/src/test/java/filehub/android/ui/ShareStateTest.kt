package filehub.android.ui

import filehub.android.core.model.ShareInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareStateTest {
    private fun share(
        expiresAt: Long? = null,
        expired: Boolean = false,
        revoked: Boolean = false,
    ) = ShareInfo(
        id = 1,
        kind = "single",
        itemCount = 1,
        realPath = "docs/report.pdf",
        isDir = false,
        hasPassword = false,
        expiresAt = expiresAt,
        expired = expired,
        readOnly = true,
        revoked = revoked,
        createdAt = 1_700_000_000,
        visitCount = 0,
    )

    @Test
    fun serverExpiredFlagWinsOverMissingTimestamp() {
        assertTrue(share(expired = true).isExpired(nowSeconds = 1_700_000_000))
    }

    @Test
    fun pastTimestampIsExpired() {
        assertTrue(share(expiresAt = 1_699_999_999).isExpired(nowSeconds = 1_700_000_000))
        assertFalse(share(expiresAt = 1_700_000_001).isExpired(nowSeconds = 1_700_000_000))
        assertFalse(share(expiresAt = null).isExpired(nowSeconds = 1_700_000_000))
    }

    @Test
    fun stoppedCoversRevokedAndExpiredShares() {
        assertTrue(share(revoked = true).isStopped(nowSeconds = 1_700_000_000))
        assertTrue(share(expiresAt = 1_699_999_999).isStopped(nowSeconds = 1_700_000_000))
        assertFalse(share().isStopped(nowSeconds = 1_700_000_000))
    }

    @Test
    fun expiredSharesGetOneDayWhenEnabled() {
        assertEquals(
            1_700_086_400L,
            share(expiresAt = 1_699_999_999).enableExpiry(nowSeconds = 1_700_000_000),
        )
        assertEquals(1_700_086_400L, share(expired = true).enableExpiry(nowSeconds = 1_700_000_000))
        assertEquals(null, share().enableExpiry(nowSeconds = 1_700_000_000))
    }
}
