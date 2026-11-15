package app.ipfsmedia.core.library

import java.text.Normalizer

/** A song found by search, with the album it is in. */
data class SongHit(val album: LibraryItem, val song: Song)

data class SearchResults(
    val albums: List<LibraryItem> = emptyList(),
    val videos: List<LibraryItem> = emptyList(),
    val songs: List<SongHit> = emptyList(),
    val uploaders: List<Uploader> = emptyList(),
    val daos: List<DataDao> = emptyList(),
) {
    val isEmpty: Boolean
        get() = albums.isEmpty() && videos.isEmpty() && songs.isEmpty() && uploaders.isEmpty() && daos.isEmpty()

    val total: Int get() = albums.size + videos.size + songs.size + uploaders.size + daos.size
}

/**
 * Local search over what the last refresh stored. No request leaves the device: what a person
 * types into a search box is exactly the kind of thing this app keeps to itself.
 *
 * Every word of the query must appear in the candidate. Results that start with the query come
 * first, then ones where a word starts with it, then the rest; ties keep catalogue order
 * (newest first).
 */
fun search(query: String, state: LibraryState, limitPerGroup: Int = 60): SearchResults {
    val words = fold(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return SearchResults()
    val whole = words.joinToString(" ")

    fun rank(vararg fields: String?): Int? {
        val texts = fields.filterNotNull().map(::fold)
        val joined = texts.joinToString(" ")
        if (words.any { it !in joined }) return null
        val primary = texts.firstOrNull().orEmpty()
        return when {
            primary == whole -> 0
            primary.startsWith(whole) -> 1
            primary.split(' ').any { it.startsWith(words.first()) } -> 2
            else -> 3
        }
    }

    fun <T> ranked(candidates: Sequence<Pair<T, Int?>>): List<T> =
        candidates.mapNotNull { (value, score) -> score?.let { value to it } }
            .sortedBy { it.second }
            .take(limitPerGroup)
            .map { it.first }
            .toList()

    val items = state.items.asSequence()
    return SearchResults(
        albums = ranked(
            items.filter { it.kind == ItemKind.ALBUM }.map { it to rank(it.title, it.uploaderName, it.daoName) },
        ),
        videos = ranked(
            items.filter { it.kind == ItemKind.VIDEO }.map { it to rank(it.title, it.uploaderName, it.daoName) },
        ),
        songs = ranked(
            items.filter { it.kind == ItemKind.ALBUM }
                .flatMap { album -> album.songs.orEmpty().asSequence().map { SongHit(album, it) } }
                .map { it to rank(it.song.title, it.album.title, it.album.uploaderName) },
        ),
        uploaders = ranked(state.uploaders.asSequence().map { it to rank(it.handle ?: it.address, it.address) }),
        daos = ranked(
            state.daos.asSequence().filter { !it.isPublicGroup }.map { it to rank(it.name, it.tokenAddress, it.about) },
        ),
    )
}

/** Lowercased, accents removed, runs of anything that is not a letter or digit become one space. */
internal fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .trim()
