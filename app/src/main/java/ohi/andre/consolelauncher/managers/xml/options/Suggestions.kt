package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * Created by francescoandreuzzi on 24/09/2017.
 *
 * Unlike [Theme] this enum mixes value kinds (booleans, integers, free text, and the per-category
 * suggestion colors), so each entry declares its own [kind] instead of sharing one [type].
 */
enum class Suggestions(
    private val default: String,
    private val kind: String,
    private val infoText: String = "",
) : XMLPrefsSave {
    show_suggestions("true", XMLPrefsSave.BOOLEAN, "If false, suggestions won't be shown"),
    default_text_color("black", XMLPrefsSave.PALETTE_REF, "The default text color"),
    default_bg_color("white", XMLPrefsSave.PALETTE_REF, "The default background color"),
    apps_text_color("black", XMLPrefsSave.PALETTE_REF, "Apps suggestions text color"),
    apps_bg_color("cyan", XMLPrefsSave.PALETTE_REF, "Apps suggestions background color"),
    alias_text_color("black", XMLPrefsSave.PALETTE_REF, "Aliases suggestions text color"),
    alias_bg_color("red", XMLPrefsSave.PALETTE_REF, "Aliases suggestions background color"),
    cmd_text_color("black", XMLPrefsSave.PALETTE_REF, "Commands suggestions text color"),
    cmd_bg_color("bright_green", XMLPrefsSave.PALETTE_REF, "Commands suggestions background color"),
    contact_text_color("black", XMLPrefsSave.PALETTE_REF, "Contacts suggestions text color"),
    contact_bg_color("bright_cyan", XMLPrefsSave.PALETTE_REF, "Contacts suggestions background color"),
    file_text_color("black", XMLPrefsSave.PALETTE_REF, "Files suggestions text color"),
    file_bg_color("blue", XMLPrefsSave.PALETTE_REF, "Files suggestions background color"),
    suggest_alias_default(
        "true",
        XMLPrefsSave.BOOLEAN,
        "If true, your alias will be shown when the input field is empty",
    ),
    suggest_appgp_default(
        "true",
        XMLPrefsSave.BOOLEAN,
        "If true, your app groups will be shown when the input field is empty",
    ),
    click_to_launch(
        "true",
        XMLPrefsSave.BOOLEAN,
        "If true, some suggestions will be executed as soon as you click them",
    ),
    suggestions_size("12", XMLPrefsSave.INTEGER, "The text size of the suggestions"),
    double_space_click_first_suggestion(
        "true",
        XMLPrefsSave.BOOLEAN,
        "If true, t-ui will simulate a click on the current first suggestion if you double-click the space bar",
    ),
    noinput_suggestions_order(
        "0(5)1(5)2(2)3(5)",
        XMLPrefsSave.TEXT,
        "The order and the number of suggestions that appears on-screen when the input field is empty. " +
            "0=apps, 1=alias, 2=cmds, 3=app groups. Put between round brackets the maximum number of " +
            "suggestions of the leading type",
    ),
    suggestions_order(
        "2(2)0(5)1(5)3(3)",
        XMLPrefsSave.TEXT,
        "The order and the number of suggestions that appears on-screen. 0=apps, 1=alias, 2=cmds, " +
            "3=app groups. Put between round brackets the maximum number of suggestions of the leading type",
    ),
    noinput_min_command_priority(
        "5",
        XMLPrefsSave.INTEGER,
        "The minimum priority of a command shown when the input field is empty",
    ),
    suggestions_per_category(
        "5",
        XMLPrefsSave.INTEGER,
        "The number of suggestions shown per category. This doesn't affect \"noinput_suggestions_order\" " +
            "and \"suggestions_order\"",
    ),
    hide_suggestions_when_empty(
        "always",
        XMLPrefsSave.TEXT,
        "If \"always\" the suggestion area will be hidden when there are no suggestions. If \"true\" it " +
            "will be hidden only if also the input area is empty. \"false\" disables the feature",
    ),
    suggestions_spaces(
        "15,15,25,20",
        XMLPrefsSave.TEXT,
        "[External horizontal margin],[E. vertical margin],[Internal horizontal margin],[I. vertical margin]",
    ),
    ;

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS

    override fun label(): String = name

    override fun type(): String = kind

    override fun defaultValue(): String = default

    override fun info(): String = infoText

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
