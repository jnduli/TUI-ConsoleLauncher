package ohi.andre.consolelauncher.managers.xml.options

import android.os.Build
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
enum class Ui(
    private val default: String,
    private val infoText: String = "",
    private val typeValue: String = XMLPrefsSave.BOOLEAN,
) : XMLPrefsSave {
    show_enter_button("true", "Hide/show the enter button"),
    system_font("false", "If false, the default t-ui font (\"Lucida Console\") will be used for all texts"),
    ram_size("13", "The ram label font size", XMLPrefsSave.INTEGER),
    battery_size("13", "The battery label font size", XMLPrefsSave.INTEGER),
    device_size("13", "The device label font size", XMLPrefsSave.INTEGER),
    time_size("13", "The time label font size", XMLPrefsSave.INTEGER),
    storage_size("13", "The storage label font size", XMLPrefsSave.INTEGER),
    network_size("13", "The network label font size", XMLPrefsSave.INTEGER),
    input_output_size("15", "The input/output font size", XMLPrefsSave.INTEGER),
    input_bottom("true", "If false, the input field will be placed on top of the screen"),
    show_device_name("true", "If false, the device label will be hidden"),
    username("user", "Your username", XMLPrefsSave.TEXT),
    deviceName(Build.DEVICE, "Your device name", XMLPrefsSave.TEXT),
    system_wallpaper("false", "If true, your system wallpaper will be used as background"),
    fullscreen("false", "If true, t-ui will run in fullscreen mode"),
    input_prefix("$", "The prefix placed before every input", XMLPrefsSave.TEXT),
    input_root_prefix("#", "The prefix placed before a root command (\"su ...\")", XMLPrefsSave.TEXT),
    display_margin_mm("0,0,0,0", "[left margin],[top margin],[right margin],[bottom margin]", XMLPrefsSave.TEXT),
    ignore_bar_color("false", "If true, statusbar_color and navigationbar_color will be ignored"),
    show_app_installed("true", "If true, you will receive a message when you install an app"),
    show_app_uninstalled("true", "If true, you will receive a message when you uninstall an app"),
    show_session_info(
        "true",
        "If true, when your input field is empty there will be a short line containing some information about the current session",
    ),
    show_restart_message("true", "If false, the restart message won't be shown"),
    show_weather("true", "If true, you will see a label containing the weather in your area"),
    weather_size("13", "Weather size", XMLPrefsSave.INTEGER),
    unlock_size("13", "Unlock size", XMLPrefsSave.INTEGER),
    statusbar_light_icons("true", "If true, your status bar icons will be white. Dark otherwise"),
    bgrect_params("2,0", "[Stroke width],[Rect corner radius]", XMLPrefsSave.TEXT),
    shadow_params("2,2,0.2", "[Shadow X offset],[Shadow Y offset],[Shadow radius]", XMLPrefsSave.TEXT),
    output_field_margins(
        "3,3,0,0",
        "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]",
        XMLPrefsSave.TEXT,
    ),
    input_area_margins(
        "3,3,0,0",
        "The dimension of the input area (prefix + input field + toolbar + suggestions). [horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]",
        XMLPrefsSave.TEXT,
    ),
    toolbar_margins(
        "3,3,0,0",
        "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]",
        XMLPrefsSave.TEXT,
    ),
    suggestions_area_margin(
        "3,3,0,0",
        "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]",
        XMLPrefsSave.TEXT,
    ),
    ;

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.UI

    override fun label(): String = name

    override fun type(): String = typeValue

    override fun defaultValue(): String = default

    override fun info(): String = infoText

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
