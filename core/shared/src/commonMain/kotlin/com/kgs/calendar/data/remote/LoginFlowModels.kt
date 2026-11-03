package com.kgs.calendar.data.remote

data class LoginFlowStart(
    val loginUrl: String,
    val pollEndpoint: String,
    val token: String,
)

data class LoginFlowResult(
    val serverUrl: String,
    val loginName: String,
    val appPassword: String,
)
