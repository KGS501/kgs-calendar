package com.kgs.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.importing.IcsImportScreen
import com.kgs.calendar.importing.IcsImportViewModel
import com.kgs.calendar.importing.icsAttachmentUri
import com.kgs.calendar.importing.readIcsAttachment
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.theme.withAppFontScaleCap

class IcsImportActivity : ComponentActivity() {
    private val graph by lazy { KgsCalendarApplication.graph(this) }
    private val viewModel: IcsImportViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = IcsImportViewModel(
                imports = graph.eventImports,
                queries = graph.calendarQueries,
                sources = graph.calendarSources,
                settings = graph.settingsStore,
                readAttachment = graph.appContext.contentResolver::readIcsAttachment,
                onImported = { ReminderScheduler.reschedule(graph.appContext) },
            ) as T
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppFontScaleCap())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.load(intent.icsAttachmentUri())
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val theme by graph.settingsStore.themeMode.collectAsStateWithLifecycle(AppThemeMode.KgsBlue)
            KgsCalendarTheme(themeMode = theme) {
                IcsImportScreen(
                    state = state,
                    onSelectCalendar = viewModel::selectCalendar,
                    onImport = viewModel::importEvents,
                    onClose = ::finish,
                    onOpenCalendar = {
                        val start = state.events.firstOrNull()?.event?.startsAtMillis ?: System.currentTimeMillis()
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$start"), this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    },
                )
            }
        }
    }
}
