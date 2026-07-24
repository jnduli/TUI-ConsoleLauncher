package ohi.andre.consolelauncher.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import ohi.andre.consolelauncher.tuils.LongClickableSpan

/**
 * Renders [spannedText], dispatching taps/long-presses on its [ClickableSpanRange]s to the
 * underlying span's onClick/onLongClick - the same dispatch [android.text.method.LinkMovementMethod]
 * does - so spans like LongClickableSpan (open a URL, re-run a previous command, notification popup
 * menu, etc.) keep working unmodified.
 */
@Composable
fun ClickableAnnotatedText(
    spannedText: SpannedText,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    shadow: Shadow? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val view = LocalView.current

    val textModifier = modifier.pointerInput(spannedText) {
        detectTapGestures(
            onTap = { pos ->
                val offset = layoutResult?.getOffsetForPosition(pos) ?: return@detectTapGestures
                spannedText.clickableSpans
                    .firstOrNull { offset in it.start until it.end }
                    ?.span?.onClick(view)
            },
            onLongPress = { pos ->
                val offset = layoutResult?.getOffsetForPosition(pos) ?: return@detectTapGestures
                val span = spannedText.clickableSpans
                    .firstOrNull { offset in it.start until it.end }
                    ?.span
                if (span is LongClickableSpan) span.onLongClick(view)
            },
            onDoubleTap = onDoubleTap?.let { { _: androidx.compose.ui.geometry.Offset -> it() } },
        )
    }

    val content = @Composable {
        Text(
            text = spannedText.annotatedString,
            maxLines = maxLines,
            style = TextStyle(shadow = shadow),
            onTextLayout = { layoutResult = it },
            modifier = textModifier,
        )
    }

    if (selectable) {
        SelectionContainer { content() }
    } else {
        content()
    }
}
