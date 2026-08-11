package com.example.sportapp.core.utils

fun formatRestTime(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return "N/A"

    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    return when {
        minutes > 0 && remainingSeconds > 0 -> "${minutes}m ${remainingSeconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${remainingSeconds}s"
    }
}

fun parseRestTimeToSeconds(time: String): Int {
    var totalSeconds = 0

    // Regex pour matcher "Xm Ys", "Xm", "Ys"
    val regex = Regex("""(?:(\d+)m)?\s*(?:(\d+)s)?""")
    val match = regex.matchEntire(time.trim())

    match?.let {
        val minutes = it.groups[1]?.value?.toIntOrNull() ?: 0
        val seconds = it.groups[2]?.value?.toIntOrNull() ?: 0
        totalSeconds = minutes * 60 + seconds
    }

    return totalSeconds
}
