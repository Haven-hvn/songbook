package app.ipfsmedia.core.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Which kinds the feed shows. */
enum class FeedKind { ALL, MUSIC, VIDEO }

/** Why an upload is in the feed. */
enum class FeedReason { YOUR_DATADAO, FOLLOWED_UPLOADER, YOUR_UPLOAD }

data class FeedEntry(
    val item: LibraryItem,
    val reason: FeedReason,
    /** Uploaded since the feed was last looked at. */
    val isNew: Boolean,
)

/** One day of the feed. */
data class FeedDay(
    /** `Today`, `Yesterday`, a weekday within the last week, else a date. `Earlier` when undated. */
    val label: String,
    val entries: List<FeedEntry>,
)

/**
 * The Home feed: newest uploads across the DataDAOs the wallet is a member of and from uploaders
 * it follows, ordered by creation block (docs/PLAN.md §11).
 *
 * It is a feed of *uploads*. Nothing about what anyone played goes into it, and it is rebuilt
 * only from what the last refresh stored: between refreshes it does not change.
 *
 * Locked uploads appear only when they come from a followed uploader, so following someone shows
 * everything they publish while a DataDAO you are not in does not fill your feed.
 */
fun buildFeed(
    state: LibraryState,
    /** Newest block the feed had shown before this visit; later uploads are marked new. */
    seenBlock: Long,
    kind: FeedKind = FeedKind.ALL,
    /** Restrict to one DataDAO, or null for all of them. */
    daoKey: String? = null,
): List<FeedEntry> {
    val memberDaos = state.daos.filter { it.membership == Membership.MEMBER && !it.isPublicGroup }.map { it.key }.toSet()
    val followed = state.uploaders.filter { it.followed }.map { it.address }.toSet()
    return state.items.mapNotNull { item ->
        if (kind == FeedKind.MUSIC && item.kind != ItemKind.ALBUM) return@mapNotNull null
        if (kind == FeedKind.VIDEO && item.kind != ItemKind.VIDEO) return@mapNotNull null
        if (daoKey != null && item.daoKey != daoKey) return@mapNotNull null
        val reason = when {
            item.uploaderAddress in followed -> FeedReason.FOLLOWED_UPLOADER
            item.daoKey != null && item.daoKey in memberDaos -> FeedReason.YOUR_DATADAO
            item.isOwn -> FeedReason.YOUR_UPLOAD
            else -> return@mapNotNull null
        }
        FeedEntry(
            item = item,
            reason = reason,
            isNew = seenBlock > 0 && (item.createdAtBlock ?: 0L) > seenBlock,
        )
    }
}

/** The DataDAOs that have something in the feed, for the filter chips. */
fun feedDaos(state: LibraryState): List<DataDao> {
    val keys = buildFeed(state, seenBlock = 0).mapNotNull { it.item.daoKey }.toSet()
    return state.daos.filter { it.key in keys }
}

/** The newest block in [entries], to remember as "seen" once the feed has been shown. */
fun newestBlock(entries: List<FeedEntry>): Long = entries.maxOfOrNull { it.item.createdAtBlock ?: 0L } ?: 0L

/** Groups an already-ordered feed by calendar day, keeping the order within and between days. */
fun groupByDay(
    entries: List<FeedEntry>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): List<FeedDay> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = LinkedHashMap<String, MutableList<FeedEntry>>()
    entries.forEach { entry ->
        val label = entry.item.createdAtMillis
            ?.let { dayLabel(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today, locale) }
            ?: UNDATED
        days.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return days.map { (label, dayEntries) -> FeedDay(label, dayEntries) }
}

/**
 * Groups an already-ordered list by calendar day under the same labels the feed uses, for lists
 * that are not uploads: the local play history.
 */
fun <T> groupByDayOf(
    items: List<T>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    millisOf: (T) -> Long?,
): List<Pair<String, List<T>>> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = LinkedHashMap<String, MutableList<T>>()
    items.forEach { item ->
        val label = millisOf(item)
            ?.let { dayLabel(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today, locale) }
            ?: UNDATED
        days.getOrPut(label) { mutableListOf() }.add(item)
    }
    return days.map { (label, dayItems) -> label to dayItems }
}

internal const val UNDATED = "Earlier"

internal fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale): String {
    val age = today.toEpochDay() - day.toEpochDay()
    return when {
        age <= 0L -> "Today"
        age == 1L -> "Yesterday"
        age < 7L -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }
}
