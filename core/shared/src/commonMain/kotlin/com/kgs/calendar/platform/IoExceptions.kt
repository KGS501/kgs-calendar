package com.kgs.calendar.platform

/** java.io.IOException on the JVM; the same hierarchy on iOS. */
expect open class IOException : Exception {
    constructor()
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
    constructor(cause: Throwable?)
}

expect open class MalformedURLException : IOException {
    constructor()
    constructor(message: String?)
}

expect open class UnknownHostException : IOException {
    constructor()
    constructor(message: String?)
}

expect open class SocketTimeoutException : IOException {
    constructor()
    constructor(message: String?)
}

/** A database write or read failed (android.database.SQLException / androidx.sqlite.SQLiteException). */
expect open class DatabaseException : RuntimeException
