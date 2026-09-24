package com.kgs.calendar.widget

import android.os.Build
import com.kgs.calendar.widget.model.usesDirectCollectionItemsAtSdk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCollectionTransportTest {
    @Test
    fun tasksUseDirectCollectionItemsOnAndroidSAndNewer() {
        assertFalse(KgsWidgetKind.Tasks.usesDirectCollectionItemsAtSdk(Build.VERSION_CODES.R))
        assertTrue(KgsWidgetKind.Tasks.usesDirectCollectionItemsAtSdk(Build.VERSION_CODES.S))
        assertTrue(KgsWidgetKind.Tasks.usesDirectCollectionItemsAtSdk(Int.MAX_VALUE))
    }

    @Test
    fun bitmapHeavyAgendaCollectionsAlwaysUseRemoteViewsService() {
        assertFalse(KgsWidgetKind.Agenda.usesDirectCollectionItemsAtSdk(Build.VERSION_CODES.S))
        assertFalse(KgsWidgetKind.Agenda.usesDirectCollectionItemsAtSdk(Int.MAX_VALUE))
        assertFalse(KgsWidgetKind.Multi.usesDirectCollectionItemsAtSdk(Build.VERSION_CODES.S))
        assertFalse(KgsWidgetKind.Multi.usesDirectCollectionItemsAtSdk(Int.MAX_VALUE))
    }

    @Test
    fun nonListWidgetsDoNotUseDirectCollectionItems() {
        assertFalse(KgsWidgetKind.Month.usesDirectCollectionItemsAtSdk(Int.MAX_VALUE))
        assertFalse(KgsWidgetKind.Day.usesDirectCollectionItemsAtSdk(Int.MAX_VALUE))
    }
}
