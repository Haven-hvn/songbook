package app.ipfsmedia.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.InterruptedIOException

/**
 * Turns `ipfsmedia://item/<record key>` into the staged file, at the moment the player opens it.
 *
 * Media3 calls this on the loading thread of the item being opened, and that thread is allowed to
 * block, so this is where the fetch and the unlock happen. While it blocks, the player reports
 * "buffering" for that item and [MediaStager.states] says what is really going on. Because the
 * player opens the next item a little before the current one ends, the next album is fetched
 * ahead without any code asking for it.
 *
 * When the player gives up on the item (the user skipped), it interrupts the thread; the wait
 * ends, and the staging run stops if nothing else is waiting on it.
 */
@OptIn(UnstableApi::class)
internal class StagingResolver(
    private val stager: MediaStager,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val request = QueueItems.requestOf(dataSpec.uri) ?: return dataSpec
        val file = try {
            runBlocking { stager.stage(request) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("staging interrupted")
        } catch (e: CancellationException) {
            throw InterruptedIOException("staging cancelled")
        }
        return dataSpec.withUri(Uri.fromFile(file))
    }
}

/**
 * Media3 retries a failed load three times by default. A staging failure is an answer, not a
 * glitch: retrying it would repeat a provider probe, or worse a key request, for the same
 * result. Everything else (a read error on a local file) keeps the default behaviour.
 */
@OptIn(UnstableApi::class)
internal class StagingAwareRetryPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (loadErrorInfo.exception.stagingFailure() != null) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
}
