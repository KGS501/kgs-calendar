package com.kgs.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class WebcalSubscriptionInstrumentedTest {
    @Test
    fun webcalFeedLoadsOverHttpOnAndroid() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = KgsCalendarApplication.graph(context).repository
        val feed = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//KGS//Webcal test//EN
            BEGIN:VEVENT
            UID:android-webcal-test
            DTSTAMP:20260928T000000Z
            DTSTART;VALUE=DATE:20261012
            DTEND;VALUE=DATE:20261013
            SUMMARY:HTTP subscription on Android
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n").toByteArray()
        ServerSocket(0).use { server ->
            server.soTimeout = 30_000
            val responder = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/calendar\r\nContent-Length: ${feed.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(feed)
                        flush()
                    }
                }
            }
            val url = "webcal://127.0.0.1:${server.localPort}/calendar.ics"
            withContext(Dispatchers.IO) {
                val account = repository.addReadOnlyCalendar(url, "Android webcal test")
                try {
                    assertEquals(url, account.serverUrl)
                    val event = repository.eventByResource("readonly-${account.id}/android-webcal-test.ics")
                    assertEquals("HTTP subscription on Android", event?.title)
                } finally {
                    repository.deleteAccount(account.id)
                }
            }
            responder.join(10_000)
        }
    }
}
