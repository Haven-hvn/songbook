package app.ipfsmedia.feature.collection

import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.OfflineState

/** What part of the collection is showing. */
enum class CollectionFilter(val label: String) {
    ALL("All"),
    MUSIC("Music"),
    VIDEO("Video"),
    FAVOURITES("Favourites"),

    /** Favourite songs: the one filter whose rows are songs, not uploads. */
    SONGS("Favourite songs"),
    ON_DEVICE("On this device"),
    YOURS("Your uploads"),
    ;

    fun accepts(item: LibraryItem): Boolean = when (this) {
        ALL -> true
        MUSIC -> item.kind == ItemKind.ALBUM
        VIDEO -> item.kind == ItemKind.VIDEO
        FAVOURITES -> item.isFavourite
        // Songs are not items; the page draws them from the favourites table instead.
        SONGS -> false
        ON_DEVICE -> item.offline == OfflineState.ON_DEVICE || item.offline == OfflineState.PARTIAL
        YOURS -> item.isOwn
    }
}

enum class CollectionSort(val label: String) {
    NEWEST("Newest first"),
    TITLE("Title"),
    UPLOADER("Uploader"),
    OPENED("Last opened"),
}

/**
 * The collection as the page shows it. Sorting is stable, so within equal keys the catalogue's
 * own order (newest first) is kept. Titles compare without regard to case.
 */
fun arrangeCollection(items: List<LibraryItem>, filter: CollectionFilter, sort: CollectionSort): List<LibraryItem> {
    val kept = items.filter(filter::accepts)
    return when (sort) {
        // The catalogue is already newest first.
        CollectionSort.NEWEST -> kept
        CollectionSort.TITLE -> kept.sortedBy { it.title.lowercase() }
        CollectionSort.UPLOADER -> kept.sortedBy { it.uploaderName.lowercase() }
        // Never-opened items go last, in catalogue order.
        CollectionSort.OPENED -> kept.sortedByDescending { it.lastOpenedAtMillis ?: Long.MIN_VALUE }
    }
}
