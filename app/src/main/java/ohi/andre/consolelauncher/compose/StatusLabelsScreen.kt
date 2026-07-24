package ohi.andre.consolelauncher.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun StatusLabelsScreen(
    time: State<CharSequence>,
    memory: State<CharSequence>,
    battery: State<CharSequence>,
    storage: State<CharSequence>,
    network: State<CharSequence>,
    deviceText: String?,
    deviceColor: Color,
    weather: State<CharSequence>,
    unlockTime: State<CharSequence>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = time.value.toAnnotatedString())
        Text(text = memory.value.toAnnotatedString())
        Text(text = battery.value.toAnnotatedString())
        Text(text = storage.value.toAnnotatedString())
        Text(text = network.value.toAnnotatedString())
        if (!deviceText.isNullOrEmpty()) {
            val theme = remember { buildTerminalTheme(ThemeSlot.OUTPUT) }
            Text(
                text = deviceText,
                color = deviceColor,
                style = TextStyle(shadow = theme.textShadow),
                modifier = Modifier.themed(theme),
            )
        }
        Text(text = weather.value.toAnnotatedString())
        Text(text = unlockTime.value.toAnnotatedString())
    }
}
