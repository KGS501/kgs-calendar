package com.kgs.calendar.platform

import platform.Foundation.NSRecursiveLock

actual class KgsLock actual constructor() {
    private val lock = NSRecursiveLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
