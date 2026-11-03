package com.kgs.calendar.platform

/** A re-entrant lock for short critical sections shared between threads. */
expect class KgsLock() {
    fun lock()
    fun unlock()
}

inline fun <T> KgsLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/** A map whose operations are atomic, for the few caches shared between threads. */
class LockedMap<K, V> {
    private val lock = KgsLock()
    private val map = HashMap<K, V>()

    operator fun get(key: K): V? = lock.withLock { map[key] }
    operator fun set(key: K, value: V) { lock.withLock { map[key] = value } }
    fun remove(key: K): V? = lock.withLock { map.remove(key) }
    fun clear() = lock.withLock { map.clear() }
    fun getOrPut(key: K, defaultValue: () -> V): V = lock.withLock { map.getOrPut(key, defaultValue) }
    fun putIfAbsent(key: K, value: V): V? = lock.withLock { map[key] ?: run { map[key] = value; null } }
    fun containsKey(key: K): Boolean = lock.withLock { map.containsKey(key) }
    fun snapshot(): Map<K, V> = lock.withLock { map.toMap() }
    val size: Int get() = lock.withLock { map.size }
}
