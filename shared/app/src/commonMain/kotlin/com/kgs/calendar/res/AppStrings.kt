package com.kgs.calendar.res

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** The language the app is shown in, which may differ from the system's (see the app language setting). */
val LocalAppLocale = compositionLocalOf { Locale.getDefault() }

/**
 * The shared strings ([R]) in a language. Formatting and plural selection follow Android's
 * `Resources.getString` and `getQuantityString`, so a text reads the same on every platform.
 */
object AppStrings {
    fun get(@StringRes id: Int, locale: Locale): String =
        StringTables.translations[locale.language]?.strings?.getOrNull(id) ?: StringTables.strings[id]

    fun get(@StringRes id: Int, locale: Locale, vararg formatArgs: Any?): String =
        if (formatArgs.isEmpty()) get(id, locale) else String.format(locale, get(id, locale), *formatArgs)

    fun quantity(@PluralsRes id: Int, count: Int, locale: Locale, vararg formatArgs: Any?): String {
        val quantity = pluralCategory(count, locale)
        val texts = StringTables.translations[locale.language]?.plurals?.getOrNull(id)
        val text = texts?.get(quantity) ?: texts?.get(OTHER)
            ?: StringTables.plurals[id][quantity] ?: StringTables.plurals[id][OTHER].orEmpty()
        return if (formatArgs.isEmpty()) text else String.format(locale, text, *formatArgs)
    }

    /** The plural category of [count]: "one" for exactly one in the app's languages, otherwise "other". */
    private fun pluralCategory(count: Int, locale: Locale): Int = when (locale.language) {
        "fr", "pt" -> if (count == 0 || count == 1) ONE else OTHER
        else -> if (count == 1) ONE else OTHER
    }

    private const val ONE = 1
    private const val OTHER = 5
}

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int): String = AppStrings.get(id, LocalAppLocale.current)

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    AppStrings.get(id, LocalAppLocale.current, *formatArgs)

@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes id: Int, count: Int): String =
    AppStrings.quantity(id, count, LocalAppLocale.current)

@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    AppStrings.quantity(id, count, LocalAppLocale.current, *formatArgs)
