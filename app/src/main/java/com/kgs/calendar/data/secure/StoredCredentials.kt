package com.kgs.calendar.data.secure

data class StoredCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
)
