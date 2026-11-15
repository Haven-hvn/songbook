package app.ipfsmedia.core.library

import java.util.Locale

/** `3:07`, or `1:02:45` past the hour. Unknown or negative lengths read as a dash, never `0:00`. */
fun formatClock(ms: Long?): String {
    if (ms == null || ms < 0) return "–:––"
    val totalSeconds = ms / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** `42 min`, `1 h 5 min`: a length to read, where [formatClock] is a position to watch. */
fun formatLength(ms: Long?): String? {
    if (ms == null || ms <= 0) return null
    val minutes = (ms + 30_000) / 60_000
    return when {
        minutes < 1 -> "Under a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** `812 KB`, `1.4 GB`. Binary units, one decimal from megabytes up. */
fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "Unknown size"
    val kb = 1024.0
    return when {
        bytes < kb -> "$bytes B"
        bytes < kb * kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        bytes < kb * kb * kb -> String.format(Locale.US, "%.1f MB", bytes / (kb * kb))
        else -> String.format(Locale.US, "%.1f GB", bytes / (kb * kb * kb))
    }
}

/** `Just now`, `5 minutes ago`, `3 days ago`. Null when either time is unknown. */
fun formatAge(thenMillis: Long?, nowMillis: Long): String? {
    if (thenMillis == null || thenMillis <= 0) return null
    val seconds = ((nowMillis - thenMillis) / 1_000).coerceAtLeast(0)
    return when {
        seconds < 90 -> "Just now"
        seconds < 3_600 -> "${seconds / 60} minutes ago"
        seconds < 7_200 -> "An hour ago"
        seconds < 86_400 -> "${seconds / 3_600} hours ago"
        seconds < 172_800 -> "Yesterday"
        seconds < 30L * 86_400 -> "${seconds / 86_400} days ago"
        seconds < 60L * 86_400 -> "A month ago"
        seconds < 365L * 86_400 -> "${seconds / (30L * 86_400)} months ago"
        else -> "Over a year ago"
    }
}

/**
 * How long a record has left: `Expires in 3 days`, `Expires in 5 hours`, `Expired`.
 * Arkiv records are leased, not permanent; threads last 14 days and comments 7, and each says so.
 */
fun formatRemainingLife(expiresAtMillis: Long?, nowMillis: Long): String? {
    if (expiresAtMillis == null) return null
    val seconds = (expiresAtMillis - nowMillis) / 1_000
    return when {
        seconds <= 0 -> "Expired"
        seconds < 3_600 -> "Expires in under an hour"
        seconds < 7_200 -> "Expires in an hour"
        seconds < 86_400 -> "Expires in ${seconds / 3_600} hours"
        seconds < 172_800 -> "Expires in a day"
        else -> "Expires in ${seconds / 86_400} days"
    }
}

/** `1 song`, `12 songs`. */
fun countOf(count: Int, singular: String, plural: String = "${singular}s"): String =
    if (count == 1) "1 $singular" else "$count $plural"
