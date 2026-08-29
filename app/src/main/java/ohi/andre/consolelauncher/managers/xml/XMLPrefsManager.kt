package ohi.andre.consolelauncher.managers.xml

import android.content.Context
import android.graphics.Color
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.classes.DomXMLPrefsList
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
import ohi.andre.consolelauncher.tuils.FileTuils
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
object XMLPrefsManager {

    @JvmField
    val XML_DEFAULT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    @JvmField
    val VALUE_ATTRIBUTE = "value"

    private var factory: DocumentBuilderFactory
    private var builder: DocumentBuilder? = null

    init {
        factory = DocumentBuilderFactory.newInstance()
        try {
            builder = factory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
        }
    }

    enum class XMLPrefsRoot(en: Array<out XMLPrefsSave>) : XMLPrefsElement {
        THEME(Theme.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf()
        },
        CMD(Cmd.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf()
        },
        TOOLBAR(Toolbar.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf()
        },
        UI(Ui.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf()
        },
        BEHAVIOR(Behavior.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf()
        },
        SUGGESTIONS(Suggestions.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf(
                "app_suggestions_minrate",
                "contact_suggestions_minrate",
                "song_suggestions_minrate",
                "file_suggestions_minrate",
            )
        },
        PALETTE(Palette.values()) {
            override val sharedPrefsBacked: Boolean = true
            override fun delete(): Array<String?> = arrayOf()
        },
        ;

        @JvmField
        val path: String = name.lowercase() + ".xml"

        open val sharedPrefsBacked: Boolean = false

        @JvmField
        var values: XMLPrefsList = DomXMLPrefsList()

        @JvmField
        val enums: MutableList<XMLPrefsSave> = ArrayList(en.toList())

        fun bindSharedPrefsAndSeedDefaults(context: Context) {
            val prefs = context.getSharedPreferences(path, Context.MODE_PRIVATE)
            values = SharedPrefsXMLPrefsList(prefs)

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

        override fun write(c: Context, save: XMLPrefsSave, value: String) {
            if (sharedPrefsBacked) {
                values.add(save.label(), value)
            } else {
                set(File(FileTuils.getFolder(c), path), save.label(), arrayOf(VALUE_ATTRIBUTE), arrayOf(value))
            }
        }

        override fun getValues(): XMLPrefsList = values

        override fun path(): String = path
    }

    @JvmStatic
    fun dispose() {
        commonsLoaded = false

        for (element in XMLPrefsRoot.values()) {
            if (!element.sharedPrefsBacked) element.values.clear()
        }
    }

    @JvmField
    var commonsLoaded = false

