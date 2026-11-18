package com.kgs.calendar.platform

actual open class IOException : Exception {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
    actual constructor(message: String?, cause: Throwable?) : super(message, cause)
    actual constructor(cause: Throwable?) : super(cause)
}

actual open class MalformedURLException : IOException {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
}

actual open class UnknownHostException : IOException {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
}

actual open class SocketTimeoutException : IOException {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
}

actual open class DatabaseException(message: String? = null) : RuntimeException(message)
