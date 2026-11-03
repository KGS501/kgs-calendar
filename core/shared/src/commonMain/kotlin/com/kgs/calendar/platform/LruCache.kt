package com.kgs.calendar.platform

/** A thread-safe LRU cache bounded by the total [sizeOf] of its entries, as android.util.LruCache. */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val lock = KgsLock()
    private val map = LinkedHashMap<K, V>()
    private var size = 0

    protected open fun sizeOf(key: K, value: V): Int = 1

    fun get(key: K): V? = lock.withLock {
        val value = map.remove(key) ?: return@withLock null
        map[key] = value // most recently used last
        value
    }

    fun put(key: K, value: V): V? = lock.withLock {
        val previous = map.remove(key)
        if (previous != null) size -= sizeOf(key, previous)
        map[key] = value
        size += sizeOf(key, value)
        while (size > maxSize && map.isNotEmpty()) {
            val eldest = map.keys.first()
            val removed = map.remove(eldest)!!
            size -= sizeOf(eldest, removed)
        }
        previous
    }

    fun evictAll() = lock.withLock {
        map.clear()
        size = 0
    }
}
