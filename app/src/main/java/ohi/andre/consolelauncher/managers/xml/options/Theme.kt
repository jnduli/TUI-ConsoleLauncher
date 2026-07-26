package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * Created by francescoandreuzzi on 24/09/2017.
 *
 * Every slot is a [XMLPrefsSave.PALETTE_REF]: [default] is either a bare [Palette] role name
 * (fully opaque, e.g. "green") or "role,opacity" (e.g. "background,0") for the translucent
 * Compose-era slots (card backgrounds/strokes/shadows, the wallpaper overlay) that need alpha
 * control a flat role can't express - see [XMLPrefsManager]'s palette-ref resolution.
 */
enum class Theme(
    private val default: String,
    private val infoText: String = "",
    private val invalid: Array<String>? = null,
) : XMLPrefsSave {
    input_color("green", "Input color"),
    output_color("foreground", "Output color"),
    bg_color("background", "Background color"),
    device_color("yellow", "Device label color"),
    battery_color_high("green", "Battery label color when the battery level is high"),
    battery_color_medium("yellow", "Battery label color when the battery level is medium"),
    battery_color_low("red", "Battery label color when the battery level is low"),
    time_color("cyan", "Time label color"),
    storage_color("magenta", "Storage label color"),
    ram_color("red", "RAM label color"),
    network_info_color("yellow"),
    toolbar_bg("background,0", "Toolbar background color"),
    toolbar_color("red", "Toolbar icons color"),
    enter_color("foreground", "Enter icon color"),
    cursor_color("foreground"),
    overlay_color(
        "background,50",
        "The overlay that overlaps to the background (only when system_wallpaper is true)",
    ),
    alias_content_color("bright_cyan", "Alias content color"),
    statusbar_color("background", "Status Bar color (5.0+)"),
    navigationbar_color("background", "Navigation Bar color (5.0+)"),
    app_installed_color("red", "App installed message color"),
    app_uninstalled_color("red", "App uninstalled message color"),
    hint_color("green", "Hint color"),
    mark_color("bright_yellow", "The background color that will be used as marker"),
    link_color("blue", "The color of the links"),
    restart_message_color(output_color.defaultValue(), "The color of the restart message"),
    weather_color(ram_color.defaultValue(), "The color of the weather label"),
    unlock_counter_color(device_color.defaultValue(), "The color of the unlock counter"),
    session_info_color("bright_black", "The color of the session info"),
    input_bgrectcolor(
        "background,0",
        "The color of the rect behind the input field",
        invalid = arrayOf("background,100"),
    ),
    output_bgrectcolor(
        "background,0",
        "The color of the rect behind the output field",
        invalid = arrayOf("background,100"),
    ),
    toolbar_bgrectcolor(
        "background,0",
        "The color of the rect behind the toolbar",
        invalid = arrayOf("background,100"),
    ),
    suggestions_bgrectcolor(
        "background,0",
        "The color of the rect behind the suggestions area",
        invalid = arrayOf("background,100"),
    ),
    input_bg(
        "background,0",
        "The background color of the input field",
        invalid = arrayOf("background,100"),
    ),
    output_bg(
        "background,0",
        "The background color of the output field",
        invalid = arrayOf("background,100"),
    ),
    suggestions_bg(
        "background,0",
        "The background color of the suggestions area",
        invalid = arrayOf("background,100"),
    ),
    input_shadow_color(
        "background,0",
        "The outline color of the input field",
        invalid = arrayOf("background,100"),
    ),
    output_shadow_color(
        "background,0",
        "The outline color of the output field",
        invalid = arrayOf("background,100"),
    ),
    ;

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.THEME

    override fun label(): String = name

    override fun type(): String = XMLPrefsSave.PALETTE_REF

    override fun defaultValue(): String = default

    override fun info(): String = infoText

    override fun invalidValues(): Array<String>? = invalid

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
