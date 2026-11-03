package com.kgs.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val LINK_PATTERN = Regex("""(https?://[^\s<>"]+|www\.[^\s<>"]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""")

@Composable
actual fun LinkableSelectableText(value: String, onClick: (() -> Unit)?) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(value, linkColor) {
        buildAnnotatedString {
            var last = 0
            for (match in LINK_PATTERN.findAll(value)) {
                append(value.substring(last, match.range.first))
                val raw = match.value.trimEnd('.', ',', ')', ';', ':')
                val url = when {
                    '@' in raw && !raw.contains("://") -> "mailto:$raw"
                    raw.startsWith("www.") -> "https://$raw"
                    else -> raw
                }
                pushLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))))
                append(raw)
                pop()
                append(match.value.substring(raw.length))
                last = match.range.last + 1
            }
            append(value.substring(last))
        }
    }
    SelectionContainer {
        Text(
            text,
            color = WarmInk,
            fontSize = 16.sp,
            lineHeight = (16 * 1.08 * 1.2).sp,
            modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}
