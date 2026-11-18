package com.kgs.calendar.platform

import java.util.concurrent.locks.ReentrantLock

actual class KgsLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
