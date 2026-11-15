package app.ipfsmedia.core.social

import app.ipfsmedia.core.library.BlockClock
import app.ipfsmedia.core.library.LibraryState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscussionStateTest {

    private val library = LibraryState(ready = true, clock = BlockClock(headBlock = 200, observedAtMillis = 1_000_000))

    private fun build(
        snapshot: BoardSnapshot,
        muted: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        unsealed: Map<String, String> = emptyMap(),
    ) = buildDiscussion(snapshot, muted, hidden, library, unsealed)

    @Test
    fun `a record with no comments has none, so its page shows no comments area`() {
        val state = build(BoardSnapshot(posts = listOf(post("a", root = "0xother"))))
        assertEquals(0, state.commentCount(ROOT))
        assertTrue(state.commentsUnder(ROOT).isEmpty())
        assertEquals(1, state.commentCount("0xOTHER"))
    }

    @Test
    fun `a datadao has a discussion tab only when it has threads`() {
        assertFalse(build(BoardSnapshot(posts = listOf(post("a")))).hasThreads(DAO))
        assertTrue(build(BoardSnapshot(threads = listOf(thread("t")))).hasThreads(DAO))
        assertFalse(build(BoardSnapshot()).hasThreads(DAO))
    }

    @Test
    fun `threads are newest first and count their comments`() {
        val state = build(
            BoardSnapshot(
                threads = listOf(thread("old", block = 10), thread("new", block = 50)),
                posts = listOf(post("c1", root = "new"), post("c2", root = "new", parent = "c1")),
            ),
        )
        assertEquals(listOf("new", "old"), state.threadsFor(DAO).map { it.key })
        assertEquals(2, state.thread("new")!!.commentCount)
        assertEquals(0, state.thread("old")!!.commentCount)
        assertEquals(listOf(0, 1), state.commentsUnder("new").map { it.depth })
    }

    @Test
    fun `each record shows when it was written and how long it has left`() {
        val view = build(BoardSnapshot(threads = listOf(thread("t", block = 100)))).thread("t")!!
        // 100 blocks before the head at 2 s a block; expires 1,000 blocks after it was written.
        assertEquals(1_000_000L - 100 * 2_000L, view.createdAtMillis)
        assertEquals(1_000_000L + 900 * 2_000L, view.expiresAtMillis)
    }

    @Test
    fun `a muted address's comments lose their text and its threads are not listed`() {
        val state = build(
            BoardSnapshot(
                threads = listOf(thread("by-bob", author = BOB), thread("by-alice")),
                posts = listOf(post("c", author = BOB)),
            ),
            muted = setOf(BOB),
        )
        assertEquals(listOf("by-alice"), state.threadsFor(DAO).map { it.key })
        val comment = state.commentsUnder(ROOT).single()
        assertTrue(comment.isMuted)
        assertNull(comment.text)
    }

    @Test
    fun `a hidden thread is left out unless asked for`() {
        val state = build(BoardSnapshot(threads = listOf(thread("t"))), hidden = setOf("t"))
        assertTrue(state.threadsFor(DAO).isEmpty())
        assertEquals(listOf("t"), state.threadsFor(DAO, includeHidden = true).map { it.key })
    }

    @Test
    fun `sealed text reads as sealed until it is unsealed`() {
        val sealed = post("s", body = PostBody.Sealed("AAAA"))
        val before = build(BoardSnapshot(posts = listOf(sealed)))
        assertNull(before.commentsUnder(ROOT).single().text)
        assertTrue(before.commentsUnder(ROOT).single().isSealed)
        assertEquals(1, before.sealedCount(ROOT))

        val after = build(BoardSnapshot(posts = listOf(sealed)), unsealed = mapOf("s" to "hello"))
        assertEquals("hello", after.commentsUnder(ROOT).single().text)
        assertEquals(0, after.sealedCount(ROOT))
    }

    @Test
    fun `receipt verdicts ride along, and an unchecked record says so`() {
        val state = build(
            BoardSnapshot(
                posts = listOf(post("ok"), post("bad"), post("unknown")),
                receipts = mapOf("ok" to ReceiptStatus.VERIFIED, "bad" to ReceiptStatus.FAILED),
            ),
        )
        val byKey = state.commentsUnder(ROOT).associate { it.key to it.receipt }
        assertEquals(ReceiptStatus.VERIFIED, byKey["ok"])
        assertEquals(ReceiptStatus.FAILED, byKey["bad"])
        assertEquals(ReceiptStatus.UNCHECKED, byKey["unknown"])
    }

    @Test
    fun `sealed text is the json before the padding`() {
        assertEquals("hi there", sealedTextOf("""{"t":"hi there"}      """.toByteArray()))
        assertEquals("x", sealedTextOf(("""{"t":"x"}""" + "\u0000".repeat(200)).toByteArray()))
        assertNull(sealedTextOf("garbage".toByteArray()))
    }

    @Test
    fun `the write path is a stub`() {
        val actions = UnavailableSocialActions()
        assertFalse(actions.canWrite)
    }
}
