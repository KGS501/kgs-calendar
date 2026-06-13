package com.kgs.calendar

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.CalendarViewModelFactory
import com.kgs.calendar.ui.KgsCalendarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val graph by lazy { KgsCalendarApplication.graph(this) }
    private val calendarViewModel: CalendarViewModel by viewModels { CalendarViewModelFactory(graph) }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyWidgetLaunch(intent)
        maybeRequestNotificationPermission()
        maybeRequestExactAlarmPermission()
        setContent {
            KgsCalendarApp(viewModel = calendarViewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyWidgetLaunch(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            val includeDisabledProviderCalendars = graph.settingsStore.showDisabledAndroidProviderCalendars.first()
            runCatching {
                graph.repository.refreshAndroidCalendarsIfEnabled(
                    removeStale = false,
                    includeDisabledProviderCalendars = includeDisabledProviderCalendars,
                )
            }
            runCatching { ReminderScheduler.reschedule(this@MainActivity) }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun maybeRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
    }

    private fun applyWidgetLaunch(intent: Intent?) {
        val target = intent.toWidgetLaunchTarget() ?: return
        calendarViewModel.openFromWidget(
            date = target.date,
            viewMode = target.viewMode,
            createEvent = target.createEvent,
            createTaskScheduled = target.createTaskScheduled,
            openTaskUid = target.openTaskUid,
        )
    }

    private fun Intent?.toWidgetLaunchTarget(): WidgetLaunchTarget? {
        val date = this
            ?.getStringExtra(EXTRA_WIDGET_DATE)
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
            ?: return null
        val viewMode = when (getStringExtra(EXTRA_WIDGET_KIND)) {
            "Month" -> CalendarViewMode.Month
            "Tasks" -> CalendarViewMode.Tasks
            "Agenda", "Multi" -> CalendarViewMode.Agenda
            else -> CalendarViewMode.Day
        }
        return WidgetLaunchTarget(
            date = date,
            viewMode = viewMode,
            createEvent = getStringExtra(EXTRA_WIDGET_ACTION) == WIDGET_ACTION_CREATE_EVENT,
            createTaskScheduled = when {
                getStringExtra(EXTRA_WIDGET_ACTION) != WIDGET_ACTION_CREATE_TASK -> null
                getStringExtra(EXTRA_WIDGET_TASK_CREATE_MODE) == WIDGET_TASK_CREATE_UNPLANNED -> false
                else -> true
            },
            openTaskUid = getStringExtra(EXTRA_WIDGET_TASK_UID)
                ?.takeIf { getStringExtra(EXTRA_WIDGET_ACTION) == WIDGET_ACTION_OPEN_TASK },
        )
    }

    private data class WidgetLaunchTarget(
        val date: LocalDate,
        val viewMode: CalendarViewMode,
        val createEvent: Boolean,
        val createTaskScheduled: Boolean?,
        val openTaskUid: String?,
    )

    private companion object {
        const val EXTRA_WIDGET_KIND = "kgs_widget_kind"
        const val EXTRA_WIDGET_DATE = "kgs_widget_date"
        const val EXTRA_WIDGET_ACTION = "kgs_widget_action"
        const val EXTRA_WIDGET_TASK_CREATE_MODE = "kgs_widget_task_create_mode"
        const val EXTRA_WIDGET_TASK_UID = "kgs_widget_task_uid"
        const val WIDGET_ACTION_CREATE_EVENT = "create_event"
        const val WIDGET_ACTION_CREATE_TASK = "create_task"
        const val WIDGET_ACTION_OPEN_TASK = "open_task"
        const val WIDGET_TASK_CREATE_UNPLANNED = "Unplanned"
    }
}
