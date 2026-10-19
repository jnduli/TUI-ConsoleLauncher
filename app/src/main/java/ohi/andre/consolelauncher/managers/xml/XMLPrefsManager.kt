package ohi.andre.consolelauncher.managers.xml

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Environment
import ohi.andre.consolelauncher.managers.xml.classes.SharedPrefsXMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Cmd
import ohi.andre.consolelauncher.managers.xml.options.Palette
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LogTuils
import java.io.File

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
object XMLPrefsManager {

    @JvmField
    val VALUE_ATTRIBUTE = "value"

    enum class XMLPrefsRoot(en: Array<out XMLPrefsSave>) : XMLPrefsElement {
        THEME(Theme.values()) {
            override fun delete(): Array<String?> = arrayOf()
        },
        CMD(Cmd.values()) {
            override fun delete(): Array<String?> = arrayOf()
        },
        TOOLBAR(Toolbar.values()) {
            override fun delete(): Array<String?> = arrayOf()
        },
        UI(Ui.values()) {
            override fun delete(): Array<String?> = arrayOf()
        },
        BEHAVIOR(Behavior.values()) {
            override fun delete(): Array<String?> = arrayOf()
        },
        SUGGESTIONS(Suggestions.values()) {
            override fun delete(): Array<String?> = arrayOf(
                "app_suggestions_minrate",
                "contact_suggestions_minrate",
                "song_suggestions_minrate",
                "file_suggestions_minrate",
            )
        },
        PALETTE(Palette.values()) {
            override fun delete(): Array<String?> = arrayOf()
        },
        ;

        @JvmField
        val path: String = name.lowercase() + ".xml"

        private lateinit var values: XMLPrefsList

        @JvmField
        val enums: MutableList<XMLPrefsSave> = ArrayList(en.toList())

        fun bindSharedPrefsAndSeedDefaults(context: Context) {
            val prefs = context.getSharedPreferences(path, Context.MODE_PRIVATE)
            values = SharedPrefsXMLPrefsList(prefs)
            seedSharedPrefsDefaults(prefs, enums)
        }

        override fun write(c: Context, save: XMLPrefsSave, value: String) {
            values.add(save.label(), value)
        }

        override fun getValues(): XMLPrefsList = values

        override fun path(): String = path
    }

    @JvmStatic
    fun seedSharedPrefsDefaults(prefs: SharedPreferences, enums: List<XMLPrefsSave>) {
        val editor = prefs.edit()
        var dirty = false
        for (save in enums) {
            val current = prefs.getString(save.label(), null)
            if (current == null) {
                editor.putString(save.label(), save.defaultValue())
                dirty = true
            } else if (save.invalidValues()?.contains(current) == true) {
                editor.putString(save.label(), save.defaultValue())
                dirty = true
            }
        }
        if (dirty) editor.apply()
    }

    @JvmStatic
    fun dispose() {
        commonsLoaded = false
    }

    @JvmField
    var commonsLoaded = false

    @JvmStatic
    fun loadCommons(context: Context) {
        if (commonsLoaded) return
        commonsLoaded = true

        for (element in XMLPrefsRoot.values()) {
            element.bindSharedPrefsAndSeedDefaults(context)
        }
    }

    @Throws(Exception::class)
    private fun transform(s: String?, c: Class<*>): Any? {
        if (s == null) throw UnsupportedOperationException()

        if (c == Int::class.javaPrimitiveType) return s.toInt()
        if (c == Color::class.java) return Color.parseColor(s)
        if (c == Boolean::class.javaPrimitiveType) return s.toBoolean()
        if (c == String::class.java) return s
        if (c == Float::class.javaPrimitiveType) return s.toFloat()
        if (c == Double::class.javaPrimitiveType) return s.toDouble()
        if (c == File::class.java) {
            if (s.isEmpty()) return null
            val file = File(s)
            if (!file.exists()) throw UnsupportedOperationException()
            return file
        }

        @Suppress("UNCHECKED_CAST")
        return LogTuils.getDefaultValue(c as Class<Any>)
    }

    /** The stored value of [prefsSave], or its default when nothing has been stored yet. */
    private fun storedOrDefault(prefsSave: XMLPrefsSave): String? =
        prefsSave.parent().getValues().get(prefsSave)?.value ?: prefsSave.defaultValue()

    /** The configured home folder, or the device's external storage when that's unset or missing. */
    @JvmStatic
    fun homeDirectory(): File =
        getOrNull(File::class.java, Behavior.home_path) ?: Environment.getExternalStorageDirectory()

