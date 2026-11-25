package filehub.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressOrderTest {
    @Test
    fun configuredOrderIsKeptAndBaseUrlDoesNotJumpTheQueue() {
        assertEquals(
            listOf(
                "http://192.0.2.10:8080",
                "https://example.com",
            ),
            orderedAddresses(
                addresses = listOf(
                    "http://192.0.2.10:8080",
                    "https://example.com",
                ),
                baseUrl = "https://example.com",
            ),
        )
    }

    @Test
    fun duplicatesAreRemovedAndEmptyListUsesBaseUrl() {
        assertEquals(
            listOf("http://192.0.2.10:8080"),
            orderedAddresses(
                addresses = listOf(
                    "http://192.0.2.10:8080",
                    "http://192.0.2.10:8080/",
                ),
                baseUrl = "https://example.com",
            ),
        )
        assertEquals(
            listOf("https://example.com"),
            orderedAddresses(emptyList(), "https://example.com"),
        )
    }
}
