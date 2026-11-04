package com.kgs.calendar.data.secure

import com.kgs.calendar.platform.IOException

/**
 * The credential store could not be opened. Unlike a missing login this may clear up by itself, so
 * it counts as a connection-style failure: queued work stays and the account simply retries later.
 */
class CredentialsUnavailableException(message: String) : IOException(message)
