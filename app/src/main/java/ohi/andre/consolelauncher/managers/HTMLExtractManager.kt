package ohi.andre.consolelauncher.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.support.v4.content.LocalBroadcastManager
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import com.jayway.jsonpath.JsonPath
import net.minidev.json.JSONArray
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.Tuils
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.htmlcleaner.HtmlCleaner
import org.htmlcleaner.TagNode
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.dropLastWhile
import kotlin.collections.indices
import kotlin.collections.toTypedArray

/**
 * Created by francescoandreuzzi on 29/03/2018.
 */
class HTMLExtractManager(context: Context, client: OkHttpClient) {
    private val xpaths: MutableList<StoreableValue>
    private val jsons: MutableList<StoreableValue>
    private val formats: MutableList<StoreableValue>
    private val TAG: String = "HTMLExtractManager"

    private val client: OkHttpClient
    private val receiver: BroadcastReceiver

    var defaultFormat: String
    var weatherFormat: String
    var weatherColor: Int

    private fun getListFromType(t: StoreableValue.Type?): MutableList<StoreableValue> {
        if (t == StoreableValue.Type.xpath) return xpaths
        else if (t == StoreableValue.Type.json) return jsons
        else return formats
    }

    fun dispose(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
    }

    var weatherFormatPattern: Pattern =
        Pattern.compile("%([a-z_]+)(\\d)*(?:\\$\\(([\\.\\+\\-\\*\\/\\^\\d]+)\\))?")

