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
    black("#ff000000"),
    red("#ffcc0000"),
    green("#ff4e9a06"),
    yellow("#ffc4a000"),
    blue("#ff3465a4"),
    magenta("#ff75507b"),
    cyan("#ff06989a"),
    white("#ffd3d7cf"),
    bright_black("#ff555753"),
    bright_red("#ffef2929"),
    bright_green("#ff8ae234"),
    bright_yellow("#fffce94f"),
    bright_blue("#ff729fcf"),
    bright_magenta("#ffad7fa8"),
    bright_cyan("#ff34e2e2"),
    bright_white("#ffeeeeec"),
    foreground("#ffffffff", "The default foreground (text) color"),
    background("#ff000000", "The default background color"),
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
