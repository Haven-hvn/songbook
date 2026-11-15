package app.ipfsmedia.player

import android.content.Context
import java.io.File

/**
 * Keeps the last crash, on the device, so it can be read and copied on the next start.
 *
 * Nothing is built or run before CI, so the first people to see a crash are users without a
 * debugger. This writes the stack trace to a private file before the process dies; the next
 * launch shows it once, with a button to copy it, and it is deleted when that is dismissed.
 * Nothing is sent anywhere: there is no crash reporting service in this app.
 */
object CrashNote {
    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_CHARS = 16_000

    fun install(context: Context) {
        val file = fileIn(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    buildString {
                        append("Googlebook IPFSMedia ").append(BuildConfig.VERSION_NAME).append('\n')
                        append("Thread: ").append(thread.name).append('\n')
                        append(error.stackTraceToString())
                    }.take(MAX_CHARS),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash, or null when the last run ended normally. */
    fun read(context: Context): String? =
        runCatching { fileIn(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear(context: Context) {
        runCatching { fileIn(context).delete() }
    }

    private fun fileIn(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)
}