    private fun query(
        context: Context,
        path: String?,
        pathType: StoreableValue.Type?,
        format: String?,
        url: String,
        weatherArea: Boolean
    ) {
        object : Thread() {
            override fun run() {
                super.run()

                if (!Tuils.hasInternetAccess()) {
                    output(R.string.no_internet, context, weatherArea)
                    return
                }

                try {
                    val builder = Request.Builder()
                        .url(url)
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:15.0) Gecko/20100101 Firefox/15.0.1"
                        )
                        .get()

                    val response = client.newCall(builder.build()).execute()

                    if (response.code() == 429 && weatherArea) {
                        val i = Intent(UIManager.ACTION_WEATHER_DELAY)
                        LocalBroadcastManager.getInstance(context.getApplicationContext())
                            .sendBroadcast(i)

                        return
                    } else if (!response.isSuccessful()) {
                        val message =
                            context.getString(R.string.internet_error) + Tuils.SPACE + response.code()

                        if (weatherArea) {
                            val i = Intent(UIManager.ACTION_WEATHER)
                            i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, message)
                            LocalBroadcastManager.getInstance(context.getApplicationContext())
                                .sendBroadcast(i)
                        } else {
                            output(message, context, false)
                        }

                        return
                    }

                    val inputStream = response.body()!!.byteStream()

                    var output: CharSequence? = Tuils.span(Tuils.EMPTYSTRING, outputColor)

                    if (weatherArea) {
                        val json = Tuils.inputStreamToString(inputStream)

                        //                        json = json.replaceAll("\"temp\":([\\d\\.]*)", "\"temp\":-4.3");
                        var o: CharSequence = Tuils.span(weatherFormat, weatherColor)

                        val m = weatherFormatPattern.matcher(weatherFormat)
                        while (m.find()) {
                            val name = m.group(1)
                            var delay = m.group(2)
                            if (delay == null || delay.length == 0) delay = "1"
                            val converter = m.group(3)

                            val stopAt = delay.toInt()

                            val p =
                                Pattern.compile("\"" + name + "\":(?:\"([^\"]+)\"|(-?\\d+\\.?\\d*))")
                            val m1 = p.matcher(json)
                            var c = 1
                            while (m1.find()) {
                                if (c == stopAt) {
                                    var value = m1.group(1)
                                    if (value == null || value.length == 0) value = m1.group(2)

                                    if (converter != null && converter.length > 0) {
                                        try {
                                            var d = value.toDouble()
                                            d = Tuils.textCalculus(d, converter)
                                            value = String.format("%.2f", d)
                                        } catch (e: Exception) {
                                            Tuils.log(e)
                                        }
                                    }

                                    o = TextUtils.replace(
                                        o, arrayOf<String?>(m.group(0)), arrayOf<String>(
                                            delimiterStart + value + delimiterEnd
                                        )
                                    )

                                    break
                                } else c++
                            }
                        }

                        o = replaceLinkColorReplace(context, o, url)
                        o = removeDelimiter(o)

                        val i = Intent(UIManager.ACTION_WEATHER)
                        i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, o)
                        LocalBroadcastManager.getInstance(context.getApplicationContext())
                            .sendBroadcast(i)
                    } else if (pathType == StoreableValue.Type.xpath) {
                        val cleaner = HtmlCleaner()
                        val props = cleaner.getProperties()
                        props.setOmitComments(true)

                        var node = cleaner.clean(inputStream)
                        val nodes = node.evaluateXPath(path)
                        if (nodes.size == 0) {
                            Tuils.sendOutput(context, R.string.no_result)
                            return
                        }

                        for (c in nodes.indices) {
                            node = nodes[c] as TagNode

                            val f = if (format == null) defaultFormat else format
                            var copy: CharSequence = Tuils.span(f, outputColor)

                            copy = Companion.replaceAllAttributesString(
                                copy,
                                node.getAttributes().entries as MutableSet<MutableMap.MutableEntry<String?, String?>?>
                            )
                            copy = replaceTagNameString(copy, node.getName(), node.getAttributes())
                            copy = replaceNodeValue(copy, node.getText().toString())
                            copy = replaceNewline(copy)
                            copy = replaceLinkColorReplace(context, copy, url)
                            copy = removeDelimiter(copy)

                            if (copy.toString().trim { it <= ' ' }.length > 0) output =
                                TextUtils.concat(
                                    output,
                                    (if (c != 0) Tuils.NEWLINE + Tuils.NEWLINE else Tuils.EMPTYSTRING),
                                    copy
                                )
                        }

                        output(output, context, weatherArea, TerminalManager.CATEGORY_NO_COLOR)
                    } else {
                        val o = JsonPath.read<Any>(inputStream, path)

                        if (o is MutableMap<*, *>) {
//                            this should be a single JSON object

                            val f = if (format == null) defaultFormat else format
                            var copy: CharSequence = Tuils.span(f, outputColor)
                            val entry = o.entries as MutableSet<MutableMap.MutableEntry<String?, Any?>?>

                            copy = Companion.replaceAllAttributesObject(copy, entry)
                            copy = replaceTagNameObject(copy, null, o as MutableMap<String?, Any?>)
                            copy = replaceNewline(copy)
                            copy = replaceLinkColorReplace(context, copy, url)
                            copy = removeDelimiter(copy)

                            output = copy

                            output(output, context, weatherArea, TerminalManager.CATEGORY_NO_COLOR)
                        } else if (o is MutableList<*>) {
//                            this is an array of JSON objects
                            val a = o as JSONArray

                            for (c in a.indices) {
                                val f = if (format == null) defaultFormat else format
                                var copy: CharSequence = Tuils.span(f, outputColor)

                                val m = a.get(c) as LinkedHashMap<String?, Any?>

                                copy = Companion.replaceAllAttributesObject(copy,
                                    m.entries as MutableSet<MutableMap.MutableEntry<String?, Any?>?>
                                )
                                copy = replaceTagNameObject(copy, null, m)
                                copy = replaceNewline(copy)
                                copy = replaceLinkColorReplace(context, copy, url)
                                copy = removeDelimiter(copy)

                                if (copy.toString().trim { it <= ' ' }.length > 0) output =
                                    TextUtils.concat(
                                        output,
                                        (if (c != 0) Tuils.NEWLINE + Tuils.NEWLINE else Tuils.EMPTYSTRING),
                                        copy
                                    )
                            }

                            output(output, context, weatherArea, TerminalManager.CATEGORY_NO_COLOR)
                        } else if (o is String) {
                            output = Tuils.span(o.toString(), outputColor)
                            output(output, context, weatherArea, TerminalManager.CATEGORY_NO_COLOR)
                        } else {
                            Tuils.sendOutput(outputColor, context, o.toString())
                        }
                    }
                } catch (e: Exception) {
                    output(e.toString(), context, weatherArea)
                    Tuils.toFile(e)
                    Tuils.log(e)
                }
            }
        }.start()
    }

    private fun query(context: Context, format: String?, url: String) {
        query(context, null, null, format, url, true)
    }

    private enum class What {
        COLOR,
        LINK,
        REPLACE
    }

    init {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(BROADCAST_COUNT, 0) < broadcastCount) return
                broadcastCount++

                val action = intent.getAction()

                val intent_id = intent.getIntExtra(ID, Int.Companion.MAX_VALUE)
                val intent_tag = intent.getStringExtra(TAG_NAME)
                val intent_value = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)

                when (action) {
                    ACTION_ADD -> {
                        var id_already = false
                        if (intent_tag == StoreableValue.Type.format.name) {
                            id_already = formats.indices.any { formats.get(it).id == intent_id }
                        } else if (intent_tag == StoreableValue.Type.xpath.name) {
                            id_already = xpaths.indices.any { xpaths.get(it).id == intent_id}
                        } else {
                            id_already = jsons.indices.any { jsons.get(it).id == intent_id}
                        }
                        if (id_already) {
                            Tuils.sendOutput(context, R.string.id_already)
                            return
                        }

                        val values: MutableList<StoreableValue>
                        try {
                            val p = StoreableValue.Type.valueOf(intent_tag!!)
                            values = getListFromType(p)
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            return
                        }
                        val v = StoreableValue.Companion.create(values, context, intent_tag, intent_value, intent_id)
                        if (v != null) values.add(v)
                    }
                    ACTION_RM -> {
                        var check = false
                        for (c in xpaths.indices) {
                            if (xpaths.get(c).id == intent_id) {
                                xpaths.removeAt(c).remove(context)
                                check = true
                                break
                            }
                        }
                        for (c in jsons.indices) {
                            if (jsons.get(c).id == intent_id) {
                                jsons.removeAt(c).remove(context)
                                check = true
                                break
                            }
                        }
                        for (c in formats.indices) {
                            if (formats.get(c).id == intent_id) {
                                formats.removeAt(c).remove(context)
                                check = true
                                break
                            }
                        }
                        if (!check) Tuils.sendOutput(context, R.string.id_notfound)
                    }
                    ACTION_EDIT -> {
                        if (intent_value == null || intent_value.length == 0) return
                        for (c in xpaths.indices) {
                            if (xpaths.get(c).id == intent_id) {
                                xpaths.get(c).edit(context, intent_value)
                                return
                            }
                        }
                        for (c in jsons.indices) {
                            if (jsons.get(c).id == intent_id) {
                                jsons.get(c).edit(context, intent_value)
                                return
                            }
                        }
                        for (c in formats.indices) {
                            if (formats.get(c).id == intent_id) {
                                formats.get(c).edit(context, intent_value)
                                return
                            }
                        }
                        Tuils.sendOutput(context, R.string.id_notfound)
                    }
                    ACTION_LS -> {
                        val values: MutableList<StoreableValue>
                        val builder = StringBuilder()
                        try {
                            val p = StoreableValue.Type.valueOf(intent_tag)
                            values = getListFromType(p)
                            for (v in values) {
                                builder.append("- ID: ").append(v.id).append(" -> ").append(v.value).append(Tuils.NEWLINE)
                            }
                        } catch (e: Exception) {
                            val items = arrayOf(Pair("Xpaths:", xpaths), Pair("JsonPaths:", jsons), Pair("Formats:", formats))
                            items.forEach {
                                builder.append(it.first).append(Tuils.NEWLINE)
                                for (v in it.second) {
                                    builder.append(Tuils.DOUBLE_SPACE).append("- ID: ").append(v.id).append(" -> ").append(v.value).append(Tuils.NEWLINE)
                                }

                            }
                        }
                        var text = builder.toString().trim { it <= ' ' }
                        if (text.length == 0) text = "[]"
                        Tuils.sendOutput(context, text)
                    }
                    ACTION_QUERY -> {
                        val website = intent_value
                        val weatherArea = intent.getBooleanExtra(WEATHER_AREA, false)
                        var path: String? = intent_id.toString()
                        var format = intent.getStringExtra(FORMAT_ID)

                        if (format == null) {
                            val formatId = intent.getIntExtra(FORMAT_ID, Int.Companion.MAX_VALUE)
                            format = formats.find { it.id == formatId }?.value ?: null
                            if (format == null) {
                                Tuils.sendOutput(context, context.getString(R.string.id_notfound) + ": " + formatId + "(" + StoreableValue.Type.format.name + ")" )
                            }
                        }

                        var pathType: StoreableValue.Type? = StoreableValue.Type.json
                        if (path == null) {
                            val pathId = intent.getIntExtra(ID, Int.Companion.MAX_VALUE)
                            val xpath = xpaths.find { it.id == pathId}
                            path = xpath?.value
                            pathType = xpath?.type

                            val jsonpath = jsons.find { it.id == pathId }
                            path = jsonpath?.value
                            pathType = jsonpath?.type

                            if (path == null) {
                                Tuils.sendOutput( context, context.getString(R.string.id_notfound) + ": " + pathId )
                                return
                            }
                        }
                        query(context, path, pathType, format, website!!, weatherArea)
                    }
                    ACTION_WEATHER -> {
                        val url = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                        query(context, weatherFormat, url!!)
                    }
                }
            }
        }

        val actions = arrayOf(ACTION_ADD, ACTION_RM, ACTION_EDIT, ACTION_LS, ACTION_QUERY, ACTION_WEATHER)
        val filter = IntentFilter()
        actions.forEach { filter.addAction(it) }

        this.client = client

        linkColor = XMLPrefsManager.getColor(Theme.link_color)
        outputColor = XMLPrefsManager.getColor(Theme.output_color)
        weatherColor = XMLPrefsManager.getColor(Theme.weather_color)
        defaultFormat = XMLPrefsManager.get(Behavior.htmlextractor_default_format)
        optionalValueSeparator = XMLPrefsManager.get(Behavior.optional_values_separator)
        weatherFormat = XMLPrefsManager.get(Behavior.weather_format)

        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .registerReceiver(receiver, filter)
        broadcastCount = 0

        xpaths = ArrayList<StoreableValue>()
        jsons = ArrayList<StoreableValue>()
        formats = ArrayList<StoreableValue>()

        val file = File(Tuils.getFolder(context), PATH)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, NAME)
            val root = o[1] as Element
            val nodes = root.getElementsByTagName("*")
            for (count in 0..<nodes.getLength()) {
                val n = nodes.item(count)
                try {
                    val v = StoreableValue.Companion.fromNode((n as org.w3c.dom.Element?)!!)
                    if (v != null) {
                        getListFromType(v.type).add(v)
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: SAXParseException) {
            Tuils.sendXMLParseError(context, PATH, e)
            Log.e(TAG, e.toString())
        } catch (e: Exception) {
            Tuils.log(e)
            Log.e(TAG, e.toString())
        }
    }

    class StoreableValue {
        enum class Type {
            xpath,
            json,
            format
        }

        var id: Int
        var value: String?

        var type: Type

        constructor(id: Int, value: String?, type: Type) {
            this.id = id
            this.value = value
            this.type = type
        }

        private constructor(id: Int, value: String?, type: String?) {
            this.id = id
            this.value = value
            this.type = Type.valueOf(type!!)
        }

        fun remove(context: Context?) {
            val file = File(Tuils.getFolder(context), PATH)
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val output = XMLPrefsManager.removeNode(
                file,
                type.name,
                arrayOf<String?>(ID),
                arrayOf<String>(id.toString())
            )
            if (output != null) {
                if (output.length > 0) Tuils.sendOutput(Color.RED, context, output)
                else {
                    Tuils.sendOutput(Color.RED, context, R.string.id_notfound)
                }
            }
        }

        fun edit(context: Context?, newExpression: String?) {
            val file = File(Tuils.getFolder(context), PATH)
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val output = XMLPrefsManager.set(
                file,
                type.name,
                arrayOf<String?>(ID),
                arrayOf<String>(id.toString()),
                arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
                arrayOf<String?>(newExpression),
                false
            )
            if (output != null) {
                if (output.length > 0) Tuils.sendOutput(Color.RED, context, output)
                else {
                    Tuils.sendOutput(Color.RED, context, R.string.id_notfound)
                }
            } else {
                this.value = newExpression
            }
        }

        companion object {
            fun fromNode(e: Element): StoreableValue? {
                val nn = e.getNodeName()

                val id = XMLPrefsManager.getIntAttribute(e, ID)
                val value = XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)

                try {
                    return StoreableValue(id, value, nn)
                } catch (e1: Exception) {
                    return null
                }
            }

            fun create(
                values: MutableList<StoreableValue>,
                context: Context?,
                tag: String?,
                path: String?,
                id: Int
            ): StoreableValue? {
                for (c in values.indices) {
                    if (values.get(c).id == id) {
                        Tuils.sendOutput(context, R.string.id_already)
                        return null
                    }
                }

                val file = File(Tuils.getFolder(context), PATH)
                if (!file.exists()) {
                    XMLPrefsManager.resetFile(file, NAME)
                }

                val output = XMLPrefsManager.add(
                    file,
                    tag,
                    arrayOf<String?>(ID, XMLPrefsManager.VALUE_ATTRIBUTE),
                    arrayOf<String?>(id.toString(), path)
                )
                if (output != null) {
                    if (output.length > 0) Tuils.sendOutput(Color.RED, context, output)
                    else Tuils.sendOutput(Color.RED, context, R.string.output_error)
                    return null
                }

                return StoreableValue(id, path, tag)
            }
        }
    }

    companion object {
        @JvmField
        var ACTION_ADD: String = BuildConfig.APPLICATION_ID + ".htmlextract_add"
        @JvmField
        var ACTION_RM: String = BuildConfig.APPLICATION_ID + ".htmlextract_rm"
        @JvmField
        var ACTION_EDIT: String = BuildConfig.APPLICATION_ID + ".htmlextract_edit"
        @JvmField
        var ACTION_LS: String = BuildConfig.APPLICATION_ID + ".htmlextract_ls"

        @JvmField
        var ACTION_QUERY: String = BuildConfig.APPLICATION_ID + ".htmlextract_query"
        var ACTION_WEATHER: String = BuildConfig.APPLICATION_ID + ".htmlextract_weather"

        @JvmField
        var ID: String = "id"
        @JvmField
        var FORMAT_ID: String = "formatId"
        @JvmField
        var TAG_NAME: String = "tag"
        var WEATHER_AREA: String = "wArea"

        @JvmField
        var BROADCAST_COUNT: String = "broadcastCount"

        @JvmField
        var PATH: String = "htmlextract.xml"
        var NAME: String = "HTMLEXTRACT"

        @JvmField
        var broadcastCount: Int = 0

        private fun output(string: Int, context: Context, weatherArea: Boolean) {
            output(context.getString(string), context, weatherArea)
        }

        private fun output(
            s: CharSequence?,
            context: Context,
            weatherArea: Boolean,
            category: Int = Int.Companion.MAX_VALUE
        ) {
            if (weatherArea) {
                val i = Intent(UIManager.ACTION_WEATHER)
                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, s)
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i)
            } else {
                if (category != Int.Companion.MAX_VALUE) Tuils.sendOutput(context, s, category)
                else Tuils.sendOutput(context, s)
            }
        }

        private fun output(string: Int, context: Context, weatherArea: Boolean, category: Int) {
            output(context.getString(string), context, weatherArea, category)
        }

        var tagName: Pattern = Pattern.compile("%t(?:\\(([^)]*)\\))?", Pattern.CASE_INSENSITIVE)
        var nodeValuePattern: String = "%v"

        var allAttributes: Pattern =
            Pattern.compile("%a\\(([^\\)]*)\\)\\(([^\\)]*)\\)", Pattern.CASE_INSENSITIVE)
        var attributeName: Pattern = Pattern.compile("%an", Pattern.CASE_INSENSITIVE)
        var attributeValue: Pattern = Pattern.compile("%av", Pattern.CASE_INSENSITIVE)

        var linkColor: Int = Color.BLUE
        var outputColor: Int = Color.RED

        fun removeDelimiter(original: CharSequence): CharSequence {
            var original = original
            var newSequence = original
            do {
                original = newSequence
                newSequence = TextUtils.replace(original, delimiterArray, delimiterReplacementArray)
            } while (newSequence.length < original.length)

            return newSequence
        }

        fun replaceAllAttributesObject(
            original: CharSequence,
            set: MutableSet<MutableMap.MutableEntry<String?, Any?>?>
        ): CharSequence {
            var original = original
            val allAttributesMatcher: Matcher = allAttributes.matcher(original)
            if (allAttributesMatcher.find()) {
                val first = allAttributesMatcher.group(1)
                val separator = allAttributesMatcher.group(2)
                val b = StringBuilder()
                b.append(delimiterStart)
                for (element in set) {
                    var temp = first
                    temp = attributeName.matcher(temp).replaceAll(element?.key)
                    temp = attributeValue.matcher(temp).replaceAll(
                        Tuils.removeUnncesarySpaces(
                            element?.value.toString().trim { it <= ' ' })
                    )

                    b.append(temp)
                    b.append(separator)
                }
                b.append(delimiterEnd)

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(allAttributesMatcher.group()),
                    arrayOf<CharSequence>(b.toString().trim { it <= ' ' })
                )
            }

            return original
        }

        fun replaceTagNameObject(
            original: CharSequence,
            tag: String?,
            attributes: MutableMap<String?, Any?>?
        ): CharSequence {
            var tag = tag
            val tagMatcher: Matcher = tagName.matcher(original)
            while (tagMatcher.find()) {
                val attribute = tagMatcher.group(1)

                if (tag == null) tag = "null"

                var replace: String? = "null"
                if (attribute == null || attribute.length == 0) {
                    replace = tag
                } else if (attributes != null) {
                    replace = attributes.get(attribute).toString()
                    if (replace == null || replace.length == 0) replace = "null"
                }

                return TextUtils.replace(
                    original, arrayOf<String>(tagMatcher.group()), arrayOf<CharSequence>(
                        delimiterStart + replace + delimiterEnd
                    )
                )
            }

            return original
        }

        fun replaceAllAttributesString(
            original: CharSequence,
            set: MutableSet<MutableMap.MutableEntry<String?, String?>?>
        ): CharSequence {
            var original = original
            val allAttributesMatcher: Matcher = allAttributes.matcher(original)
            if (allAttributesMatcher.find()) {
                val first = allAttributesMatcher.group(1)
                val separator = allAttributesMatcher.group(2)
                val b = StringBuilder()
                b.append(delimiterStart)
                for (element in set) {
                    var temp = first
                    temp = attributeName.matcher(temp).replaceAll(element?.key)
                    temp = attributeValue.matcher(temp)
                        .replaceAll(Tuils.removeUnncesarySpaces(element?.value!!.trim { it <= ' ' }))
                    b.append(temp)
                    b.append(separator)
                }
                b.append(delimiterEnd)

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(allAttributesMatcher.group()),
                    arrayOf<CharSequence>(b.toString().trim { it <= ' ' })
                )
            }

            return original
        }

        fun replaceTagNameString(
            original: CharSequence,
            tag: String?,
            attributes: MutableMap<String?, String?>?
        ): CharSequence {
            var tag = tag
            val tagMatcher: Matcher = tagName.matcher(original)
            while (tagMatcher.find()) {
                val attribute = tagMatcher.group(1)

                if (tag == null) tag = "null"

                var replace: String? = "null"
                if (attribute == null || attribute.length == 0) {
                    replace = tag
                } else if (attributes != null) {
                    replace = attributes.get(attribute)
                    if (replace == null || replace.length == 0) replace = "null"
                }

                return TextUtils.replace(
                    original, arrayOf<String>(tagMatcher.group()), arrayOf<CharSequence>(
                        delimiterStart + replace + delimiterEnd
                    )
                )
            }

            return original
        }

        fun replaceNodeValue(original: CharSequence?, nodeValue: String): CharSequence {
            var nodeValue = nodeValue
            nodeValue = Jsoup.parse(nodeValue).text()
            return TextUtils.replace(
                original,
                arrayOf<String?>(nodeValuePattern),
                arrayOf<CharSequence>(
                    delimiterStart + Tuils.removeUnncesarySpaces(nodeValue)
                        .trim { it <= ' ' } + delimiterEnd))
        }

        fun replaceNewline(original: CharSequence): CharSequence {
            var original = original
            var before: Int
            do {
                before = original.length
                original = TextUtils.replace(
                    original,
                    arrayOf<String>(Tuils.patternNewline.pattern()),
                    arrayOf<CharSequence>(Tuils.NEWLINE)
                )
            } while (original.length < before)

            return original
        }

        //    static Pattern linkColorReplace = Pattern.compile("#([a-zA-Z0-9]{6})?(?:\\[([^\\]]*)\\](@#&.*@#&)|\\[([^\\]]+)\\])", Pattern.CASE_INSENSITIVE);
        var colorPattern: Pattern = Pattern.compile("(#[a-fA-F0-9]{6})\\[([^\\]]+)\\]")
        var linkPattern: Pattern = Pattern.compile("#\\[((?:(?:http(?:s)?)|(?:www\\.))[^\\]]+)\\]")
        var replacePattern: Pattern = Pattern.compile("#(\\[.+?\\])@#&(.+?)&#@")

        var extractUrl: Pattern = Pattern.compile("(.*\\.[^\\/]{2,})\\/", Pattern.CASE_INSENSITIVE)

        //    this is used to know where a group begins and when it ends
        var delimiterStart: String = "@#&"
        var delimiterEnd: String = StringBuilder(delimiterStart).reverse().toString()
        var optionalValueSeparator: String = "/"
        var delimiterArray: Array<String?> = arrayOf<String?>(delimiterStart, delimiterEnd)
        var delimiterReplacementArray: Array<String?> =
            arrayOf<String?>(Tuils.EMPTYSTRING, Tuils.EMPTYSTRING)

        fun replaceLinkColorReplace(
            context: Context,
            original: CharSequence,
            url: String
        ): CharSequence {
            var original = original
            var m: Matcher = colorPattern.matcher(original)
            while (m.find()) {
                try {
                    val cl = Color.parseColor(m.group(1))
                    original = TextUtils.replace(
                        original,
                        arrayOf<String>(m.group()),
                        arrayOf<CharSequence>(Tuils.span(m.group(2), cl))
                    )
                } catch (e: Exception) {
                    Tuils.sendOutput(
                        context,
                        context.getString(R.string.output_invalidcolor) + ": " + m.group(1)
                    )
                }
            }

            m = linkPattern.matcher(original)
            while (m.find()) {
                var text = m.group(1)

                //            fix relative links
                if (text!!.startsWith("/")) {
                    val m1: Matcher = extractUrl.matcher(url)
                    if (m1.find()) {
                        text = m1.group(1) + text
                    }
                }

                val sp = SpannableString(text)
                sp.setSpan(
                    LongClickableSpan(Uri.parse(text)),
                    0,
                    sp.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sp.setSpan(
                    ForegroundColorSpan(linkColor),
                    0,
                    sp.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(m.group()),
                    arrayOf<CharSequence>(sp)
                )
            }

            m = replacePattern.matcher(original)
            while (m.find()) {
                val replaceGroups = m.group(1)
                var text = m.group(2)

                val groups: Array<String?> =
                    replaceGroups!!.split("]".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                for (c in groups.indices) {
                    groups[c] = groups[c]!!.replace("[\\[\\]]".toRegex(), Tuils.EMPTYSTRING)

                    val split: Array<String?> = groups[c]!!.split(optionalValueSeparator.toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (split.size == 0) continue

                    text = text!!.replace(split[0]!!.toRegex(), split[1]!!)
                }

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(m.group()),
                    arrayOf<CharSequence?>(text)
                )
            }

            return original
        }
    }
}
