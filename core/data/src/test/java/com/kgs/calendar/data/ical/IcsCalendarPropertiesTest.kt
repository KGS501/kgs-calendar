package com.kgs.calendar.data.ical

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcsCalendarPropertiesTest {
    @Test
    fun readsNameAndImageUriOfTheCalendarOnly() {
        val properties = IcsCalendarProperties.parse(
            listOf(
                "BEGIN:VCALENDAR",
                "X-WR-CALNAME:Holidays\\, Bavaria",
                "IMAGE;VALUE=URI;DISPLAY=BADGE:https://example.org/",
                " logo.png",
                "BEGIN:VEVENT",
                "NAME:Not the calendar",
                "IMAGE;VALUE=URI:https://example.org/event.png",
                "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\r\n"),
        )

        assertEquals("Holidays, Bavaria", properties.name)
        assertEquals("https://example.org/logo.png", properties.imageUrl)
        assertNull(properties.imageBytes)
    }

    @Test
    fun prefersRfc7986NameAndDecodesInlineImages() {
        val properties = IcsCalendarProperties.parse(
            "BEGIN:VCALENDAR\nX-WR-CALNAME:Old\nNAME:New\nIMAGE;ENCODING=BASE64;VALUE=BINARY;FMTTYPE=image/png:AQID\nEND:VCALENDAR\n",
        )

        assertEquals("New", properties.name)
        assertNull(properties.imageUrl)
        assertArrayEquals(byteArrayOf(1, 2, 3), properties.imageBytes)
    }

    @Test
    fun feedsWithoutPropertiesHaveNone() {
        val properties = IcsCalendarProperties.parse("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR")

        assertEquals(IcsCalendarProperties(null, null, null), properties)
    }
}
