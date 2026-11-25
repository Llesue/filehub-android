package filehub.android.data.sync

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncHasherTest {

    @Test
    fun `hashes abc with the standard sha256 vector`() = runBlocking {
        val hash = SyncHasher.digest(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hash,
        )
    }

    @Test
    fun `hashes the empty input`() = runBlocking {
        val hash = SyncHasher.digest(ByteArrayInputStream(ByteArray(0)))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hash,
        )
    }

    @Test
    fun `hashes input larger than the read buffer`() = runBlocking {
        val bytes = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        val first = SyncHasher.digest(ByteArrayInputStream(bytes))
        val second = SyncHasher.digest(ByteArrayInputStream(bytes))
        assertEquals(first, second)
        assertEquals(64, first.length)
    }
}
