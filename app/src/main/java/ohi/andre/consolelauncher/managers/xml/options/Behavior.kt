package ohi.andre.consolelauncher.managers.xml.options

import android.os.Environment
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
enum class Behavior(
    private val default: String,
    private val infoText: String = "",
    private val typeValue: String = XMLPrefsSave.BOOLEAN,
) : XMLPrefsSave {
    double_tap_lock("true", "If true, t-ui will lock the screen on double tap"),
    double_tap_cmd("", "The command that will run when you touch two times the screen quickly", XMLPrefsSave.TEXT),
    tui_notification(
        "false",
        "If true, there will always be a notification in your status bar, telling you that t-ui is running",
    ),
    auto_show_keyboard("true", "If true, your keyboard will be shown everytime you go back to t-ui"),
    auto_scroll(
        "true",
        "If true, the terminal will be automatically scrolled down when the keyboard is open",
    ),
    show_hints("true", "If true, t-ui will tell you some useful hints sometime"),
    show_alias_content(
        "false",
        "If true, when you use an alias you'll also be able to know what command has been executed",
    ),
    clear_after_cmds(
        "-1",
        "Auto-clear after n commands (if -1, this feature will be disabled)",
        XMLPrefsSave.INTEGER,
    ),
    clear_after_seconds(
        "-1",
        "Auto-clear after n seconds (if -1, this feature will be disabled)",
        XMLPrefsSave.INTEGER,
    ),
    max_lines(
        "-1",
        "Set maximum number of lines that will be shown in the terminal (if -1, this feature is be disabled)",
        XMLPrefsSave.INTEGER,
    ),
    time_format(
        "d MMM yyyy HH:mm:ss@HH:mm:ss",
        "Define the time format (see also Time Format)",
        XMLPrefsSave.TEXT,
    ),
    time_format_separator(
        "@",
        "This is the separator between your different time formats",
        XMLPrefsSave.TEXT,
    ),
    battery_medium(
        "50",
        "The percentage below which the battery level will be considered \"medium\"",
        XMLPrefsSave.INTEGER,
    ),
    battery_low(
        "15",
        "The percentage below which the battery level will be considered \"low\"",
        XMLPrefsSave.INTEGER,
    ),
    input_format("[%t] %p %i", "Define the input format ", XMLPrefsSave.TEXT),
    output_format("%o", "Define the output format ", XMLPrefsSave.TEXT),
    session_info_format("%u@%d:%p", "Define the session info format", XMLPrefsSave.TEXT),
    alias_param_marker(
        "%",
        "Define the marker that will be replaced with a provided param",
        XMLPrefsSave.TEXT,
    ),
    alias_param_separator(",", "Define the separator between a group of params", XMLPrefsSave.TEXT),
    alias_replace_all_markers(
        "true",
        "If true, if you pass a lower number of parameters to an alias, t-ui will use the first one to replace the others",
    ),
    alias_content_format(
        "%a --> [%v]",
        "Define the format used to show your alias contents ",
        XMLPrefsSave.TEXT,
    ),
    home_path(
        Environment.getExternalStorageDirectory().absolutePath,
        "The path to your home directory",
        XMLPrefsSave.TEXT,
    ),
    app_installed_format(
        "App installed: %p",
        "The format of the \"app installed\" message ",
        XMLPrefsSave.TEXT,
    ),
    app_uninstalled_format(
        "App uninstalled: %p",
        "The format of the \"app uninstalled\" message ",
        XMLPrefsSave.TEXT,
    ),
    max_optional_depth(
        "2",
        "A value which is used to tell how deep t-ui can go in a nested optional value",
        XMLPrefsSave.INTEGER,
    ),
    tui_notification_title("T-UI", "The title of the T-UI notification", XMLPrefsSave.TEXT),
    tui_notification_subtitle("T-UI is running", "The subtitle of the T-UI notification", XMLPrefsSave.TEXT),
    tui_notification_click_cmd(
        "",
        "The command ran when the T-UI notification is clicked",
        XMLPrefsSave.TEXT,
    ),
    tui_notification_click_showhome(
        "true",
        "If false, the click on the T-UI notification won't bring you to your phone home",
    ),
    tui_notification_lastcmds_size(
        "5",
        "The number of used commands that will appear inside the T-UI notification (<0 will disable the feature)",
        XMLPrefsSave.INTEGER,
    ),
    tui_notification_lastcmds_updown("true", "If true, the last used command will appear on top"),
    tui_notification_priority(
        "0",
        "The priority of the T-UI notification (min: -2, max: 2)",
        XMLPrefsSave.INTEGER,
    ),
    long_click_vibration_duration(
        "100",
        "The duration (in milliseconds) of the vibration when you long click a notification or an RSS item (<0 will disable the feature)",
        XMLPrefsSave.INTEGER,
    ),
    click_commands("true", "If true, you will be able to use a command again clicking on it"),
    long_click_commands(
        "true",
        "If true, you will be able to put a used command in the input field long-clicking it",
    ),
    append_quote_before_file(
        "false",
        "If true, t-ui will automatically append a quote before a file inserted clicking on a suggestion",
    ),
    optional_values_separator(
        "/",
        "The separator between two optional values (doesn't affect notification optional values)",
        XMLPrefsSave.TEXT,
    ),
    orientation("2", "0 = landscape, 1 = portrait, 2 = auto-rotate", XMLPrefsSave.INTEGER),
    htmlextractor_default_format(
        "%t -> %v%n%a(%an = %av)(%n)",
        "The default format used by htmlextract -use",
        XMLPrefsSave.TEXT,
    ),
    tui_notification_time_color(
        Theme.time_color.defaultValue(),
        "The time color inside the t-ui notification",
        XMLPrefsSave.PALETTE_REF,
    ),
    tui_notification_input_color(
        Theme.input_color.defaultValue(),
        "The input color inside the t-ui notification",
        XMLPrefsSave.PALETTE_REF,
    ),
    weather_key(
        "1f798f99228596c20ccfda51b9771a86",
        "The key of your account on OpenWeatherMap. You can keep the default one, or create your custom key (check the wiki)",
        XMLPrefsSave.TEXT,
    ),
    weather_temperature_measure(
        "metric",
        "metric = Celsius; imperial = Fahrenheit; standard = Kelvin",
        XMLPrefsSave.TEXT,
    ),
    weather_format("Weather: %main, Temp: %temp", "The format used to show the weather", XMLPrefsSave.TEXT),
    unlock_counter_cycle_start(
        "6.00",
        "The starting hour of the unlock counter cycle (hh.mm)",
        XMLPrefsSave.INTEGER,
    ),
    not_available_text("n/a", "The text shown when a value is not available", XMLPrefsSave.TEXT),
    back_button_enabled(
        "true",
        "If true, the back button will put the previous command inside the input area",
    ),
    location_update_mintime(
        "20",
        "The amount of time between two location updates (in minutes, must be an integer value)",
    ),
    location_update_mindistance(
        "500",
        "The minimum distance (in meters) to get a location update",
        XMLPrefsSave.INTEGER,
    ),
    ;

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.BEHAVIOR

    override fun label(): String = name

    override fun type(): String = typeValue

    override fun defaultValue(): String = default

    override fun info(): String = infoText

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
