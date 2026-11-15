package app.ipfsmedia.feature.datadao

/**
 * The faces of a DataDAO page. The names are what `DataDaoRoute.tab` carries, so they are part
 * of saved navigation history and must not be renamed.
 */
enum class DataDaoTab(val label: String) {
    MUSIC("Music"),
    VIDEO("Video"),
    DISCUSSION("Discussion"),
    UPLOADERS("Uploaders"),
    ABOUT("About"),
    ;

    companion object {
        fun of(name: String?): DataDaoTab? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The tabs a DataDAO page shows. A tab with nothing behind it is left out, so a DataDAO with no
 * videos has no empty Video tab, and Discussion exists only where someone has started a thread
 * (docs/PLAN.md §11). About is always there: it is where the membership rule is.
 */
fun tabsFor(albumCount: Int, videoCount: Int, hasThreads: Boolean, uploaderCount: Int): List<DataDaoTab> = buildList {
    if (albumCount > 0) add(DataDaoTab.MUSIC)
    if (videoCount > 0) add(DataDaoTab.VIDEO)
    if (hasThreads) add(DataDaoTab.DISCUSSION)
    if (uploaderCount > 0) add(DataDaoTab.UPLOADERS)
    add(DataDaoTab.ABOUT)
}
