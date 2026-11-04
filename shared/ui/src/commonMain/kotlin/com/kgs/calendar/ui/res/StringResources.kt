package com.kgs.calendar.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.kgs.calendar.ui.LocalAppLocale
import com.kgs.calendar.time.AppLocale

/** A UI string in the languages the app ships (English, German). */
class StringResource(val key: String, val en: String, val de: String?) {
    fun text(locale: AppLocale): String = if (locale.isGerman() && de != null) de else en
    override fun equals(other: Any?): Boolean = other is StringResource && other.key == key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = key
}

/** A plural string; quantities follow CLDR's rules for English and German (one, other). */
class PluralsResource(val key: String, val en: Map<String, String>, val de: Map<String, String>?) {
    fun text(locale: AppLocale, count: Int): String {
        val forms = if (locale.isGerman() && de != null) de else en
        val quantity = if (count == 1) "one" else "other"
        return forms[quantity] ?: forms["other"] ?: forms.values.first()
    }
    override fun equals(other: Any?): Boolean = other is PluralsResource && other.key == key
    override fun hashCode(): Int = key.hashCode()
}

private fun AppLocale.isGerman(): Boolean = getLanguage().equals("de", ignoreCase = true)


@Composable
@ReadOnlyComposable
fun stringResource(resource: StringResource): String = resource.text(LocalAppLocale.current)

@Composable
@ReadOnlyComposable
fun stringResource(resource: StringResource, vararg args: Any?): String =
    formatAndroidString(resource.text(LocalAppLocale.current), args)

@Composable
@ReadOnlyComposable
fun pluralStringResource(resource: PluralsResource, count: Int): String =
    resource.text(LocalAppLocale.current, count)

@Composable
@ReadOnlyComposable
fun pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any?): String =
    formatAndroidString(resource.text(LocalAppLocale.current, count), args)

/** Outside composition (view models, notifications). */
fun StringResource.localized(locale: AppLocale, vararg args: Any?): String = formatAndroidString(text(locale), args)

fun PluralsResource.localized(locale: AppLocale, count: Int, vararg args: Any?): String =
    formatAndroidString(text(locale, count), args)

private val FormatPlaceholder = Regex("""(\d+\$)?([sdf])|(\d+\$)?\.(\d+)f""")

/** Android's `%s`, `%d`, `%1$s`, `%2$d` and `%%`, which are all the app's strings use. */
fun formatAndroidString(template: String, args: Array<out Any?>): String {
    if ('%' !in template) return template
    val out = StringBuilder(template.length + 16)
    var next = 0
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c != '%' || i + 1 >= template.length) {
            out.append(c)
            i++
            continue
        }
        if (template[i + 1] == '%') {
            out.append('%')
            i += 2
            continue
        }
        val match = FormatPlaceholder.matchAt(template, i + 1)
        if (match == null) {
            out.append(c)
            i++
            continue
        }
        val position = (match.groupValues[1].ifEmpty { match.groupValues[3] }).removeSuffix("$").toIntOrNull()
        val arg = args.getOrNull((position ?: (next + 1)) - 1)
        if (position == null) next++
        out.append(
            when {
                match.groupValues[4].isNotEmpty() -> (arg as? Number)?.toDouble()?.let { formatDecimal(it, match.groupValues[4].toInt()) } ?: arg.toString()
                match.groupValues[2] == "d" -> (arg as? Number)?.toLong()?.toString() ?: arg.toString()
                else -> arg.toString()
            },
        )
        i += 1 + match.value.length
    }
    return out.toString()
}

private fun formatDecimal(value: Double, decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val rounded = kotlin.math.round(value * factor) / factor
    val text = rounded.toString()
    val dot = text.indexOf('.')
    return if (decimals == 0) text.substringBefore('.') else if (dot < 0) text + "." + "0".repeat(decimals)
    else text.substring(0, dot + 1) + text.substring(dot + 1).padEnd(decimals, '0').take(decimals)
}
