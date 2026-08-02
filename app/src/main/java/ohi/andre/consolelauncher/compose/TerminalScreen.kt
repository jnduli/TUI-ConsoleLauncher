package ohi.andre.consolelauncher.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TerminalUiEvent
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils

import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.ViewTuils

/**
 * The terminal core: output history, input row (prefix + text field + submit) and toolbar
 * (back/next/delete/paste), in either order depending on [inputAtBottom] - replaces
 * input_up_layout.xml/input_down_layout.xml, which mirrored each other except for element order.
 */
@Composable
fun TerminalScreen(
    terminalManager: TerminalManager,
    suggestionsManager: SuggestionsManager?,
    inputAtBottom: Boolean,
    showToolbar: Boolean,
    hideToolbarNoInput: Boolean,
    showSubmitButton: Boolean,
    onDoubleTapOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ioSize = remember { XMLPrefsManager.getInt(Ui.input_output_size) }
    val typeface = remember { ViewTuils.getTypeface(context)?.let { FontFamily(it) } ?: FontFamily.Default }

    val output by terminalManager.output.collectAsStateWithLifecycle()
    val inputValue by terminalManager.inputValue.collectAsStateWithLifecycle()
    val prefix by terminalManager.prefix.collectAsStateWithLifecycle()
    val hint by terminalManager.hint.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(terminalManager) {
        terminalManager.events.collect { event ->
            when (event) {
                TerminalUiEvent.RequestFocus -> focusRequester.requestFocus()
                TerminalUiEvent.ShowKeyboard -> keyboardController?.show()
                TerminalUiEvent.HideKeyboard -> keyboardController?.hide()
                TerminalUiEvent.ScrollToEnd -> scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    val outputTheme = remember { buildTerminalTheme(ThemeSlot.OUTPUT) }
    val outputSpanned = remember(output) { output.toSpannedText() }

    val outputBox: @Composable ColumnScope.() -> Unit = {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTapOutput() }) },
            contentAlignment = Alignment.BottomStart,
        ) {
            ClickableAnnotatedText(
                spannedText = outputSpanned,
                shadow = outputTheme.textShadow,
                modifier = Modifier.fillMaxWidth().themed(outputTheme),
            )
        }
    }

    val inputRow: @Composable () -> Unit = {
        InputRow(
            terminalManager = terminalManager,
            suggestionsManager = suggestionsManager,
            prefix = prefix,
            hint = hint,
            inputValue = inputValue,
            ioSize = ioSize,
            typeface = typeface,
            showSubmitButton = showSubmitButton,
            focusRequester = focusRequester,
        )
    }

    val toolbarRow: @Composable () -> Unit = {
        val toolbarVisible = showToolbar && (!hideToolbarNoInput || inputValue.text.isNotEmpty())
        if (toolbarVisible) Toolbar(terminalManager)
    }

    val suggestionsRow: @Composable () -> Unit = {
        if (suggestionsManager != null) {
            SuggestionsRow(
                manager = suggestionsManager,
                modifier = Modifier.themed(buildTerminalTheme(ThemeSlot.SUGGESTIONS)),
            )
        }
    }

    Column(modifier = modifier) {
        if (inputAtBottom) {
            outputBox()
            inputRow()
            toolbarRow()
            suggestionsRow()
        } else {
            suggestionsRow()
            inputRow()
            toolbarRow()
            outputBox()
        }
    }
}

@Composable
private fun InputRow(
    terminalManager: TerminalManager,
    suggestionsManager: SuggestionsManager?,
    prefix: String,
    hint: String,
    inputValue: TextFieldValue,
    ioSize: Int,
    typeface: FontFamily,
    showSubmitButton: Boolean,
    focusRequester: FocusRequester,
) {
    val inputTheme = remember { buildTerminalTheme(ThemeSlot.INPUT) }
    val inputColor = remember { Color(XMLPrefsManager.getColor(Theme.input_color)) }
    val hintColor = remember { Color(XMLPrefsManager.getColor(Theme.session_info_color)) }
    val cursorColor = remember { Color(XMLPrefsManager.getColor(Theme.cursor_color)) }
    val enterColor = remember { Color(XMLPrefsManager.getColor(Theme.enter_color)) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().themed(inputTheme),
    ) {
        Text(
            text = prefix,
            color = inputColor,
            fontSize = ioSize.sp,
            fontFamily = typeface,
            style = TextStyle(shadow = inputTheme.textShadow),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (inputValue.text.isEmpty()) {
                Text(text = hint, color = hintColor, fontSize = ioSize.sp, fontFamily = typeface)
            }
            BasicTextField(
                value = inputValue,
                onValueChange = {
                    terminalManager.onInputEdited(it)
                    suggestionsManager?.requestSuggestion(it.text)
                },
                singleLine = true,
                textStyle = TextStyle(color = inputColor, fontSize = ioSize.sp, fontFamily = typeface),
                cursorBrush = SolidColor(cursorColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { terminalManager.onNewInput() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent {
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                            terminalManager.onNewInput()
                            true
                        } else {
                            false
                        }
                    },
            )
        }

        if (showSubmitButton) {
            Image(
                painter = painterResource(R.drawable.ic_enter),
                contentDescription = null,
                colorFilter = ColorFilter.tint(enterColor),
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { terminalManager.onNewInput() }) },
            )
        }
    }
}

@Composable
private fun Toolbar(terminalManager: TerminalManager) {
    val toolbarTheme = remember { buildTerminalTheme(ThemeSlot.TOOLBAR) }
    val toolbarColor = remember { Color(XMLPrefsManager.getColor(Theme.toolbar_color)) }
    val context = LocalContext.current

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.tools_height))
            .themed(toolbarTheme),
    ) {
        ToolbarIcon(R.drawable.ic_clear, toolbarColor) { terminalManager.input = Tuils.EMPTYSTRING }
        ToolbarIcon(R.drawable.ic_back, toolbarColor) { terminalManager.onBackPressed() }
        ToolbarIcon(R.drawable.ic_next, toolbarColor) { terminalManager.onNextPressed() }
        ToolbarIcon(R.drawable.ic_paste, toolbarColor) {
            val text = SystemUtils.getTextFromClipboard(context)
            if (!text.isNullOrEmpty()) terminalManager.input = terminalManager.input + text
        }
    }
}

@Composable
private fun RowScope.ToolbarIcon(resId: Int, tint: Color, onClick: () -> Unit) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .padding(4.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    )
}