    @JvmStatic
    fun getInt(prefsSave: XMLPrefsSave): Int = get(Int::class.java, prefsSave)

    @JvmStatic
    fun getBoolean(prefsSave: XMLPrefsSave): Boolean = get(Boolean::class.java, prefsSave)

    @JvmStatic
    fun getColor(prefsSave: XMLPrefsSave): Int {
        if (prefsSave.parent() == null) return Integer.MAX_VALUE

        if (XMLPrefsSave.PALETTE_REF == prefsSave.type()) {
            return getPaletteRefColor(prefsSave)
        }

        try {
            return transform(storedOrDefault(prefsSave), Color::class.java) as Int
        } catch (e: Exception) {
            // unset or unparseable: fall back to the default, if it has one
            val def = prefsSave.defaultValue()
            if (def == null || def.isEmpty()) {
                return Integer.MAX_VALUE
            }

            return try {
                transform(def, Color::class.java) as Int
            } catch (e1: Exception) {
                Integer.MAX_VALUE
            }
        }
    }

    /**
     * Resolves a [XMLPrefsSave.PALETTE_REF] slot. Its stored/default value is either a
     * bare [Palette] role name (e.g. "green" - fully opaque), or "role,opacity" (e.g.
     * "background,0") for slots that need alpha control a flat palette role can't express, where
     * opacity is a 0-100 percentage overriding the role's alpha channel. Empty string means
     * "unset" (mirrors the pre-palette behaviour of empty-default color slots). If the role isn't
     * recognised - e.g. a pre-existing prefs file still has a raw hex saved under a key that used
     * to be type COLOR - fall back to parsing the whole stored string directly as hex, so old
     * installs keep their custom color instead of silently resetting.
     */
    private fun getPaletteRefColor(prefsSave: XMLPrefsSave): Int {
        val stored = storedOrDefault(prefsSave)

        if (stored == null || stored.isEmpty()) return Integer.MAX_VALUE

        var role = stored
        var opacityPercent: Int? = null
        val comma = stored.indexOf(',')
        if (comma >= 0) {
            role = stored.substring(0, comma).trim()
            opacityPercent = try {
                stored.substring(comma + 1).trim().toInt()
            } catch (e: Exception) {
                null
            }
        }

        return try {
            var base = getColor(Palette.valueOf(role.lowercase()))
            if (opacityPercent != null) {
                val clamped = opacityPercent.coerceIn(0, 100)
                val alpha = Math.round(clamped / 100f * 255f)
                base = (alpha shl 24) or (base and 0x00FFFFFF)
            }
            base
        } catch (e: Exception) {
            try {
                transform(stored, Color::class.java) as Int
            } catch (e1: Exception) {
                Integer.MAX_VALUE
            }
        }
    }

    @JvmStatic
    fun <T> get(c: Class<T>, root: XMLPrefsRoot, s: String): T? {
        val stored = root.getValues().get(s)?.value ?: return null
        return try {
            transform(stored, c) as T
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The value of [prefsSave] as [c]. Falls back to its default when the stored value can't be
     * parsed, and to the type's zero value (`0`, `false`, `null` for objects) when the default can't either.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> get(c: Class<T>, prefsSave: XMLPrefsSave): T = getOrNull(c, prefsSave) as T

    /** [get], typed honestly: for object types like [File] the last-resort value is `null`. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getOrNull(c: Class<T>, prefsSave: XMLPrefsSave): T? {
        try {
            return transform(storedOrDefault(prefsSave), c) as T?
        } catch (e: Exception) {
            LogTuils.log(e)
            // the stored value couldn't be parsed
            try {
                return transform(prefsSave.defaultValue(), c) as T
            } catch (e1: Exception) {
                LogTuils.log(e1)
                // attempts to get a default value for the given type, as we say in italian, "the last beach"
                return LogTuils.getDefaultValue(c) as T
            }
        }
    }

    @JvmStatic
    fun get(prefsSave: XMLPrefsSave): String = get(String::class.java, prefsSave)

    @JvmStatic
    fun get(root: XMLPrefsRoot, s: String): String? = get(String::class.java, root, s)

    @JvmStatic
    fun wasChanged(save: XMLPrefsSave, allowLengthZero: Boolean): Boolean {
        val value = get(save)
        return (allowLengthZero || value.isNotEmpty()) && value != save.defaultValue()
    }

}
