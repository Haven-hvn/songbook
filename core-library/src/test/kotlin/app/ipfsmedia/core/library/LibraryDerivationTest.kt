package app.ipfsmedia.core.library

import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryDerivationTest {

    @Test
    fun `a sealed upload in a datadao the wallet is in is a member item`() {
        val item = library(media("a")).item("a")!!
        assertEquals(AccessState.MEMBER, item.access)
        assertEquals(DAO_A, item.daoKey)
        assertTrue(item.inCollection)
    }

    @Test
    fun `a sealed upload in a datadao the wallet is not in is locked and says which`() {
        val item = library(media("a"), daoStates = listOf(notMember())).item("a")!!
        assertEquals(AccessState.NOT_MEMBER, item.access)
        assertTrue(item.access.isLocked)
        assertFalse(item.inCollection)
        assertTrue(item.lockedReason()!!.startsWith("Members of"))
    }

    @Test
    fun `membership that could not be read is unknown, never not-a-member`() {
        val item = library(media("a"), daoStates = emptyList()).item("a")!!
        assertEquals(AccessState.UNKNOWN, item.access)
        assertTrue(item.access.canOpen)
        assertNull(item.lockedReason())
    }

    @Test
    fun `an upload that is not sealed is open even when it names a datadao`() {
        val item = library(media("a", encrypted = false), daoStates = listOf(notMember())).item("a")!!
        assertEquals(AccessState.OPEN, item.access)
    }

    @Test
    fun `an upload with no gate belongs to the public group`() {
        val state = library(media("a", gate = null))
        assertNull(state.item("a")!!.daoKey)
        assertEquals(listOf("a"), state.itemsOf(PUBLIC_GROUP_KEY).map { it.id })
        assertTrue(state.daos.single().isPublicGroup)
    }

    @Test
    fun `a market cap refusal shows as not open yet`() {
        val item = library(media("a"), notOpenYet = setOf("a")).item("a")!!
        assertEquals(AccessState.NOT_OPEN_YET, item.access)
        assertEquals("Not open yet", item.lockedReason())
    }

    @Test
    fun `images documents and files are left out`() {
        val state = library(media("a", kind = MediaKind.IMAGE), media("b", kind = MediaKind.DOCUMENT), media("c"))
        assertEquals(listOf("c"), state.items.map { it.id })
    }

    @Test
    fun `items are newest first by block`() {
        val state = library(media("old", block = 10), media("new", block = 30), media("mid", block = 20))
        assertEquals(listOf("new", "mid", "old"), state.items.map { it.id })
    }

    @Test
    fun `songs are unknown until the album has been read`() {
        val item = library(media("a")).item("a")!!
        assertNull(item.songs)
        assertFalse(item.songsKnown)
        // An unread album moves as a whole album.
        assertEquals(listOf(PlayableEntry.WHOLE_FILE), item.playEntries().map { it.chapterIndex })
    }

    @Test
    fun `cached chapters become songs that end where the next begins`() {
        val item = library(media("a", durationSeconds = 200), chapters = chapters("a", titles = arrayOf("One", "Two", "Three")))
            .item("a")!!
        val songs = item.songs!!
        assertEquals(listOf("One", "Two", "Three"), songs.map { it.title })
        assertEquals(listOf(60_000L, 120_000L, 200_000L), songs.map { it.endMs })
        assertEquals(3, item.playEntries().size)
        assertEquals(EntryKind.SONG, item.playEntries().first().kind)
    }

    @Test
    fun `an album read and found to have no chapters plays as one track`() {
        val item = library(media("a"), chapters = listOf(ChapterRow("a", -1, 0, "", "bafkzcib-a"))).item("a")!!
        assertEquals(emptyList<Song>(), item.songs)
        assertTrue(item.songsKnown)
        assertEquals(1, item.playEntries().size)
    }

    @Test
    fun `chapters read from a different file are not shown against a re-uploaded album`() {
        val item = library(
            media("a", pieceCid = "bafkzcib-new"),
            chapters = chapters("a", pieceCid = "bafkzcib-old", titles = arrayOf("One")),
        ).item("a")!!
        assertNull(item.songs)
    }

    @Test
    fun `datadaos are named by their board title and count their uploads`() {
        val state = library(
            media("a"),
            media("v", kind = MediaKind.VIDEO),
            media("x", gate = gate(TOKEN_B)),
            daoStates = listOf(member(DAO_A, title = "Night Shift"), notMember(DAO_B)),
        )
        val a = state.dao(DAO_A)!!
        assertEquals("Night Shift", a.name)
        assertEquals(1, a.albumCount)
        assertEquals(1, a.videoCount)
        assertEquals(Membership.MEMBER, a.membership)
        assertEquals(Membership.NOT_MEMBER, state.dao(DAO_B)!!.membership)
        // Yours first.
        assertEquals(listOf(DAO_A, DAO_B), state.daos.map { it.key })
        assertEquals(listOf(DAO_A), state.yourDaos.map { it.key })
    }

    @Test
    fun `a pinned datadao with nothing live stays listed`() {
        val state = library(media("a"), pins = listOf(DAO_B))
        val pinned = state.dao(DAO_B)!!
        assertTrue(pinned.pinned)
        assertEquals(0, pinned.itemCount)
        assertEquals(DAO_B, state.daos.first().key)
    }

    @Test
    fun `the datadao rule is stated in words with no price`() {
        val rule = library(media("a", gate = gate(threshold = 25.0))).dao(DAO_A)!!.ruleInWords
        assertEquals("Hold at least 25 of token $TOKEN_A on Base.", rule)
    }

    @Test
    fun `uploaders carry their handle and unseen uploads`() {
        val state = library(
            media("a", owner = OTHER, block = 10, handle = "moth.eth"),
            media("b", owner = OTHER, block = 30),
            media("mine", owner = WALLET, block = 20),
            follows = mapOf(OTHER to 15L),
        )
        val other = state.uploader(OTHER)!!
        assertEquals("moth.eth", other.displayName)
        assertEquals(2, other.uploadCount)
        assertTrue(other.followed)
        assertEquals(1, other.unseenCount)
        assertTrue(state.uploader(WALLET)!!.isSelf)
        assertTrue(state.item("mine")!!.isOwn)
    }

    @Test
    fun `offline state follows the content cache`() {
        val state = library(media("on", cache = ContentCacheStatus.CACHED), media("off"))
        assertEquals(OfflineState.ON_DEVICE, state.item("on")!!.offline)
        assertEquals(OfflineState.NOT_DOWNLOADED, state.item("off")!!.offline)
    }

    @Test
    fun `dates are estimated from the block clock`() {
        val clock = BlockClock(headBlock = 1_000, observedAtMillis = 10_000_000)
        val item = library(media("a", block = 900), clock = clock).item("a")!!
        assertEquals(10_000_000 - 100 * 2_000L, item.createdAtMillis)
    }

    @Test
    fun `favourites and own uploads are in the collection even when locked`() {
        val state = library(
            media("fav"),
            media("mine", owner = WALLET),
            media("other"),
            daoStates = listOf(notMember()),
            favourites = setOf("fav"),
        )
        assertEquals(setOf("fav", "mine"), state.collection.map { it.id }.toSet())
    }

    @Test
    fun `the datadao key is the same one the access checker uses`() {
        // core-collections spells gate keys "<aolVariant>:<lowercased token>".
        assertEquals("BaseMainnet:$TOKEN_A", gate(TOKEN_A.uppercase().replace("0X", "0x")).daoKeyOrNull())
    }
}
