package com.kgs.calendar.desktop

import com.kgs.calendar.ui.platform.DefaultDesktopShortcuts
import com.kgs.calendar.ui.platform.KeyBinding
import com.kgs.calendar.ui.platform.ShortcutAction
import com.kgs.calendar.ui.platform.ShortcutBindings
import com.kgs.calendar.ui.platform.desktopShortcutLabel
import com.kgs.calendar.ui.platform.isDesktopModifierKey
import java.io.File
import java.util.Properties

/** Keeps the keyboard shortcuts changed in the settings; actions without an entry use their default key. */
class ShortcutStore(private val file: File) {
    fun restore(): ShortcutBindings {
        val values = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
        val initial = ShortcutAction.entries.mapNotNull { action ->
            val value = values.getProperty(action.name) ?: return@mapNotNull null
            action to parse(value)
        }.toMap()
        return ShortcutBindings(
            defaults = DefaultDesktopShortcuts,
            initial = initial,
            label = ::desktopShortcutLabel,
            isModifierOnly = ::isDesktopModifierKey,
            onChanged = ::save,
        )
    }

    private fun save(bindings: Map<ShortcutAction, KeyBinding?>) {
        val values = Properties()
        bindings.forEach { (action, binding) ->
            if (binding != DefaultDesktopShortcuts[action]) values[action.name] = format(binding)
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { values.store(it, null) }
        }
    }

    /** "none", or the key code followed by the modifiers held, e.g. "78,ctrl,shift". */
    private fun format(binding: KeyBinding?): String = if (binding == null) {
        "none"
    } else {
        listOfNotNull(
            binding.keyCode.toString(),
            "ctrl".takeIf { binding.ctrl },
            "shift".takeIf { binding.shift },
            "alt".takeIf { binding.alt },
        ).joinToString(",")
    }

    private fun parse(value: String): KeyBinding? {
        if (value == "none") return null
        val parts = value.split(',')
        val code = parts.firstOrNull()?.toLongOrNull() ?: return null
        return KeyBinding(code, ctrl = "ctrl" in parts, shift = "shift" in parts, alt = "alt" in parts)
    }
}
