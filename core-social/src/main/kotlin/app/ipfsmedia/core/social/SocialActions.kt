package app.ipfsmedia.core.social

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writing to discussion: commenting, replying, voting.
 *
 * **A stub, on purpose** (docs/PLAN.md §11). This version reads discussion and does not write it.
 * The interface exists so the shape of a later write path is settled — a write is signed and paid
 * for by the user's own wallet, with a membership receipt attached — and no screen calls it:
 * there is no comment box, reply button or vote control anywhere in the app.
 */
interface SocialActions {
    /** Whether this build can write at all. False here; screens show no write controls when false. */
    val canWrite: Boolean

    suspend fun comment(rootKey: String, text: String): Result<Unit>

    suspend fun reply(rootKey: String, parentKey: String, text: String): Result<Unit>

    suspend fun vote(recordKey: String, up: Boolean): Result<Unit>
}

/** Thrown by every [SocialActions] call in this version. */
class SocialWritesUnavailable : UnsupportedOperationException(
    "Writing comments and votes is not available in this version.",
)

@Singleton
class UnavailableSocialActions @Inject constructor() : SocialActions {
    override val canWrite: Boolean = false

    override suspend fun comment(rootKey: String, text: String): Result<Unit> =
        Result.failure(SocialWritesUnavailable())

    override suspend fun reply(rootKey: String, parentKey: String, text: String): Result<Unit> =
        Result.failure(SocialWritesUnavailable())

    override suspend fun vote(recordKey: String, up: Boolean): Result<Unit> =
        Result.failure(SocialWritesUnavailable())
}
