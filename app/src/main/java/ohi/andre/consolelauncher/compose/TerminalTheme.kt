package ohi.andre.consolelauncher.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import ohi.andre.consolelauncher.UIManager.Companion.getListOfStringValues
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

/** Wraps XMLPrefsManager.getColor(): treats its "unparsable" sentinel (Integer.MAX_VALUE) and the
 * app-wide "#00xxxxxx = disabled" convention (checked via the parsed alpha channel, not string
 * sniffing) as a single "no color configured" result. */
private fun colorOrNull(pref: XMLPrefsSave): Color? {
    val argb = XMLPrefsManager.getColor(pref)
    if (argb == Int.MAX_VALUE) return null
    if (android.graphics.Color.alpha(argb) == 0) return null
    return Color(argb)
}

/** [Ui.shadow_params]: "[x offset],[y offset],[radius]", shared by every slot. */
private data class ShadowParams(val offsetX: Int, val offsetY: Int, val radius: Float) {
    companion object {
        fun read(): ShadowParams {
            val parts = getListOfStringValues(XMLPrefsManager.get(Ui.shadow_params), 3, "0")
            return ShadowParams(
                offsetX = parts[0]?.toIntOrNull() ?: 0,
                offsetY = parts[1]?.toIntOrNull() ?: 0,
                radius = parts[2]?.toFloatOrNull() ?: 0f,
            )
        }
    }
}

/** [Ui.bgrect_params]: "[stroke width],[corner radius]", shared by every slot. */
private data class RectParams(val strokeWidthPx: Int, val cornerRadiusPx: Int) {
    companion object {
        fun read(): RectParams {
            val parts = getListOfStringValues(XMLPrefsManager.get(Ui.bgrect_params), 2, "0")
            return RectParams(
                strokeWidthPx = parts[0]?.toIntOrNull() ?: 0,
                cornerRadiusPx = parts[1]?.toIntOrNull() ?: 0,
            )
        }
    }
}

/** Any of the per-slot margins prefs (e.g. [Ui.output_field_margins]):
 * "[external horizontal],[external vertical],[internal horizontal],[internal vertical]" (pixels). */
data class BoxMargins(val externalH: Int, val externalV: Int, val internalH: Int, val internalV: Int) {
    companion object {
        fun read(pref: XMLPrefsSave): BoxMargins {
            val parts = getListOfStringValues(XMLPrefsManager.get(pref), 4, "0")
            return BoxMargins(
                externalH = parts[0]?.toIntOrNull() ?: 0,
                externalV = parts[1]?.toIntOrNull() ?: 0,
                internalH = parts[2]?.toIntOrNull() ?: 0,
                internalV = parts[3]?.toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * One named box/shadow slot, each pointing directly at the prefs that describe it - no shared
 * index scheme. Mirrors what UIManager reads for the same UI regions (see its constructor), but
 * each slot names its own keys instead of assembling parallel arrays and indexing into them.
 */
enum class ThemeSlot(
    val bgColorPref: XMLPrefsSave,
    val bgRectColorPref: XMLPrefsSave,
    val marginsPref: XMLPrefsSave,
    // Only the input/output text views ever got a text shadow in the legacy View code (it's a
    // TextView-only effect); suggestions/toolbar are plain ViewGroups and never had one.
    val shadowColorPref: XMLPrefsSave? = null,
) {
    OUTPUT(Theme.output_bg, Theme.output_bgrectcolor, Ui.output_field_margins, Theme.output_shadow_color),
    INPUT(Theme.input_bg, Theme.input_bgrectcolor, Ui.input_area_margins, Theme.input_shadow_color),
    SUGGESTIONS(Theme.suggestions_bg, Theme.suggestions_bgrectcolor, Ui.suggestions_area_margin),
    TOOLBAR(Theme.toolbar_bg, Theme.toolbar_bgrectcolor, Ui.toolbar_margins),
}

/** All pixel fields are raw pixels (as read from XML prefs and historically fed straight into
 * View.setPadding/GradientDrawable), not dp - see [themed] for the px->dp conversion. */
data class TerminalTheme(
    val bgColor: Color,
    val strokeColor: Color?,
    val strokeWidthPx: Int,
    val cornerRadiusPx: Int,
    val margins: BoxMargins,
    val textShadow: Shadow?,
)

fun buildTerminalTheme(slot: ThemeSlot): TerminalTheme {
    val rect = RectParams.read()
    val shadow = slot.shadowColorPref?.let { pref ->
        colorOrNull(pref)?.let { color ->
            val params = ShadowParams.read()
            Shadow(color, Offset(params.offsetX.toFloat(), params.offsetY.toFloat()), params.radius)
        }
    }

    return TerminalTheme(
        bgColor = colorOrNull(slot.bgColorPref) ?: Color.Transparent,
        strokeColor = colorOrNull(slot.bgRectColorPref),
        strokeWidthPx = rect.strokeWidthPx,
        cornerRadiusPx = rect.cornerRadiusPx,
        margins = BoxMargins.read(slot.marginsPref),
        textShadow = shadow,
    )
}

/** Applies [theme]'s background/stroke/margins/padding to a composable, in that order (external
 * margin, then background+stroke, then internal padding) - matching applyBgRect/applyMargins. */
@Composable
fun Modifier.themed(theme: TerminalTheme): Modifier {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(with(density) { theme.cornerRadiusPx.toDp() })
    var modifier = with(density) {
        this@themed.padding(
            horizontal = theme.margins.externalH.toDp(),
            vertical = theme.margins.externalV.toDp(),
        )
    }
    modifier = modifier.background(theme.bgColor, shape)
    if (theme.strokeColor != null) {
        modifier = modifier.border(with(density) { theme.strokeWidthPx.toDp() }, theme.strokeColor, shape)
    }
    return with(density) {
        modifier.padding(
            horizontal = theme.margins.internalH.toDp(),
            vertical = theme.margins.internalV.toDp(),
        )
    }
}
