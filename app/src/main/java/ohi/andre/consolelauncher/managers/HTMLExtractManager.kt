package ohi.andre.consolelauncher.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jayway.jsonpath.JsonPath
import kotlinx.serialization.Serializable
import net.minidev.json.JSONArray
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.loadJsonList
import ohi.andre.consolelauncher.managers.xml.classes.saveJsonList
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.FileTuils
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.MathTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.ViewTuils

import java.io.InputStream
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import android.text.Html
import org.htmlcleaner.HtmlCleaner
import org.htmlcleaner.TagNode
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

    /** xpaths, jsons and formats, in the order they're searched. */
    private fun allLists(): List<MutableList<StoreableValue>> = listOf(xpaths, jsons, formats)

    private fun add(context: Context, tag: String?, type: StoreableValue.Type?, value: String?, id: Int) {
        if (type == null) {
            Log.e(TAG, "unknown type: $tag")
            return
        }
        // create() reports R.string.id_already itself when the id is taken
        val values = getListFromType(type)
        val v = StoreableValue.Companion.create(values, context, type, value, id)
        if (v != null) values.add(v)
    }

    /** Removes [id] from every list that has it (ids are only unique per type). */
    private fun remove(context: Context, id: Int) {
        var found = false
        for (list in allLists()) {
            val index = list.indexOfFirst { it.id == id }
            if (index == -1) continue

            list.removeAt(index).remove(context)
            found = true
        }
        if (!found) OutputTuils.sendOutput(context, R.string.id_notfound)
    }

    /** Edits the first value with [id], searching xpaths, then jsons, then formats. */
    private fun edit(context: Context, id: Int, newValue: String?) {
        if (newValue.isNullOrEmpty()) return

        val target = allLists().firstNotNullOfOrNull { list -> list.find { it.id == id } }
        if (target == null) {
            OutputTuils.sendOutput(context, R.string.id_notfound)
            return
        }
        target.edit(context, newValue)
    }

    /** Lists the values of [type], or of every type (grouped) when [type] is null. */
    private fun list(context: Context, type: StoreableValue.Type?) {
        val builder = StringBuilder()
        if (type != null) {
            for (v in getListFromType(type)) {
                builder.append("- ID: ").append(v.id).append(" -> ").append(v.value).append(Tuils.NEWLINE)
            }
        } else {
            val items = arrayOf(Pair("Xpaths:", xpaths), Pair("JsonPaths:", jsons), Pair("Formats:", formats))
            items.forEach {
                builder.append(it.first).append(Tuils.NEWLINE)
                for (v in it.second) {
                    builder.append(Tuils.DOUBLE_SPACE).append("- ID: ").append(v.id).append(" -> ").append(v.value).append(Tuils.NEWLINE)
                }
            }
        }
        val text = builder.toString().trim { it <= ' ' }
        OutputTuils.sendOutput(context, text.ifEmpty { "[]" })
    }

    /** `htmlextract query`: looks up the saved path [pathId] and the requested format, then runs the query. */
    private fun query(context: Context, intent: Intent, pathId: Int, website: String?) {
        if (website == null) return
        val weatherArea = intent.getBooleanExtra(WEATHER_AREA, false)

        var format = intent.getStringExtra(FORMAT_ID)
        if (format == null) {
            val formatId = intent.getIntExtra(FORMAT_ID, Int.MAX_VALUE)
            // no format id: query() falls back to the default format
            if (formatId != Int.MAX_VALUE) {
                format = formats.find { it.id == formatId }?.value
                if (format == null) {
                    OutputTuils.sendOutput(context, context.getString(R.string.id_notfound) + ": " + formatId + "(" + StoreableValue.Type.format.name + ")" )
                }
            }
        }

        // jsons are checked first, matching the original Java (where a json match overrode an xpath one)
        val stored = jsons.find { it.id == pathId } ?: xpaths.find { it.id == pathId }
        if (stored == null) {
            OutputTuils.sendOutput(context, context.getString(R.string.id_notfound) + ": " + pathId)
            return
        }
        query(context, stored.value, stored.type, format, website, weatherArea)
    }

    var weatherFormatPattern: Pattern =
        Pattern.compile("%([a-z_]+)(\\d*)(?:\\$\\(([\\.\\+\\-\\*\\/\\^\\d]+)\\))?")

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
                runQuery(context, path, pathType, format, url, weatherArea)
            }
        }.start()
    }

    /** Runs on a background thread: fetches [url] and prints (or, for [weatherArea], broadcasts) the result. */
    private fun runQuery(
        context: Context,
        path: String?,
        pathType: StoreableValue.Type?,
        format: String?,
        url: String,
        weatherArea: Boolean
    ) {
        if (!SystemUtils.hasInternetAccess()) {
            output(R.string.no_internet, context, weatherArea)
            return
        }

        try {
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:15.0) Gecko/20100101 Firefox/15.0.1"
                )
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!checkResponse(context, response, weatherArea)) return

            val inputStream = (response.body ?: throw java.io.IOException("empty response body")).byteStream()
            val f = format ?: defaultFormat

            when {
                weatherArea -> showWeather(context, FileTuils.inputStreamToString(inputStream), url)
                pathType == StoreableValue.Type.xpath -> showXPathResult(context, inputStream, path, f, url)
                else -> showJsonPathResult(context, inputStream, path, f, url)
            }
        } catch (e: Exception) {
            output(e.toString(), context, weatherArea)
            LogTuils.toFile(e)
            LogTuils.log(e)
        }
    }

    /** True if [response] has a usable body; otherwise reports the error (or asks the weather area to retry later). */
    private fun checkResponse(context: Context, response: Response, weatherArea: Boolean): Boolean {
        if (response.code == 429 && weatherArea) {
            LocalBroadcastManager.getInstance(context.getApplicationContext())
                .sendBroadcast(Intent(UIManager.ACTION_WEATHER_DELAY))
            return false
        }
        if (response.isSuccessful) return true

        val message = context.getString(R.string.internet_error) + Tuils.SPACE + response.code
        if (weatherArea) {
            val i = Intent(UIManager.ACTION_WEATHER)
            i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, message)
            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i)
        } else {
            output(message, context, false)
        }
        return false
    }

    private fun showWeather(context: Context, json: String, url: String) {
        var o = fillWeatherFormat(json)
        o = replaceLinkColorReplace(context, o, url)
        o = removeDelimiter(o)

        val i = Intent(UIManager.ACTION_WEATHER)
        i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, o)
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i)
    }

    /**
     * Replaces each `%name[n][$(expr)]` field of [weatherFormat] with the n-th (default 1st) value of
     * `"name"` in [json], optionally passed through the arithmetic expression `expr`.
     */
    private fun fillWeatherFormat(json: String): CharSequence {
        var o: CharSequence = ViewTuils.span(weatherFormat, weatherColor)

        val m = weatherFormatPattern.matcher(weatherFormat)
        while (m.find()) {
            val name = m.group(1)
            val occurrence = m.group(2)?.ifEmpty { null }?.toInt() ?: 1
            val converter = m.group(3)

            var value = nthJsonValue(json, name, occurrence) ?: continue

            if (converter != null && converter.isNotEmpty()) {
                try {
                    value = String.format("%.2f", MathTuils.textCalculus(value.toDouble(), converter))
                } catch (e: Exception) {
                    LogTuils.log(e)
                }
            }

            o = TextUtils.replace(
                o, arrayOf<String?>(m.group(0)), arrayOf<String>(delimiterStart + value + delimiterEnd)
            )
        }
        return o
    }

    /** The [n]-th (1-based) string or number value of the key [name] in [json], or null if there are fewer. */
    private fun nthJsonValue(json: String, name: String?, n: Int): String? {
        val m = Pattern.compile("\"" + name + "\":(?:\"([^\"]+)\"|(-?\\d+\\.?\\d*))").matcher(json)
        var c = 1
        while (m.find()) {
            if (c == n) return m.group(1)?.ifEmpty { null } ?: m.group(2)
            c++
        }
        return null
    }

    private fun showXPathResult(context: Context, inputStream: InputStream, path: String?, format: String, url: String) {
        val cleaner = HtmlCleaner()
        cleaner.getProperties().setOmitComments(true)

        val nodes = cleaner.clean(inputStream).evaluateXPath(path)
        if (nodes.size == 0) {
            OutputTuils.sendOutput(context, R.string.no_result)
            return
        }

        var output: CharSequence? = ViewTuils.span(Tuils.EMPTYSTRING, outputColor)
        for (c in nodes.indices) {
            val node = nodes[c] as TagNode

            var copy: CharSequence = ViewTuils.span(format, outputColor)
            copy = Companion.replaceAllAttributesString(
                copy,
                node.getAttributes().entries as MutableSet<MutableMap.MutableEntry<String?, String?>?>
            )
            copy = replaceTagNameString(copy, node.getName(), node.getAttributes())
            copy = replaceNodeValue(copy, node.getText().toString())

            output = appendResult(output, finishFormat(context, copy, url), c)
        }

        output(output, context, false, TerminalManager.CATEGORY_NO_COLOR)
    }

    private fun showJsonPathResult(context: Context, inputStream: InputStream, path: String?, format: String, url: String) {
        val o = JsonPath.read<Any>(inputStream, path)

        when (o) {
            // a single JSON object
            is MutableMap<*, *> -> {
                val copy = formatJsonObject(ViewTuils.span(format, outputColor), o as MutableMap<String?, Any?>)
                output(finishFormat(context, copy, url), context, false, TerminalManager.CATEGORY_NO_COLOR)
            }
            // an array of JSON objects
            is MutableList<*> -> {
                val a = o as JSONArray

                var output: CharSequence? = ViewTuils.span(Tuils.EMPTYSTRING, outputColor)
                for (c in a.indices) {
                    val copy = formatJsonObject(ViewTuils.span(format, outputColor), a.get(c) as LinkedHashMap<String?, Any?>)
                    output = appendResult(output, finishFormat(context, copy, url), c)
                }

                output(output, context, false, TerminalManager.CATEGORY_NO_COLOR)
            }
            is String -> output(ViewTuils.span(o.toString(), outputColor), context, false, TerminalManager.CATEGORY_NO_COLOR)
            else -> OutputTuils.sendOutput(context, o.toString(), outputColor)
        }
    }

    private fun formatJsonObject(format: CharSequence, obj: MutableMap<String?, Any?>): CharSequence {
        val copy = Companion.replaceAllAttributesObject(
            format,
            obj.entries as MutableSet<MutableMap.MutableEntry<String?, Any?>?>
        )
        return replaceTagNameObject(copy, null, obj)
    }

    /** The formatting steps every result goes through last: newlines, links/colors/replacements, delimiters. */
    private fun finishFormat(context: Context, copy: CharSequence, url: String): CharSequence {
        var s = replaceNewline(copy)
        s = replaceLinkColorReplace(context, s, url)
        return removeDelimiter(s)
    }

    /** Appends a non-blank [result] to [output], separated by a blank line from the previous one. */
    private fun appendResult(output: CharSequence?, result: CharSequence, index: Int): CharSequence? {
        if (result.toString().trim { it <= ' ' }.isEmpty()) return output
        return TextUtils.concat(output, if (index != 0) Tuils.NEWLINE + Tuils.NEWLINE else Tuils.EMPTYSTRING, result)
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
                val intent_tag: String? = intent.getStringExtra(TAG_NAME)
                // null when the extra is missing or isn't a known type (e.g. `htmlextract ls` with no type)
                val intent_type = StoreableValue.Type.entries.find { it.name == intent_tag }
                val intent_value = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)

                when (action) {
                    ACTION_ADD -> add(context, intent_tag, intent_type, intent_value, intent_id)
                    ACTION_RM -> remove(context, intent_id)
                    ACTION_EDIT -> edit(context, intent_id, intent_value)
                    ACTION_LS -> list(context, intent_type)
                    ACTION_QUERY -> query(context, intent, intent_id, intent_value)
                    ACTION_WEATHER -> {
                        val url = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE) ?: return
                        query(context, weatherFormat, url)
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

        try {
            val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            for (type in StoreableValue.Type.values()) {
                val records = loadJsonList<StoreableValueRecord>(prefs, type.name)
                val list = getListFromType(type)
                for (record in records) list.add(StoreableValue(record.id, record.value, type))
            }
        } catch (e: Exception) {
            LogTuils.log(e)
            Log.e(TAG, e.toString())
        }
    }

    @Serializable
    data class StoreableValueRecord(val id: Int, val value: String?)

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

        fun remove(context: Context) {
            val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val records = loadJsonList<StoreableValueRecord>(prefs, type.name)
            val removed = records.removeIf { it.id == id }
            if (removed) {
                saveJsonList(prefs, type.name, records)
            } else {
                OutputTuils.sendOutput(context, R.string.id_notfound, Color.RED)
            }
        }

        fun edit(context: Context, newExpression: String?) {
            val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val records = loadJsonList<StoreableValueRecord>(prefs, type.name)
            val index = records.indexOfFirst { it.id == id }
            if (index < 0) {
                OutputTuils.sendOutput(context, R.string.id_notfound, Color.RED)
                return
            }

            records[index] = StoreableValueRecord(id, newExpression)
            saveJsonList(prefs, type.name, records)
            this.value = newExpression
        }

        companion object {
            fun create(
                values: MutableList<StoreableValue>,
                context: Context,
                type: Type,
                path: String?,
                id: Int
            ): StoreableValue? {
                for (c in values.indices) {
                    if (values.get(c).id == id) {
                        OutputTuils.sendOutput(context, R.string.id_already)
                        return null
                    }
                }

                val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
                val records = loadJsonList<StoreableValueRecord>(prefs, type.name)
                records.add(StoreableValueRecord(id, path))
                saveJsonList(prefs, type.name, records)

                return StoreableValue(id, path, type)
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
                if (category != Int.Companion.MAX_VALUE) OutputTuils.sendOutput(context, s, type = category)
                else OutputTuils.sendOutput(context, s)
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
                        StringTuils.removeUnncesarySpaces(
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
                    if (element == null) continue
                    var temp = first
                    temp = attributeName.matcher(temp).replaceAll(element.key)
                    temp = attributeValue.matcher(temp)
                        .replaceAll(StringTuils.removeUnncesarySpaces(element.value.orEmpty().trim { it <= ' ' }))
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
            nodeValue = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(nodeValue, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(nodeValue).toString()
            }
            return TextUtils.replace(
                original,
                arrayOf<String?>(nodeValuePattern),
                arrayOf<CharSequence>(
                    delimiterStart + StringTuils.removeUnncesarySpaces(nodeValue)
                        .trim { it <= ' ' } + delimiterEnd))
        }

        fun replaceNewline(original: CharSequence): CharSequence {
            var original = original
            var before: Int
            do {
                before = original.length
                original = TextUtils.replace(
                    original,
                    arrayOf<String>(StringTuils.patternNewline.pattern()),
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
                        arrayOf<CharSequence>(ViewTuils.span(m.group(2), cl))
                    )
                } catch (e: Exception) {
                    OutputTuils.sendOutput(
                        context,
                        context.getString(R.string.output_invalidcolor) + ": " + m.group(1)
                    )
                }
            }

            m = linkPattern.matcher(original)
            while (m.find()) {
                var text = m.group(1) ?: continue

                //            fix relative links
                if (text.startsWith("/")) {
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
                val replaceGroups = m.group(1) ?: continue
                var text = m.group(2) ?: continue

                val groups = replaceGroups.split("]".toRegex()).dropLastWhile { it.isEmpty() }
                for (group in groups) {
                    val split = group.replace("[\\[\\]]".toRegex(), Tuils.EMPTYSTRING)
                        .split(optionalValueSeparator.toRegex())
                        .dropLastWhile { it.isEmpty() }
                    // needs both a pattern and its replacement
                    if (split.size < 2) continue

                    text = text.replace(split[0].toRegex(), split[1])
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
