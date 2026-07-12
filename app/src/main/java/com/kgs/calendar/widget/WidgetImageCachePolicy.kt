package com.kgs.calendar.widget

internal data class WidgetImageCacheEntry(
    val name: String,
    val lastModifiedMillis: Long,
    val bytes: Long,
)

internal fun <T> cachedWidgetValue(cached: T?, create: () -> T): T =
    cached ?: create()

internal fun selectWidgetImageCacheEvictions(
    entries: List<WidgetImageCacheEntry>,
    protectedNames: Set<String>,
    maxFiles: Int,
    maxBytes: Long,
): List<String> {
    var remainingFiles = entries.size
    var remainingBytes = entries.sumOf { entry -> entry.bytes.coerceAtLeast(0L) }
    val evictions = mutableListOf<String>()
    entries.sortedBy { entry -> entry.lastModifiedMillis }.forEach { entry ->
        if (remainingFiles <= maxFiles && remainingBytes <= maxBytes) return@forEach
        if (entry.name in protectedNames) return@forEach
        evictions += entry.name
        remainingFiles -= 1
        remainingBytes -= entry.bytes.coerceAtLeast(0L)
    }
    return evictions
}
