package com.kgs.calendar.ui.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.desktop.WindowsChrome
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.source.isLocalAccount
import com.kgs.calendar.ui.LocalAppLocale
import com.kgs.calendar.ui.res.pluralStringResource
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.KgsAppLogo
import com.kgs.calendar.ui.MotionEmphasized
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.isDark
import com.kgs.calendar.ui.platform.ShortcutAction
import com.kgs.calendar.ui.platform.ShortcutBindings
import com.kgs.calendar.ui.platform.desktopShortcutLabel
import com.kgs.calendar.ui.popupSurfaceColor
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import androidx.compose.foundation.isSystemInDarkTheme
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.ui.res.Res

/**
 * The menu of the app's icon in the notification area: what is coming up next, the everyday actions
 * with their keys, the settings and quitting, in the app's own look. [anchor] is where the icon was
 * clicked (in screen coordinates); the menu opens just above it, like the system's own menus.
 */
@Composable
fun KgsTrayMenu(
    anchor: Point?,
    viewModel: CalendarViewModel?,
    themeMode: AppThemeMode,
    colorMode: AppColorMode,
    shortcuts: ShortcutBindings?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAction: (ShortcutAction) -> Unit,
    onQuit: () -> Unit,
    /** The app's logo. The menu is a window of its own, which Windows shows by name and icon, e.g. while shutting down. */
    icon: Painter? = null,
) {
    val open = anchor != null
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(open) { LocalDate.now(zone) }
    val state = viewModel?.uiState?.collectAsState()?.value
    val range by remember(viewModel, today, open) {
        if (viewModel != null && open) viewModel.observeRange(today, today.plusDays(2)) else flowOf(null)
    }.collectAsState(null)
    val now = remember(open) { nowMillis() }
    val upcoming = remember(range, now) {
        range?.first.orEmpty()
            .filter { it.endsAtMillis > now && !it.status.equals("CANCELLED", ignoreCase = true) }
            .sortedWith(compareBy<EventEntity>({ !it.allDay }, { it.startsAtMillis }))
            .take(MaxUpcoming)
    }
    val tasksDueToday = remember(range, today) {
        range?.second.orEmpty().count { task ->
            !task.isCompleted && task.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        }
    }
    val canSync = state?.accounts.orEmpty().any { !it.isLocalAccount() }

    val agendaRows = upcoming.size.coerceAtLeast(1) + if (tasksDueToday > 0) 1 else 0
    val actionRows = 4 + if (canSync) 1 else 0
    val height = VerticalPadding * 2 + HeaderHeight + DividerHeight + LabelHeight + RowHeight * agendaRows +
        DividerHeight + ActionHeight * actionRows + DividerHeight + ActionHeight
    val dialogState = rememberDialogState(size = DpSize(MenuWidth, height))
    // Above the clicked point, kept inside the work area of its screen (clear of the taskbar).
    if (anchor != null) {
        val area = workAreaAt(anchor)
        val width = MenuWidth.value.toInt()
        val x = (anchor.x - width / 2).coerceIn(area.x + ScreenMargin, (area.x + area.width - width - ScreenMargin).coerceAtLeast(area.x))
        val bottom = minOf(anchor.y, area.y + area.height) - ScreenMargin
        val y = (bottom - height.value.toInt()).coerceAtLeast(area.y + ScreenMargin)
        dialogState.size = DpSize(MenuWidth, height)
        dialogState.position = WindowPosition(x.dp, y.dp)
    }
    val dark = when (state?.colorMode ?: colorMode) {
        AppColorMode.Auto -> isSystemInDarkTheme()
        AppColorMode.Light -> false
        AppColorMode.Dark -> true
    }
    val currentDismiss by rememberUpdatedState(onDismiss)
    DialogWindow(
        onCloseRequest = onDismiss,
        visible = open,
        state = dialogState,
        title = "KGS Calendar",
        icon = icon,
        undecorated = true,
        resizable = false,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        DisposableEffect(window) {
            window.isAlwaysOnTop = true
            // Clicking anywhere else closes it, as menus do.
            val listener = object : WindowAdapter() {
                override fun windowDeactivated(e: WindowEvent?) = currentDismiss()
            }
            window.addWindowListener(listener)
            onDispose { window.removeWindowListener(listener) }
        }
        val reveal = remember { Animatable(0f) }
        LaunchedEffect(anchor) {
            if (anchor == null) {
                reveal.snapTo(0f)
                return@LaunchedEffect
            }
            window.toFront()
            window.requestFocus()
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(RevealMs, easing = MotionEmphasized))
        }
        KgsCalendarTheme(themeMode = state?.themeMode ?: themeMode, darkTheme = dark) {
            val surface = popupSurfaceColor()
            val border = WarmInk.copy(alpha = if (surface.isDark()) 0.16f else 0.12f)
            LaunchedEffect(surface, border) {
                WindowsChrome.roundMenuCorners(window, surface.compositeOnto(), border.compositeOnto(surface))
                window.background = java.awt.Color(surface.toArgb() or (0xFF shl 24))
            }
            CompositionLocalProvider(LocalAppLocale provides Locale.getDefault()) {
                Box(Modifier.fillMaxSize().background(surface)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = reveal.value
                                translationY = (1f - reveal.value) * 6.dp.toPx()
                            }
                            .padding(vertical = VerticalPadding),
                    ) {
                        Header(today, onClick = { onDismiss(); onOpen() })
                        Divider()
                        SectionLabel(stringResource(Res.string.tray_up_next))
                        if (upcoming.isEmpty()) {
                            AgendaRow(color = null, time = "", title = stringResource(Res.string.tray_nothing_planned), muted = true) { onDismiss(); onOpen() }
                        }
                        upcoming.forEach { event ->
                            AgendaRow(
                                color = Color(event.displayColor()),
                                time = event.timeLabel(today, zone),
                                title = event.title,
                            ) { onDismiss(); onOpen() }
                        }
                        if (tasksDueToday > 0) {
                            AgendaRow(color = null, time = "", title = pluralStringResource(Res.plurals.tray_tasks_due, tasksDueToday, tasksDueToday)) {
                                onDismiss(); onAction(ShortcutAction.Tasks)
                            }
                        }
                        Divider()
                        ActionRow(Icons.Outlined.Add, stringResource(Res.string.new_event), shortcuts.labelFor(ShortcutAction.NewEvent)) {
                            onDismiss(); onAction(ShortcutAction.NewEvent)
                        }
                        ActionRow(Icons.Outlined.TaskAlt, stringResource(Res.string.new_task), shortcuts.labelFor(ShortcutAction.NewTask)) {
                            onDismiss(); onAction(ShortcutAction.NewTask)
                        }
                        ActionRow(Icons.Outlined.Search, stringResource(Res.string.search), shortcuts.labelFor(ShortcutAction.Search)) {
                            onDismiss(); onAction(ShortcutAction.Search)
                        }
                        if (canSync) {
                            ActionRow(Icons.Outlined.Sync, stringResource(Res.string.sync_now), shortcuts.labelFor(ShortcutAction.Refresh)) {
                                onDismiss(); viewModel?.sources?.syncNow()
                            }
                        }
                        ActionRow(Icons.Outlined.Settings, stringResource(Res.string.settings), shortcuts.labelFor(ShortcutAction.Settings)) {
                            onDismiss(); onAction(ShortcutAction.Settings)
                        }
                        Divider()
                        ActionRow(Icons.Outlined.PowerSettingsNew, stringResource(Res.string.tray_quit), null) {
                            onDismiss(); onQuit()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(today: LocalDate, onClick: () -> Unit) {
    val locale = LocalAppLocale.current
    MenuRow(height = HeaderHeight, onClick = onClick) {
        KgsAppLogo(Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.tray_open),
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(Modifier.fillMaxWidth().height(LabelHeight).padding(horizontal = 18.dp), contentAlignment = Alignment.BottomStart) {
        Text(
            text.uppercase(LocalAppLocale.current),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun AgendaRow(color: Color?, time: String, title: String, muted: Boolean = false, onClick: () -> Unit) {
    MenuRow(height = RowHeight, onClick = onClick) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.CenterStart) {
            if (color != null) Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        if (time.isNotEmpty()) {
            Text(
                time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                modifier = Modifier.width(TimeColumnWidth),
            )
        }
        Text(
            title,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else WarmInk,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, shortcut: String?, onClick: () -> Unit) {
    MenuRow(height = ActionHeight, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = WarmInk.copy(alpha = 0.82f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = WarmInk, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (shortcut != null) {
            Text(shortcut, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1)
        }
    }
}

/** A row of the menu, tinted while hovered, inset from the menu's edges like Windows 11's menus. */
@Composable
private fun MenuRow(height: Dp, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .background(if (hovered) WarmInk.copy(alpha = 0.07f) else Color.Transparent, RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(DividerHeight), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(WarmInk.copy(alpha = 0.09f)))
    }
}

@Composable
private fun EventEntity.timeLabel(today: LocalDate, zone: ZoneId): String {
    val locale = LocalAppLocale.current
    val start = Instant.ofEpochMilli(startsAtMillis).atZone(zone)
    val day = start.toLocalDate()
    val dayLabel = when {
        allDay || !day.isAfter(today) -> null
        day == today.plusDays(1) -> stringResource(Res.string.tray_tomorrow)
        else -> day.format(DateTimeFormatter.ofPattern("EEE", locale))
    }
    val time = if (allDay) stringResource(Res.string.all_day) else start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return if (dayLabel != null) "$dayLabel $time" else time
}

private fun ShortcutBindings?.labelFor(action: ShortcutAction): String? =
    this?.bindings?.get(action)?.let(::desktopShortcutLabel)

/** The work area (the screen without the taskbar) of the screen with [point]. */
private fun workAreaAt(point: Point): Rectangle {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val configuration = environment.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(point) }?.defaultConfiguration
        ?: environment.defaultScreenDevice.defaultConfiguration
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return Rectangle(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
}

private fun Color.compositeOnto(base: Color = Color.Black): Color =
    Color(
        red = red * alpha + base.red * (1 - alpha),
        green = green * alpha + base.green * (1 - alpha),
        blue = blue * alpha + base.blue * (1 - alpha),
    )

private val MenuWidth = 320.dp
private val VerticalPadding = 6.dp
private val HeaderHeight = 58.dp
private val DividerHeight = 9.dp
private val LabelHeight = 24.dp
private val RowHeight = 32.dp
private val ActionHeight = 36.dp
private val TimeColumnWidth = 92.dp
private const val ScreenMargin = 8
private const val MaxUpcoming = 4
private const val RevealMs = 160
