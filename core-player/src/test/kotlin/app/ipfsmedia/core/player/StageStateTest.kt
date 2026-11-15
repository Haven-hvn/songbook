package app.ipfsmedia.core.player

import cloud.filecoin.foc.cache.PieceUnavailableException
import haven.mobile.core.domain.error.HavenError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class StageStateTest {

    @Test
    fun `fetching never shows a fraction, because the cache reports nothing while it downloads`() {
        assertNull(StageState.Working(StageStep.FETCHING, writtenBytes = 10, totalBytes = 100).fraction)
        assertNull(StageState.Working(StageStep.CHECKING_ACCESS).fraction)
    }

    @Test
    fun `unlocking shows real bytes against the declared size`() {
        assertEquals(0.25f, StageState.Working(StageStep.UNLOCKING, 25, 100).fraction)
        assertEquals(1f, StageState.Working(StageStep.PREPARING, 120, 100).fraction)
    }

    @Test
    fun `no fraction is invented when the size is unknown`() {
        assertNull(StageState.Working(StageStep.UNLOCKING, 25, null).fraction)
        assertNull(StageState.Working(StageStep.UNLOCKING, 25, 0).fraction)
        assertNull(StageState.Working(StageStep.UNLOCKING, null, 100).fraction)
    }

    @Test
    fun `a provider saying unavailable keeps its meaning through wrappers`() {
        val wrapped = HavenError.CacheReadFailed("Fetch failed", RuntimeException(PieceUnavailableException("gone")))
        assertEquals("PIECE_UNAVAILABLE", wrapped.toStagingException("a").code)
    }

    @Test
    fun `a dry key service is told apart from a wallet problem`() {
        val dry = HavenError.CanisterCallFailed("Canister is out of cycles")
        assertEquals("CANISTER_OUT_OF_CYCLES", dry.toStagingException("a").code)
    }

    @Test
    fun `a haven error keeps its own code`() {
        val failure = HavenError.SigningFailed("rejected").toStagingException("a")
        assertEquals("SIGNING_FAILED", failure.code)
        assertEquals("a", failure.recordKey)
    }

    @Test
    fun `anything else takes the fallback code`() {
        assertEquals("CACHE_WRITE_FAILED", IllegalStateException("disk").toStagingException("a", "CACHE_WRITE_FAILED").code)
        assertEquals("INTERNAL", IllegalStateException("?").toStagingException("a").code)
    }

    @Test
    fun `a staging failure is passed through unchanged`() {
        val original = StagingException("a", StagingException.SIGNATURE_REQUIRED)
        assertSame(original, original.toStagingException("b"))
    }

    @Test
    fun `the player finds the staging failure inside its own error`() {
        val original = StagingException("a", StagingException.RECORD_GONE)
        val asThePlayerReportsIt = RuntimeException("source error", IOException("load failed", original))
        assertSame(original, asThePlayerReportsIt.stagingFailure())
        assertNull(RuntimeException("decoder").stagingFailure())
    }

    @Test
    fun `every failure has words and something to do`() {
        val message = StagingException("a", StagingException.SIGNATURE_REQUIRED).userMessage
        assertEquals("SIGNATURE_REQUIRED", message.code)
        assertTrue(message.what.isNotBlank())
        assertTrue(!message.todo.isNullOrBlank())
    }
}