    @JvmStatic
    fun loadCommons(context: Context) {
        if (commonsLoaded) return
        commonsLoaded = true

        val folder = FileTuils.getFolder(context)
        if (folder == null) {
            OutputTuils.sendOutput(context, R.string.tuinotfound_xmlprefs, Color.RED)
            return
        }

        for (element in XMLPrefsRoot.values()) {
            if (element.sharedPrefsBacked) {
                element.bindSharedPrefsAndSeedDefaults(context)
                continue
            }

            val file = File(folder, element.path)
            if (!file.exists()) {
                resetFile(file, element.name)
            }

            val o: Array<Any?>
            try {
                val built = buildDocument(file, element.name)
                if (built == null) {
                    OutputTuils.sendXMLParseError(context, element.path)
                    return
                }
                o = built
            } catch (e: SAXParseException) {
                OutputTuils.sendXMLParseError(context, element.path, e)
                continue
            } catch (e: Exception) {
                LogTuils.log(e)
                return
            }

            var d = o[0] as Document
            var root = o[1] as Element?

            // we are keeping this because maybe there are some new values to write
            val enums: MutableList<XMLPrefsSave> = ArrayList(element.enums)

            val deleted = element.delete()
            var needToWrite = false

            if (root == null) {
                resetFile(file, element.name)
                try {
                    d = builder!!.parse(file)
                } catch (e: Exception) {
                }
                root = d.getElementsByTagName(element.name).item(0) as Element
            }
            val nodes = root.getElementsByTagName("*")

            for (count in 0 until nodes.length) {
                val node = nodes.item(count)
                val nn = node.nodeName

                val value: String
                try {
                    value = node.attributes.getNamedItem(VALUE_ATTRIBUTE).nodeValue
                } catch (e: Exception) {
                    continue
                }
                var resolvedValue = value

                var check = false
                var en = 0
                while (en < enums.size) {
                    val opt = enums[en]

                    if (opt.label() == nn) {
                        enums.removeAt(en)

                        val iv = opt.invalidValues()
                        if (iv != null) {
                            for (temp in iv) {
                                if (temp == resolvedValue) {
                                    resolvedValue = opt.defaultValue()

                                    val em = node as Element
                                    em.setAttribute(VALUE_ATTRIBUTE, resolvedValue)

                                    needToWrite = true

                                    break
                                }
                            }
                        }

                        element.values.add(nn, resolvedValue)

                        check = true
                        break
                    }
                    en++
                }

                if (!check && deleted != null) {
                    @Suppress("UNCHECKED_CAST")
                    val index = StringTuils.find(nn, deleted as Array<Any?>)
                    if (index != -1) {
                        deleted[index] = null
                        val e = node as Element
                        root.removeChild(e)

                        needToWrite = true
                    }
                }
            }

            if (enums.size == 0) {
                if (needToWrite) writeTo(d, file)
                continue
            }

            for (s in enums) {
                val value = s.defaultValue()

                val em = d.createElement(s.label())
                em.setAttribute(VALUE_ATTRIBUTE, value)
                root.appendChild(em)

                element.values.add(s.label(), value)
            }

            writeTo(d, file)
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

    @JvmStatic
    fun getInt(prefsSave: XMLPrefsSave): Int = get(Int::class.javaPrimitiveType!!, prefsSave)

    @JvmStatic
    fun getBoolean(prefsSave: XMLPrefsSave): Boolean = get(Boolean::class.javaPrimitiveType!!, prefsSave)

    @JvmStatic
    fun getColor(prefsSave: XMLPrefsSave): Int {
        if (prefsSave.parent() == null) return Integer.MAX_VALUE

        if (XMLPrefsSave.PALETTE_REF == prefsSave.type()) {
            return getPaletteRefColor(prefsSave)
        }

        try {
            return transform(prefsSave.parent().getValues().get(prefsSave)!!.value, Color::class.java) as Int
        } catch (e: Exception) {
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
        val stored: String? = try {
            prefsSave.parent().getValues().get(prefsSave)!!.value
        } catch (e: Exception) {
            prefsSave.defaultValue()
        }

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
        return try {
            transform(root.getValues().get(s)!!.value, c) as T
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun <T> get(c: Class<T>, prefsSave: XMLPrefsSave): T {
        try {
            return transform(prefsSave.parent().getValues().get(prefsSave)!!.value, c) as T
        } catch (e: Exception) {
            LogTuils.log(e)
            // this will happen if the option is not found
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

    private val p1: Pattern = Pattern.compile(">")

    private val p3: Pattern = Pattern.compile("\n\n")

    private val p1s: String = ">" + Tuils.NEWLINE

    private val p3s: String = Tuils.NEWLINE

    private fun fixNewlines(s: String): String {
        var r = p1.matcher(s).replaceAll(p1s)
        r = p3.matcher(r).replaceAll(p3s)
        return r
    }

    @JvmStatic
    @Throws(Exception::class)
    fun buildDocument(file: File?, rootName: String?): Array<Any?>? {
        val f = file!!
        if (!f.exists()) {
            resetFile(f, rootName)
        }

        var d: Document
        try {
            d = builder!!.parse(f)
        } catch (e: Exception) {
            LogTuils.log(e)

            val nOfBytes = FileTuils.nOfBytes(f)
            if (nOfBytes == 0 && rootName != null) {
                resetFile(f, rootName)
                d = builder!!.parse(f)
            } else return null
        }

        val r = d.documentElement
        return arrayOf(d, r)
    }

    @JvmStatic
    fun writeTo(d: Document?, f: File?) {
        try {
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()

            val source = DOMSource(d)
            val writer = StringWriter()
            val result = StreamResult(writer)
            transformer.transform(source, result)

            val s = fixNewlines(writer.toString())

            val stream = FileOutputStream(f)
            stream.write(s.toByteArray())

            stream.flush()
            stream.close()
        } catch (e: Exception) {
            LogTuils.log(e)
        }
    }

    // this will only add, it won't check if there's already one
    @JvmStatic
    fun add(file: File?, elementName: String?, attributeNames: Array<out String?>, attributeValues: Array<out String?>): String? {
        try {
            val o: Array<Any?>
            try {
                val built = buildDocument(file, null)
                if (built == null) return Tuils.EMPTYSTRING
                o = built
            } catch (e: Exception) {
                LogTuils.log(e)
                return e.toString()
            }

            val d = o[0] as Document
            val root = o[1] as Element

            val element = d.createElement(elementName)
            for (c in attributeNames.indices) {
                val v = attributeValues[c] ?: continue
                element.setAttribute(attributeNames[c], v)
            }
            root.appendChild(element)

            writeTo(d, file)
        } catch (e: Exception) {
            LogTuils.log(e)
            return e.toString()
        }
        return null
    }

    @JvmStatic
    fun set(file: File?, elementName: String?, attributeNames: Array<out String?>, attributeValues: Array<out String?>): String? {
        return set(file, elementName, null, null, attributeNames, attributeValues, true)
    }

    @JvmStatic
    fun set(
        file: File?,
        elementName: String?,
        thatHasThose: Array<out String?>?,
        forValues: Array<out String?>?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out String?>,
        addIfNotFound: Boolean,
    ): String? {
        val values = arrayOf(attributeValues)
        return setMany(file, arrayOf(elementName), thatHasThose, forValues, attributeNames, values, addIfNotFound)
    }

    private fun setMany(
        file: File?,
        elementNames: Array<String?>,
        thatHasThose: Array<out String?>?,
        forValues: Array<out String?>?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out Array<out String?>>,
        addIfNotFound: Boolean,
    ): String? {
        try {
            val o: Array<Any?>
            try {
                val built = buildDocument(file, null)
                if (built == null) return Tuils.EMPTYSTRING
                o = built
            } catch (e: Exception) {
                LogTuils.log(e)
                return e.toString()
            }

            val d = o[0] as Document?
            val root = o[1] as Element?

            if (d == null || root == null) {
                return Tuils.EMPTYSTRING
            }

            var nFound = 0

            Main@ for (c in elementNames.indices) {
                val elementName = elementNames[c] ?: continue
                val nodes = root.getElementsByTagName(elementName)

                Nodes@ for (j in 0 until nodes.length) {
                    val n = nodes.item(j)
                    if (n.nodeType == Node.ELEMENT_NODE) {
                        val e = n as Element

                        if (!checkAttributes(e, thatHasThose, forValues, false)) {
                            continue@Nodes
                        }

                        nFound++

                        for (a in attributeNames.indices) {
                            e.setAttribute(attributeNames[a], attributeValues[c][a])
                        }

                        elementNames[c] = null

                        continue@Main
                    }
                }
            }

            if (nFound < elementNames.size) {
                for (count in elementNames.indices) {
                    val name = elementNames[count]
                    if (name == null || name.isEmpty()) continue

                    if (!addIfNotFound) continue

                    val element = d.createElement(name)
                    for (c in attributeNames.indices) {
                        val v = attributeValues[count][c] ?: continue
                        element.setAttribute(attributeNames[c], v)
                    }
                    root.appendChild(element)
                }
            }

            writeTo(d, file)

            if (nFound == 0) return Tuils.EMPTYSTRING
            return null
        } catch (e: Exception) {
            LogTuils.log(e)
            // LogTuils.toFile(e);
            return e.toString()
        }
    }

    // return "" if node not found, null if all good
    @JvmStatic
    fun removeNode(file: File?, nodeName: String?): String? {
        return removeNode(file, nodeName, null, null)
    }

    @JvmStatic
    fun removeNode(file: File?, nodeName: String?, thatHasThose: Array<out String?>?, forValues: Array<out String?>?): String? {
        try {
            val o: Array<Any?>
            try {
                val built = buildDocument(file, null)
                if (built == null) return Tuils.EMPTYSTRING
                o = built
            } catch (e: Exception) {
                return e.toString()
            }

            val d = o[0] as Document
            val root = o[1] as Element

            val n = findNode(root, nodeName, thatHasThose, forValues) ?: return Tuils.EMPTYSTRING

            root.removeChild(n)
            writeTo(d, file)

            return null
        } catch (e: Exception) {
            return e.toString()
        }
    }

    @JvmStatic
    fun findNode(file: File?, nodeName: String?, thatHasThose: Array<out String?>?, forValues: Array<out String?>?): Node? {
        try {
            val o: Array<Any?>
            try {
                val built = buildDocument(file, null)
                if (built == null) return null
                o = built
            } catch (e: Exception) {
                return null
            }

            val root = o[1] as Element

            return findNode(root, nodeName, thatHasThose, forValues)
        } catch (e: Exception) {
            return null
        }
    }

    // useful only if you're looking for a single node
    private fun findNode(root: Element?, nodeName: String?, thatHasThose: Array<out String?>?, forValues: Array<out String?>?): Node? {
        val nodes = root!!.getElementsByTagName(nodeName)
        for (j in 0 until nodes.length) {
            if (checkAttributes(nodes.item(j) as Element, thatHasThose, forValues, false)) return nodes.item(j)
        }
        return null
    }

    @JvmStatic
    fun findNode(root: Element?, nodeName: String?): Node? {
        return findNode(root, nodeName, null, null)
    }

    private fun checkAttributes(e: Element, thatHasThose: Array<out String?>?, forValues: Array<out String?>?, alsoIfAttributeNotFound: Boolean): Boolean {
        if (thatHasThose != null && forValues != null && thatHasThose.size == forValues.size) {
            for (a in thatHasThose.indices) {
                if (!e.hasAttribute(thatHasThose[a])) return alsoIfAttributeNotFound
                if (forValues[a] != e.getAttribute(thatHasThose[a])) return false
            }
        }
        return true
    }

    @JvmStatic
    fun resetFile(f: File, name: String?): Boolean {
        try {
            if (f.exists()) f.delete()

            val stream = FileOutputStream(f)
            stream.write(XML_DEFAULT.toByteArray())
            stream.write(("<" + name + ">\n").toByteArray())
            stream.write(("</" + name + ">\n").toByteArray())
            stream.flush()
            stream.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @JvmStatic
    fun getStringAttribute(e: Element, attribute: String): String? {
        return if (e.hasAttribute(attribute)) e.getAttribute(attribute) else null
    }

    @JvmStatic
    fun getLongAttribute(e: Element, attribute: String): Long {
        val value = getStringAttribute(e, attribute)
        return try {
            value!!.toLong()
        } catch (ex: Exception) {
            -1
        }
    }

    @JvmStatic
    fun getBooleanAttribute(e: Element, attribute: String): Boolean {
        val s = getStringAttribute(e, attribute)
        return s != null && s.toBoolean()
    }

    @JvmStatic
    fun getIntAttribute(e: Element, attribute: String): Int {
        return try {
            getStringAttribute(e, attribute)!!.toInt()
        } catch (ex: Exception) {
            -1
        }
    }

    class IdValue(@JvmField var value: String, @JvmField var id: Int)
}
