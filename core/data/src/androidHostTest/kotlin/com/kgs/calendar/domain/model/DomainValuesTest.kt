package com.kgs.calendar.domain.model

import com.kgs.calendar.data.local.KgsTypeConverters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainValuesTest {
    private val converters = KgsTypeConverters()

    @Test
    fun storedValuesStayTheLegacyStrings() {
        assertEquals(
            listOf("local", "caldav", "readonly_url", "android_provider"),
            SourceType.entries.map(converters::sourceTypeToValue),
        )
        assertEquals(listOf("VEVENT", "VTODO", "UNKNOWN"), ComponentType.entries.map(converters::componentTypeToValue))
        assertEquals(listOf("PUT", "DELETE", "UNKNOWN"), MutationAction.entries.map(converters::mutationActionToValue))
        assertEquals(listOf("idle", "syncing", "error"), SyncState.entries.map(converters::syncStateToValue))
    }

    @Test
    fun everyValueRoundTripsThroughTheConverters() {
        SourceType.entries.forEach { assertEquals(it, converters.sourceTypeFromValue(converters.sourceTypeToValue(it))) }
        ComponentType.entries.forEach { assertEquals(it, converters.componentTypeFromValue(converters.componentTypeToValue(it))) }
        MutationAction.entries.forEach { assertEquals(it, converters.mutationActionFromValue(converters.mutationActionToValue(it))) }
        SyncState.entries.forEach { assertEquals(it, converters.syncStateFromValue(converters.syncStateToValue(it))) }
    }

    @Test
    fun unknownSourceTypeFallsBackToCalDav() {
        assertEquals(SourceType.CalDav, SourceType.fromValue("webcal"))
        assertEquals(SourceType.CalDav, SourceType.fromValue(""))
        assertEquals(SourceType.CalDav, SourceType.fromValue(null))
        assertEquals(SourceType.CalDav, SourceType.fromValue("LOCAL"))
    }

    @Test
    fun componentTypeMatchesCaseInsensitivelyAndFallsBackToUnknown() {
        assertEquals(ComponentType.Event, ComponentType.fromValue("vevent"))
        assertEquals(ComponentType.Task, ComponentType.fromValue("VTODO"))
        assertEquals(ComponentType.Unknown, ComponentType.fromValue("UNKNOWN"))
        assertEquals(ComponentType.Unknown, ComponentType.fromValue("VJOURNAL"))
        assertEquals(ComponentType.Unknown, ComponentType.fromValue(null))
    }

    @Test
    fun unknownMutationActionIsNeitherPutNorDelete() {
        assertEquals(MutationAction.Put, MutationAction.fromValue("PUT"))
        assertEquals(MutationAction.Delete, MutationAction.fromValue("DELETE"))
        assertEquals(MutationAction.Unknown, MutationAction.fromValue("PATCH"))
        assertEquals(MutationAction.Unknown, MutationAction.fromValue("put"))
        assertEquals(MutationAction.Unknown, MutationAction.fromValue(null))
    }

    @Test
    fun unknownSyncStateFallsBackToIdle() {
        assertEquals(SyncState.Syncing, SyncState.fromValue("syncing"))
        assertEquals(SyncState.Error, SyncState.fromValue("error"))
        assertEquals(SyncState.Idle, SyncState.fromValue("paused"))
        assertEquals(SyncState.Idle, SyncState.fromValue(null))
    }

    @Test
    fun taskStatusMatchesKnownValuesCaseInsensitivelyAndKeepsOthersVerbatim() {
        assertNull(TaskStatus.from(null))
        assertEquals(TaskStatus.InProcess, TaskStatus.from("in-process"))
        assertEquals(TaskStatus.NeedsAction, TaskStatus.from("NEEDS-ACTION"))
        assertEquals(TaskStatus.Other("X-WAITING"), TaskStatus.from("X-WAITING"))
        assertEquals("x-waiting", TaskStatus.from("x-waiting")!!.value)
        assertEquals("", TaskStatus.from("")!!.value)
        assertTrue(TaskStatus.from("Completed")!!.closesTask)
        assertTrue(TaskStatus.Cancelled.closesTask)
        assertFalse(TaskStatus.InProcess.closesTask)
        assertFalse(TaskStatus.Other("COMPLETED-ISH").closesTask)
    }

    @Test
    fun eventPropertiesMatchKnownValuesCaseInsensitivelyAndKeepOthersVerbatim() {
        assertEquals(EventStatus.Tentative, EventStatus.from("tentative"))
        assertEquals(EventStatus.Other("CANCELED"), EventStatus.from("CANCELED"))
        assertNull(EventStatus.from(null))
        assertEquals(EventClassification.Confidential, EventClassification.from("Confidential"))
        assertEquals("X-SECRET", EventClassification.from("X-SECRET")!!.value)
        assertEquals(EventTransparency.Transparent, EventTransparency.from("transparent"))
        assertEquals(EventTransparency.Other("free"), EventTransparency.from("free"))
    }
}
