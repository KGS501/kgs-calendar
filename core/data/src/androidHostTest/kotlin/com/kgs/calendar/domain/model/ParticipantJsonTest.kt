package com.kgs.calendar.domain.model

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantJsonTest {
    private val fullAttendee = Attendee(
        email = "alice@example.com",
        name = "Doe, \"Ali\" \\ Ünïcødé ✓",
        partstat = "ACCEPTED",
        role = "OPT-PARTICIPANT",
        rsvp = "TRUE",
        calendarUserType = "INDIVIDUAL",
        member = "mailto:team@example.com",
        delegatedTo = "mailto:bob@example.com",
        delegatedFrom = "mailto:carol@example.com",
        sentBy = "mailto:assistant@example.com",
        directory = "https://dir.example.com/alice?x=1&y=2",
        language = "de",
        scheduleAgent = "CLIENT",
        scheduleForceSend = "REQUEST",
        scheduleStatus = "2.0;Success",
    )

    @Test
    fun attendeesRoundTrip() {
        val attendees = listOf(fullAttendee, Attendee(email = "bob@example.com"))

        assertEquals(attendees, ParticipantJson.decodeAttendees(ParticipantJson.encodeAttendees(attendees)))
    }

    @Test
    fun organizerRoundTrips() {
        val organizer = Organizer(
            email = "host@example.com",
            name = "Line\nbreak\ttab \"quoted\" é",
            sentBy = "mailto:assistant@example.com",
            directory = "ldap://dir/x",
            language = "en",
        )

        assertEquals(organizer, ParticipantJson.decodeOrganizer(ParticipantJson.encodeOrganizer(organizer)))
        assertEquals(Organizer(email = "x@y.z"), ParticipantJson.decodeOrganizer(ParticipantJson.encodeOrganizer(Organizer(email = "x@y.z"))))
    }

    @Test
    fun readsTheLegacyHandRolledFormatWithExplicitNulls() {
        val organizer = """{"name":"Host \"H\" Person","email":"host@example.com","sentBy":null,"directory":null,"language":null}"""
        val attendees = """[{"name":"Doe, Alice","email":"alice@example.com","partstat":"ACCEPTED","role":"REQ-PARTICIPANT","rsvp":null,""" +
            """"calendarUserType":null,"member":null,"delegatedTo":null,"delegatedFrom":null,"sentBy":null,"directory":null,""" +
            """"language":null,"scheduleAgent":null,"scheduleForceSend":null,"scheduleStatus":"3.7"}]"""

        assertEquals(Organizer(email = "host@example.com", name = "Host \"H\" Person"), ParticipantJson.decodeOrganizer(organizer))
        assertEquals(
            listOf(
                Attendee(
                    email = "alice@example.com",
                    name = "Doe, Alice",
                    partstat = "ACCEPTED",
                    role = "REQ-PARTICIPANT",
                    scheduleStatus = "3.7",
                ),
            ),
            ParticipantJson.decodeAttendees(attendees),
        )
    }

    @Test
    fun readsTheLegacyEditorFormatWithoutNullFields() {
        val attendees = """[{"name":"b@example.com","email":"b@example.com","partstat":"NEEDS-ACTION","role":"REQ-PARTICIPANT","rsvp":"FALSE"}]"""

        assertEquals(
            listOf(Attendee(email = "b@example.com", name = "b@example.com", partstat = "NEEDS-ACTION", role = "REQ-PARTICIPANT", rsvp = "FALSE")),
            ParticipantJson.decodeAttendees(attendees),
        )
        assertEquals(Organizer(email = "me@example.com", name = "Me"), ParticipantJson.decodeOrganizer("""{"name":"Me","email":"me@example.com"}"""))
    }

    @Test
    fun decodesUnicodeEscapesAndWhitespace() {
        val json = """ [ { "email" : "u@example.com" , "name" : "Jürgen ✓" } ] """

        assertEquals(listOf(Attendee(email = "u@example.com", name = "Jürgen ✓")), ParticipantJson.decodeAttendees(json))
    }

    @Test
    fun jsonNullIsAbsentButTheStringNullIsKept() {
        val attendees = ParticipantJson.decodeAttendees(
            """[{"email":"a@example.com","scheduleStatus":null},{"email":"b@example.com","scheduleStatus":"null"}]""",
        )

        assertNull(attendees[0].scheduleStatus)
        assertEquals("null", attendees[1].scheduleStatus)
    }

    @Test
    fun missingFieldsAreAbsent() {
        assertEquals(listOf(Attendee(email = "a@example.com")), ParticipantJson.decodeAttendees("""[{"email":"a@example.com"}]"""))
        assertEquals(Organizer(email = ""), ParticipantJson.decodeOrganizer("""{"name":null}"""))
    }

    @Test
    fun nonStringValuesAreReadAsText() {
        assertEquals("true", ParticipantJson.decodeAttendees("""[{"email":"a@example.com","rsvp":true}]""").single().rsvp)
    }

    @Test
    fun emptyNullBlankAndMalformedInputDecodeToNothing() {
        listOf(null, "", "   ", "null", "[]", "[1,\"x\",null]", "{not json", "[{\"email\":\"a@example.com\"").forEach { input ->
            assertEquals(input, emptyList<Attendee>(), ParticipantJson.decodeAttendees(input))
        }
        listOf(null, "", "   ", "[]", "{not json").forEach { input ->
            assertNull(input, ParticipantJson.decodeOrganizer(input))
        }
    }

    @Test
    fun attendeesWithoutEmailAreSkippedAndSingleObjectsAreAccepted() {
        val json = """[{"name":"No mail"},{"email":"  ","name":"Blank"},{"email":"ok@example.com"}]"""

        assertEquals(listOf("ok@example.com"), ParticipantJson.decodeAttendees(json).map { it.email })
        assertEquals(listOf("solo@example.com"), ParticipantJson.decodeAttendees("""{"email":"solo@example.com"}""").map { it.email })
    }

    @Test
    fun emptyAttendeeListEncodesAsNull() {
        assertNull(ParticipantJson.encodeAttendees(emptyList()))
    }

    @Test
    fun encodedJsonKeepsEveryKeyForOldReaders() {
        val encoded = ParticipantJson.encodeAttendees(listOf(Attendee(email = "a@example.com")))!!
        val obj = JSONArray(encoded).getJSONObject(0)

        listOf(
            "name", "email", "partstat", "role", "rsvp", "calendarUserType", "member", "delegatedTo", "delegatedFrom",
            "sentBy", "directory", "language", "scheduleAgent", "scheduleForceSend", "scheduleStatus",
        ).forEach { key -> assertTrue(key, obj.has(key)) }
        assertTrue(obj.isNull("rsvp"))
    }

    @Test
    fun encodedJsonIsReadableByTheRemovedRegexReader() {
        val encoded = ParticipantJson.encodeAttendees(listOf(fullAttendee, Attendee(email = "bob@example.com", name = "Bob")))!!
        val bodies = LegacyJsonReader.objectBodies(encoded)

        assertEquals(2, bodies.size)
        assertEquals(fullAttendee.name, LegacyJsonReader.field(bodies[0], "name"))
        assertEquals(fullAttendee.directory, LegacyJsonReader.field(bodies[0], "directory"))
        assertEquals(fullAttendee.scheduleStatus, LegacyJsonReader.field(bodies[0], "scheduleStatus"))
        assertEquals("bob@example.com", LegacyJsonReader.field(bodies[1], "email"))
        assertNull(LegacyJsonReader.field(bodies[1], "partstat"))

        val organizer = ParticipantJson.encodeOrganizer(Organizer(email = "h@example.com", name = "Tab\there", directory = "https://d/x"))
        assertEquals("Tab\there", LegacyJsonReader.field(organizer, "name"))
        assertEquals("https://d/x", LegacyJsonReader.field(organizer, "directory"))
        assertNull(LegacyJsonReader.field(organizer, "sentBy"))
    }

    @Test
    fun withPartstatUpdatesMatchingAttendeesAndKeepsOtherFields() {
        val json = """[{"name":"A","email":"A@Example.com","partstat":"NEEDS-ACTION","member":"mailto:t@example.com"},{"email":"b@example.com","partstat":"NEEDS-ACTION"}]"""

        val updated = ParticipantJson.withPartstat(json, listOf(" a@example.com "), "accepted")!!
        val attendees = ParticipantJson.decodeAttendees(updated)

        assertEquals(listOf("ACCEPTED", "NEEDS-ACTION"), attendees.map { it.partstat })
        assertEquals("mailto:t@example.com", attendees[0].member)
        assertNull(ParticipantJson.withPartstat(json, listOf("nobody@example.com"), "ACCEPTED"))
        assertNull(ParticipantJson.withPartstat(json, listOf("a@example.com"), "DELEGATED"))
        assertNull(ParticipantJson.withPartstat(json, listOf(" "), "ACCEPTED"))
        assertNull(ParticipantJson.withPartstat(null, listOf("a@example.com"), "ACCEPTED"))
    }

    /** Copy of the regex-based reader that IcalCodec used before the codec existed. */
    private object LegacyJsonReader {
        fun field(json: String, field: String): String? {
            val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*(null|\"((?:\\\\.|[^\"\\\\])*)\")")
            val match = pattern.find(json) ?: return null
            if (match.groupValues[1] == "null") return null
            return unescape(match.groupValues[2])
        }

        fun objectBodies(json: String): List<String> {
            val result = mutableListOf<String>()
            var depth = 0
            var start = -1
            var inString = false
            var escaped = false
            json.forEachIndexed { index, char ->
                when {
                    escaped -> escaped = false
                    inString && char == '\\' -> escaped = true
                    inString && char == '"' -> inString = false
                    inString -> Unit
                    char == '"' -> inString = true
                    char == '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    char == '}' && depth > 0 -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            result += json.substring(start, index + 1)
                            start = -1
                        }
                    }
                }
            }
            return result
        }

        private fun unescape(value: String): String = buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char == '\\' && index + 1 < value.length) {
                    when (val escaped = value[index + 1]) {
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        'u' -> {
                            val decoded = value.drop(index + 2).take(4).takeIf { it.length == 4 }?.toIntOrNull(16)
                            if (decoded != null) {
                                append(decoded.toChar())
                                index += 4
                            } else {
                                append("\\u")
                            }
                        }
                        else -> append(escaped)
                    }
                    index += 2
                } else {
                    append(char)
                    index++
                }
            }
        }
    }
}
