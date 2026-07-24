package ohi.andre.consolelauncher.compose

import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

fun CharSequence.toAnnotatedString(): AnnotatedString {
    if (this !is Spanned) return AnnotatedString(toString())

    return AnnotatedString.Builder(toString()).apply {
        getSpans(0, length, Any::class.java).forEach { span ->
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            when (span) {
                is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                is BackgroundColorSpan -> addStyle(SpanStyle(background = Color(span.backgroundColor)), start, end)
            }
        }
    }.toAnnotatedString()
}
