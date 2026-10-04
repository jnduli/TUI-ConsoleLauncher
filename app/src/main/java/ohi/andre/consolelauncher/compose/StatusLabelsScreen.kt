package ohi.andre.consolelauncher.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.ViewTuils

/**
 * The status rows at the top of the home screen. Long pressing a row opens a picker to change what
 * it shows, add a row below it, move it or remove it. Empty rows also open it on a
 * plain tap, since their hint invites one.
 *
 * [sources] maps each live row type to its flow; flows are only collected for rows that are on
 * screen, so e.g. removing the weather row also stops location updates.
 */
@Composable
fun StatusLabelsScreen(
    rows: List<StatusRowType>,
    onRowsChange: (List<StatusRowType>) -> Unit,
    sources: Map<StatusRowType, StateFlow<CharSequence>>,
    hintColor: Color,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current

    fun Modifier.editOnPress(index: Int, onTapToo: Boolean = false) = pointerInput(index, onTapToo) {
        detectTapGestures(
            onTap = { if (onTapToo) editing = index },
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                editing = index
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, type ->
            val modifier = Modifier.fillMaxWidth().editOnPress(index, onTapToo = type == StatusRowType.EMPTY)
            StatusRowText(type, sources, hintColor, modifier)
        }
        if (rows.isEmpty()) {
            Text(
                text = "(long press to add a row)",
                color = hintColor.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().editOnPress(0),
            )
        }
    }

    editing?.let { index ->
        StatusRowPicker(
            rows = rows,
            index = index,
            onRowsChange = onRowsChange,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun StatusRowText(
    type: StatusRowType,
    sources: Map<StatusRowType, StateFlow<CharSequence>>,
    hintColor: Color,
    modifier: Modifier,
) {
    when (type) {
        StatusRowType.EMPTY -> Text(text = "(tap to choose)", color = hintColor.copy(alpha = 0.4f), modifier = modifier)
        StatusRowType.DEVICE -> {
            val theme = remember { buildTerminalTheme(ThemeSlot.OUTPUT) }
            val text by sources.getValue(type).collectAsStateWithLifecycle()
            Text(
                text = text.toAnnotatedString(),
                style = TextStyle(shadow = theme.textShadow),
                modifier = modifier.themed(theme),
            )
        }
        else -> {
            val text by sources.getValue(type).collectAsStateWithLifecycle()
            Text(text = text.toAnnotatedString(), modifier = modifier)
        }
    }
}

@Composable
private fun StatusRowPicker(
    rows: List<StatusRowType>,
    index: Int,
    onRowsChange: (List<StatusRowType>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val typeface = remember { ViewTuils.getTypeface(context)?.let { FontFamily(it) } ?: FontFamily.Default }
    val fg = remember { Color(XMLPrefsManager.getColor(Theme.output_color)) }
    val bg = remember { Color(XMLPrefsManager.getColor(Theme.bg_color)) }
    val accent = remember { Color(XMLPrefsManager.getColor(Theme.input_color)) }
    val style = TextStyle(color = fg, fontFamily = typeface, fontSize = 15.sp)

    val current = rows.getOrNull(index)
    // True when the chosen row goes in below the current one instead of replacing it.
    var addingBelow by rememberSaveable { mutableStateOf(current == null) }

    fun commit(type: StatusRowType) {
        val updated = rows.toMutableList()
        if (addingBelow || current == null) updated.add(minOf(index + 1, updated.size), type)
        else updated[index] = type
        onRowsChange(updated)
        onDismiss()
    }

    fun move(offset: Int) {
        val target = index + offset
        if (target !in rows.indices) return
        val updated = rows.toMutableList()
        updated.add(target, updated.removeAt(index))
        onRowsChange(updated)
        onDismiss()
    }

    @Composable
    fun Option(text: String, color: Color = fg, onClick: () -> Unit) {
        Text(
            text = text,
            style = style.copy(color = color),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(bg)
                .border(1.dp, fg)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val title = when {
                addingBelow || current == null -> "> new row"
                else -> "> row ${index + 1}: ${current.label}"
            }
            Text(text = title, style = style.copy(color = accent))
            Spacer(Modifier.padding(4.dp))

            Text(text = if (addingBelow) "show:" else "change to:", style = style.copy(color = fg.copy(alpha = 0.6f)))
            StatusRowType.entries.forEach { type ->
                val marker = if (!addingBelow && type == current) "* " else "  "
                Option(marker + type.label) { commit(type) }
            }

            if (current != null && !addingBelow) {
                Spacer(Modifier.padding(4.dp))
                Option("[+ add row below]", accent) { addingBelow = true }
                if (index > 0) Option("[move up]") { move(-1) }
                if (index < rows.lastIndex) Option("[move down]") { move(1) }
                Option("[remove row]", Color(0xFFE57373)) {
                    onRowsChange(rows.filterIndexed { i, _ -> i != index })
                    onDismiss()
                }
            }
        }
    }
}
