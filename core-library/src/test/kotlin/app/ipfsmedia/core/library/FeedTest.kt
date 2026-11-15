package app.ipfsmedia.core.library

import haven.mobile.core.domain.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class FeedTest {

    private val state = library(
        media("mine-dao-old", block = 10),
        media("mine-dao-new", block = 40, kind = MediaKind.VIDEO),
        media("other-dao", block = 30, gate = gate(TOKEN_B)),
        media("followed-locked", block = 20, gate = gate(TOKEN_B), owner = WALLET.replace('1', '3')),
        media("public", block = 50, gate = null),
        daoStates = listOf(member(DAO_A), notMember(DAO_B)),
        follows = mapOf(WALLET.replace('1', '3') to 0L),
    )

    @Test
    fun `the feed is uploads from your datadaos and followed uploaders, newest first`() {
        assertEquals(
            listOf("mine-dao-new", "followed-locked", "mine-dao-old"),
            buildFeed(state, seenBlock = 0).map { it.item.id },
        )
    }

    @Test
    fun `a locked upload appears only because its uploader is followed`() {
        val entry = buildFeed(state, seenBlock = 0).first { it.item.id == "followed-locked" }
        assertEquals(FeedReason.FOLLOWED_UPLOADER, entry.reason)
        assertTrue(entry.item.access.isLocked)
        assertFalse(buildFeed(state, seenBlock = 0).any { it.item.id == "other-dao" })
    }

    @Test
    fun `uploads newer than the last visit are marked new`() {
        val feed = buildFeed(state, seenBlock = 20).associate { it.item.id to it.isNew }
        assertEquals(mapOf("mine-dao-new" to true, "followed-locked" to false, "mine-dao-old" to false), feed)
        // A first visit marks nothing: everything would be new, which says nothing.
        assertTrue(buildFeed(state, seenBlock = 0).none { it.isNew })
    }

    @Test
    fun `filters narrow by kind and by datadao`() {
        assertEquals(listOf("mine-dao-new"), buildFeed(state, 0, kind = FeedKind.VIDEO).map { it.item.id })
        assertEquals(listOf("followed-locked", "mine-dao-old"), buildFeed(state, 0, kind = FeedKind.MUSIC).map { it.item.id })
        assertEquals(listOf("followed-locked"), buildFeed(state, 0, daoKey = DAO_B).map { it.item.id })
        assertEquals(listOf(DAO_A, DAO_B), feedDaos(state).map { it.key })
    }

    @Test
    fun `the newest block is what gets remembered as seen`() {
        assertEquals(40L, newestBlock(buildFeed(state, 0)))
        assertEquals(0L, newestBlock(emptyList()))
    }

    @Test
    fun `days are labelled relative to today`() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals("Today", dayLabel(today, today, Locale.US))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today, Locale.US))
        assertEquals("Wednesday", dayLabel(today.minusDays(3), today, Locale.US))
        assertEquals("1 November", dayLabel(today.minusDays(13), today, Locale.US))
        assertEquals("14 November 2025", dayLabel(today.minusYears(1), today, Locale.US))
    }

    @Test
    fun `grouping keeps feed order and puts undated uploads last under their own heading`() {
        val day = 86_400_000L
        val now = 20 * day
        val clock = BlockClock(headBlock = 1_000, observedAtMillis = now, blockMillis = day)
        val dated = library(
            media("today", block = 1_000),
            media("yesterday", block = 999),
            media("undated", block = null),
            clock = clock,
        )
        val days = groupByDay(buildFeed(dated, 0), nowMillis = now, zone = ZoneOffset.UTC, locale = Locale.US)
        assertEquals(listOf("Today", "Yesterday", "Earlier"), days.map { it.label })
        assertEquals(listOf("today"), days.first().entries.map { it.item.id })
    }

    @Test
    fun `any list can be grouped by day under the same labels`() {
        val day = 86_400_000L
        // Midday, so "a second ago" is still today.
        val now = 20 * day + day / 2
        // Play times, newest first, as History holds them.
        val plays = listOf(now - 1_000L, now - 2_000L, now - day, now - 3 * day)
        val days = groupByDayOf(plays, nowMillis = now, zone = ZoneOffset.UTC, locale = Locale.US) { it }
        assertEquals("Today", days[0].first)
        assertEquals(2, days[0].second.size)
        assertEquals("Yesterday", days[1].first)
        assertEquals(3, days.size)
        assertEquals(plays, days.flatMap { it.second })
    }
}
