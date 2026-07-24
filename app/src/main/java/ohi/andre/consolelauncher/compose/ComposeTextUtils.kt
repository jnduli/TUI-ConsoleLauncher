package ohi.andre.consolelauncher.compose

import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
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

data class ClickableSpanRange(val start: Int, val end: Int, val span: ClickableSpan)

data class SpannedText(val annotatedString: AnnotatedString, val clickableSpans: List<ClickableSpanRange>)

/** Like [toAnnotatedString], but also captures [ClickableSpan]s (and subclasses, e.g. LongClickableSpan)
 * so a composable can dispatch taps back to them - see ClickableAnnotatedText. */
fun CharSequence.toSpannedText(): SpannedText {
    if (this !is Spanned) return SpannedText(AnnotatedString(toString()), emptyList())

    val clickableSpans = mutableListOf<ClickableSpanRange>()
    val annotatedString = AnnotatedString.Builder(toString()).apply {
        getSpans(0, length, Any::class.java).forEach { span ->
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            when (span) {
                is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                is BackgroundColorSpan -> addStyle(SpanStyle(background = Color(span.backgroundColor)), start, end)
            }
            if (span is ClickableSpan) clickableSpans.add(ClickableSpanRange(start, end, span))
        }
    }.toAnnotatedString()

    return SpannedText(annotatedString, clickableSpans)
}
