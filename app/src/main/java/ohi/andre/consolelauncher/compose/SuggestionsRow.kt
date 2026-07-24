package ohi.andre.consolelauncher.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ohi.andre.consolelauncher.managers.ContactManager.Contact
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion
import ohi.andre.consolelauncher.tuils.Tuils

@Composable
fun SuggestionsRow(manager: SuggestionsManager, modifier: Modifier = Modifier) {
    val suggestions by manager.suggestions.collectAsStateWithLifecycle()
    val visible by manager.visible.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    if (!visible) return

    // Mirrors the legacy scrollView.fullScroll(FOCUS_LEFT) after every refresh: snap back to the
    // start of the row rather than preserving whatever scroll offset was left from before.
    LaunchedEffect(suggestions) {
        if (suggestions.isNotEmpty()) listState.scrollToItem(0)
    }

    LazyRow(state = listState, modifier = modifier) {
        items(suggestions) { suggestion ->
            SuggestionChip(manager, suggestion)
        }
    }
}

@Composable
private fun SuggestionChip(manager: SuggestionsManager, suggestion: Suggestion) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val spacing = manager.chipSpacing
    val colors = remember(suggestion) { manager.colorsFor(suggestion) }
    var showContactMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.padding(
            horizontal = with(density) { spacing.marginH.toDp() },
            vertical = with(density) { spacing.marginV.toDp() },
        ),
    ) {
        Text(
            text = suggestion.text.orEmpty(),
            color = Color(colors.foreground),
            fontSize = manager.chipTextSizeSp.sp,
            maxLines = 1,
            modifier = Modifier
                .background(Color(colors.background))
                .padding(
                    horizontal = with(density) { spacing.paddingH.toDp() },
                    vertical = with(density) { spacing.paddingV.toDp() },
                )
                .pointerInput(suggestion) {
                    detectTapGestures(
                        onTap = { manager.clickSuggestion(suggestion) },
                        onLongPress = {
                            if (suggestion.type == Suggestion.TYPE_CONTACT) showContactMenu = true
                        },
                    )
                },
        )

        if (suggestion.type == Suggestion.TYPE_CONTACT) {
            val contact = suggestion.`object` as Contact
            DropdownMenu(expanded = showContactMenu, onDismissRequest = { showContactMenu = false }) {
                contact.numbers.forEachIndexed { index, number ->
                    DropdownMenuItem(
                        text = { Text(number) },
                        onClick = {
                            contact.setSelectedNumber(index)
                            Tuils.sendInput(context, suggestion.getText())
                            showContactMenu = false
                        },
                    )
                }
            }
        }
    }
}
