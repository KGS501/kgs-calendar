package com.kgs.calendar.desktop

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Fills a fresh profile with three months of realistic, synthetic items for previews and manual
 * testing: overlapping meetings, multi-day and stacked all-day events, a weekly series, reminders
 * and tasks with dates, priorities and overdue items. Only used with `--demo`; never touches a
 * profile that already has items.
 */
suspend fun seedDemoData(repository: CalendarRepository, today: LocalDate = LocalDate.now()) {
    repository.ensureLocalCalendar()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    suspend fun event(
        title: String,
        date: LocalDate,
        start: LocalTime? = null,
        end: LocalTime? = null,
        endDate: LocalDate? = null,
        location: String? = null,
        notes: String? = null,
        color: Long? = null,
        rule: String? = null,
        reminders: List<Int> = emptyList(),
        status: String? = null,
    ) = repository.createEvent(
        EventEditPayload(
            title = title,
            collectionHref = null,
            date = date,
            endDate = endDate,
            startTime = start,
            endTime = end,
            allDay = start == null,
            description = notes,
            location = location,
            locationMapVerified = null,
            manualColor = color?.toInt(),
            recurrenceRule = rule,
            reminderMinutes = reminders,
            status = status,
        ),
    )

    suspend fun task(
        title: String,
        due: LocalDate?,
        dueTime: LocalTime? = null,
        start: LocalDate? = null,
        priority: Int? = null,
        done: Boolean = false,
        notes: String? = null,
    ) = repository.createTask(
        TaskEditPayload(
            title = title,
            collectionHref = null,
            notes = notes,
            location = null,
            locationMapVerified = null,
            manualColor = null,
            url = null,
            categories = null,
            startDate = start,
            startTime = null,
            startHasTime = false,
            dueDate = due,
            dueTime = dueTime,
            dueHasTime = dueTime != null,
            priority = priority,
            percentComplete = null,
            isCompleted = done,
            recurrenceRule = null,
        ),
    )

    val t = { hour: Int, minute: Int -> LocalTime.of(hour, minute) }
    val teal = 0xFF00897B
    val purple = 0xFF7E57C2
    val orange = 0xFFF57C00
    val pink = 0xFFD81B60
    val green = 0xFF43A047

    // A weekly rhythm across the whole period.
    event("Team stand-up", monday, t(9, 0), t(9, 15), rule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", reminders = listOf(5))
    event("Gym", monday.plusDays(1), t(18, 30), t(19, 45), location = "FitX Innenstadt", color = green, rule = "FREQ=WEEKLY;INTERVAL=1")
    event("Choir rehearsal", monday.plusDays(3), t(19, 30), t(21, 0), location = "Gemeindehaus", color = purple, rule = "FREQ=WEEKLY")

    for (week in -4L..8L) {
        val base = monday.plusWeeks(week)
        event("Project sync", base.plusDays(1), t(10, 0), t(11, 0), location = "Room 2.14", notes = "Agenda: roadmap, open issues, release date.")
        event("1:1 with Anna", base.plusDays(1), t(10, 30), t(11, 15), color = teal)
        event("Design review", base.plusDays(2), t(14, 0), t(15, 30), location = "Studio", color = pink)
        event("Lunch with Max", base.plusDays(3), t(12, 15), t(13, 15), location = "Café Lindner, Hauptstraße 12, Heidelberg", color = orange)
        event("Planning", base.plusDays(3), t(15, 0), t(16, 0))
        event("Planning follow-up", base.plusDays(3), t(15, 30), t(16, 30), status = if (week % 2 == 0L) "TENTATIVE" else null)
        event("Customer call", base.plusDays(4), t(11, 0), t(11, 45), reminders = listOf(10))
        if (week % 3 == 0L) event("Dentist", base.plusDays(4), t(8, 0), t(8, 45), location = "Dr. Weber", reminders = listOf(60))
        if (week % 2 == 0L) event("Dinner with family", base.plusDays(5), t(18, 0), t(21, 0), color = orange)
        if (week % 2 != 0L) event("Hiking", base.plusDays(6), t(9, 0), t(16, 0), location = "Königstuhl", color = green)
        event("Laundry day", base.plusDays(5), color = teal)
    }

    // Multi-day and overlapping all-day events, so the all-day area stacks, overflows and collapses.
    event("Conference", monday.plusDays(2), endDate = monday.plusDays(4), location = "Berlin", color = purple)
    event("Vacation", monday.plusWeeks(2).plusDays(4), endDate = monday.plusWeeks(3).plusDays(1), color = orange)
    event("Grandma visiting", monday.plusDays(3), endDate = monday.plusDays(6), color = pink)
    event("Public holiday", monday.plusDays(4))
    event("Release week", monday.plusWeeks(1), endDate = monday.plusWeeks(1).plusDays(4), color = teal)
    event("Hackathon", monday.plusWeeks(1).plusDays(3), endDate = monday.plusWeeks(1).plusDays(4), color = green)
    event("Office closed", monday.plusWeeks(1).plusDays(4))
    event("Birthday: Lisa", today.plusDays(1), color = pink, rule = "FREQ=YEARLY")
    event("Trip to Hamburg", monday.minusWeeks(2).plusDays(3), endDate = monday.minusWeeks(2).plusDays(6), color = orange)
    event("Cancelled workshop", today.plusDays(2), t(13, 0), t(14, 30), status = "CANCELLED")
    event("Late-night deploy", today.plusDays(3), t(22, 30), t(23, 45), notes = "Rollback plan: https://example.com/runbook")

    // Tasks: overdue, today, upcoming, undated and done.
    task("Submit expense report", today.minusDays(3), priority = 1)
    task("Renew passport", today.minusDays(1), priority = 5)
    task("Prepare slides", today, t(16, 0), priority = 1, notes = "Use the new template.")
    task("Call the plumber", today)
    task("Book train tickets", today.plusDays(2), priority = 5)
    task("Pay rent", today.plusDays(5), t(9, 0))
    task("Write blog post", today.plusDays(9), start = today.plusDays(4))
    task("Buy a gift for Lisa", today.plusDays(1), priority = 1)
    task("Plan vacation route", null)
    task("Read 'Deep Work'", null, priority = 9)
    task("Clean the garage", today.minusDays(6), done = true)
    task("Order new monitor", today.minusDays(2), done = true)
}
