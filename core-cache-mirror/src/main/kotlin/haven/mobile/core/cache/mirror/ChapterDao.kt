package haven.mobile.core.cache.mirror

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM cached_chapters WHERE albumId = :albumId ORDER BY chapterIndex ASC")
    suspend fun getForAlbum(albumId: String): List<CachedChapterEntity>

    /** Every cached chapter, so a library can list an album's songs without its file. */
    @Query("SELECT * FROM cached_chapters ORDER BY albumId ASC, chapterIndex ASC")
    fun observeAll(): Flow<List<CachedChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedChapterEntity>)

    @Query("DELETE FROM cached_chapters WHERE albumId = :albumId")
    suspend fun deleteForAlbum(albumId: String)

    @Query("DELETE FROM cached_chapters WHERE albumId IN (:ids)")
    suspend fun deleteForAlbums(ids: Collection<String>)

    @Query("DELETE FROM cached_chapters")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceForAlbum(albumId: String, items: List<CachedChapterEntity>) {
        deleteForAlbum(albumId)
        insertAll(items)
    }
}
