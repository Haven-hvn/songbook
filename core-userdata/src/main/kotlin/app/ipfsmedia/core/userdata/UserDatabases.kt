package app.ipfsmedia.core.userdata

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens one [UserDatabase] per identity and keeps it open.
 *
 * Unlike haven-mobile's mirror, which closes the previous wallet's database when the wallet
 * changes, a database opened here stays open until its identity is removed: a screen still
 * collecting from the previous identity's tables during a switch then sees its flow replaced,
 * not a closed database.
 */
@Singleton
class UserDatabases @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
) {
    private val open = ConcurrentHashMap<String, UserDatabase>()

    fun forAddress(walletAddress: String): UserDatabase {
        val id = walletAddress.trim().lowercase()
        return open.computeIfAbsent(id) {
            Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.fileName(id))
                .addMigrations(*UserDatabase.MIGRATIONS)
                .build()
        }
    }

    /** The active identity's database, or null when no identity is known. */
    fun current(): UserDatabase? = walletSession.address.value?.let(::forAddress)

    /** Follows the active identity. Emits null while there is none. */
    val active: Flow<UserDatabase?> =
        walletSession.address
            .map { address -> address?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } }
            .distinctUntilChanged()
            .map { id -> id?.let(::forAddress) }

    /** Closes and deletes [walletAddress]'s database. */
    suspend fun delete(walletAddress: String) {
        val id = walletAddress.trim().lowercase()
        withContext(Dispatchers.IO) {
            open.remove(id)?.close()
            context.deleteDatabase(UserDatabase.fileName(id))
        }
    }
}
