package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * The 16 base terminal colors (ANSI black/red/green/yellow/blue/magenta/cyan/white + their bright
 * variants) plus the default foreground/background, mirroring how a real terminal color scheme
 * works. Every [XMLPrefsSave.PALETTE_REF] slot in [Theme] / [Suggestions] resolves to one of these
 * roles instead of storing its own hex value.
 */
enum class Palette(private val default: String, private val infoText: String = "") : XMLPrefsSave {
    black("#ff073642"),
    red("#ffdc322f"),
    green("#ff859900"),
    yellow("#ffb58900"),
    blue("#ff268bd2"),
    magenta("#ffd33682"),
    cyan("#ff2aa198"),
    white("#ffeee8d5"),
    bright_black("#ff002b36"),
    bright_red("#ffcb4b16"),
    bright_green("#ff586e75"),
    bright_yellow("#ff657b83"),
    bright_blue("#ff839496"),
    bright_magenta("#ff6c71c4"),
    bright_cyan("#ff93a1a1"),
    bright_white("#fffdf6e3"),
    foreground("#ff839496", "The default foreground (text) color"),
    background("#ff002b36", "The default background color"),
    ;

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.PALETTE

    override fun label(): String = name

    override fun type(): String = XMLPrefsSave.COLOR

    override fun defaultValue(): String = default

    override fun info(): String = infoText

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
