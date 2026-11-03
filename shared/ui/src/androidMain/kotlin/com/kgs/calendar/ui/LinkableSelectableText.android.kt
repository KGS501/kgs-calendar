package com.kgs.calendar.ui

import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun LinkableSelectableText(value: String, onClick: (() -> Unit)?) {
    val textColor = WarmInk
    val linkColor = MaterialTheme.colorScheme.primary
    val highlightColor = WarmPeach.copy(alpha = 0.64f).toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                includeFontPadding = false
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.08f)
                this.highlightColor = highlightColor
            }
        },
        update = { textView ->
            val linkedText = SpannableString(value)
            Linkify.addLinks(linkedText, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
            textView.text = linkedText
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            textView.isClickable = onClick != null
            textView.setOnClickListener(if (onClick != null) android.view.View.OnClickListener { onClick() } else null)
        },
    )
}
