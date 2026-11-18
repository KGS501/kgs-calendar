package com.kgs.calendar.data.secure

interface CredentialsStore {
    fun save(credentials: StoredCredentials)

    fun save(accountId: String, credentials: StoredCredentials)

    fun get(accountId: String = PRIMARY_ID): StoredCredentials?

    fun clear()

    fun clear(accountId: String)

    /**
     * False while saved logins exist but can't be opened right now. Nothing has been lost at that
     * point: [get] and [save] throw [CredentialsUnavailableException] until this store opens again.
     */
    fun isReadable(): Boolean = true

    /** Deletes the saved logins of every account for good, including ones that can't be read. */
    fun resetUnreadable() = clear()

    companion object {
        const val PRIMARY_ID = "primary"
    }
}
