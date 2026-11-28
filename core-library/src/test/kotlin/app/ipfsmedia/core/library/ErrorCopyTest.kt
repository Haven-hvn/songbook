package app.ipfsmedia.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The user-facing copy for a failure code.
 *
 * `NETWORK_ERROR` is the one that regressed silently: `ArkivClientImpl.networkError` puts the
 * endpoint host, the failing call, and the exception class in the message, but the copy used to
 * throw that detail away and show a generic "no connectivity" line. A rejected RPC query then
 * read to users as an offline device, and gave nobody anything to diagnose from. These pin that
 * the detail now survives.
 */
class ErrorCopyTest {

    @Test
    fun `network error shows the diagnostic detail`() {
        val detail = "Couldn't reach rpc.example.test [scanCatalog] (SocketTimeoutException: timed out). Check your connection."
        val message = describeError("NETWORK_ERROR", detail)

        assertEquals(detail, message.what, "the failing endpoint/class must reach the user")
        assertEquals("NETWORK_ERROR", message.code)
        assertTrue(message.todo!!.contains("still plays"), "reassurance copy changed: ${message.todo}")
    }

    @Test
    fun `blank network detail falls back to the plain line`() {
        val message = describeError("NETWORK_ERROR", "")

        assertEquals("The network could not be reached.", message.what)
    }

    @Test
    fun `null network detail falls back to the plain line`() {
        assertEquals("The network could not be reached.", describeError("NETWORK_ERROR").what)
    }

    @Test
    fun `cache write and read errors preserve diagnostic detail`() {
        val writeDetail = "IOException: No space left on device"
        assertEquals(writeDetail, describeError("CACHE_WRITE_FAILED", writeDetail).what)
        assertEquals("The file could not be written to this device.", describeError("CACHE_WRITE_FAILED").what)

        val readDetail = "EOFException: unexpected end of CAR stream"
        assertEquals(readDetail, describeError("CACHE_READ_FAILED", readDetail).what)
        assertEquals("The file could not be read from this device.", describeError("CACHE_READ_FAILED").what)
    }

    @Test
    fun `playback decrypt error preserves diagnostic detail`() {
        val detail = "The sealed key would not open (invalid ciphertext)."
        assertEquals(detail, describeError("PLAYBACK_DECRYPT_FAILED", detail).what)
        assertEquals("The file could not be unlocked.", describeError("PLAYBACK_DECRYPT_FAILED").what)
    }
}