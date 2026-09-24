package com.kgs.calendar.data.secure

interface CredentialsStore {
    fun save(credentials: StoredCredentials)

    fun save(accountId: String, credentials: StoredCredentials)

    fun get(accountId: String = PRIMARY_ID): StoredCredentials?

    fun clear()

    fun clear(accountId: String)

    companion object {
        const val PRIMARY_ID = "primary"
    }
}
