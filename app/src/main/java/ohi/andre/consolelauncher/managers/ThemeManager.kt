package ohi.andre.consolelauncher.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Created by francescoandreuzzi on 17/02/2018.
 */
class ThemeManager(
    private val client: OkHttpClient,
    private val context: Context,
    private val reloadable: Reloadable,
) {

    private val parser = Pattern.compile("(<SUGGESTIONS>.+</SUGGESTIONS>).*(<THEME>.+</THEME>)", Pattern.DOTALL)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_APPLY -> {
                    val name = intent.getStringExtra(NAME) ?: return
                    // name needs to be the absolute path
                    if (name.endsWith(".zip")) apply(File(name)) else apply(name)
                }
                ACTION_REVERT -> revert()
                ACTION_STANDARD -> standard()
            }
        }
    }

    init {
        val filter = IntentFilter()
        filter.addAction(ACTION_APPLY)
        filter.addAction(ACTION_REVERT)
        filter.addAction(ACTION_STANDARD)

        LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(receiver, filter)
    }

    fun dispose() {
        LocalBroadcastManager.getInstance(context.applicationContext).unregisterReceiver(receiver)
    }

    fun apply(themeName: String) {
        Thread {
            if (!SystemUtils.hasInternetAccess()) {
                OutputTuils.sendOutput(context, R.string.no_internet, Color.RED)
                return@Thread
            }

            val url = "https://tui.tarunshankerpandey.com/show_data.php?data_type=xml&theme_id=$themeName"
            val builder = Request.Builder().url(url).get()

            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: IOException) {
                OutputTuils.sendOutput(context, e.toString())
                return@Thread
            }

            if (!response.isSuccessful) {
                OutputTuils.sendOutput(context, "theme url unavailable", Color.RED)
            } else {
                val string = try {
                    response.body?.string() ?: Tuils.EMPTYSTRING
                } catch (e: IOException) {
                    Tuils.EMPTYSTRING
                }

                if (string.isEmpty()) {
                    OutputTuils.sendOutput(context, R.string.theme_not_found)
                    return@Thread
                }

                val m = parser.matcher(string)
                if (m.find()) {
                    val suggestions = m.group(1)
                    val theme = m.group(2)

                    applyTheme(theme, suggestions, true, themeName)
                } else {
                    OutputTuils.sendOutput(context, R.string.theme_not_found)
                    return@Thread
                }
            }
        }.start()
    }

    fun apply(zip: File) {
    }

    private fun applyTheme(theme: String, suggestions: String, keepOld: Boolean, themeName: String) {
        var theme = theme
        var suggestions = suggestions

        var colorMatcher = colorParser.matcher(theme)
        while (colorMatcher.find()) {
            theme = Pattern.compile(Pattern.quote(colorMatcher.group())).matcher(theme).replaceAll(toHexColor(colorMatcher.group()))
        }

        colorMatcher = colorParser.matcher(suggestions)
        while (colorMatcher.find()) {
            suggestions = Pattern.compile(Pattern.quote(colorMatcher.group())).matcher(suggestions).replaceAll(toHexColor(colorMatcher.group()))
        }

        if (keepOld) {
            backupPrefs(XMLPrefsRoot.THEME)
            backupPrefs(XMLPrefsRoot.SUGGESTIONS)
        }

        try {
            writeValues(XMLPrefsRoot.THEME, parseXmlValues(theme))
            writeValues(XMLPrefsRoot.SUGGESTIONS, parseXmlValues(suggestions))

            reloadable.addMessage(context.getString(R.string.theme_applied) + Tuils.SPACE + themeName, null)
            reloadable.reload()
        } catch (e: Exception) {
            OutputTuils.sendOutput(context, R.string.output_error)
        }
    }

    private fun revert() {
        restorePrefs(XMLPrefsRoot.THEME)
        restorePrefs(XMLPrefsRoot.SUGGESTIONS)
        reloadable.reload()
    }

    private fun standard() {
        backupPrefs(XMLPrefsRoot.THEME)
        backupPrefs(XMLPrefsRoot.SUGGESTIONS)

        XMLPrefsRoot.THEME.getValues().clear()
        XMLPrefsRoot.SUGGESTIONS.getValues().clear()

        reloadable.addMessage(context.getString(R.string.theme_applied) + Tuils.SPACE + "standard", null)
        reloadable.reload()
    }

    private fun parseXmlValues(xml: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = document.documentElement.getElementsByTagName("*")

        val map = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val valueAttr = node.attributes.getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE) ?: continue
            map[node.nodeName] = valueAttr.nodeValue
        }
        return map
    }

    private fun writeValues(root: XMLPrefsRoot, values: Map<String, String>) {
        val list = root.getValues()
        list.clear()
        for ((key, value) in values) list.add(key, value)
    }

    private fun backupPrefs(root: XMLPrefsRoot) {
        val live = context.getSharedPreferences(root.path, Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences(root.path + BACKUP_SUFFIX, Context.MODE_PRIVATE)

        val editor = backup.edit()
        editor.clear()
        for ((key, value) in live.all) {
            if (value is String) editor.putString(key, value)
        }
        editor.apply()
    }

    private fun restorePrefs(root: XMLPrefsRoot) {
        val backup = context.getSharedPreferences(root.path + BACKUP_SUFFIX, Context.MODE_PRIVATE)

        val list = root.getValues()
        list.clear()
        for ((key, value) in backup.all) {
            if (value is String) list.add(key, value)
        }
    }

    // rgba(255,87,34,1)
    private val colorParser = Pattern.compile("rgba\\([\\s]*(\\d+),[\\s]*(\\d+),[\\s]*(\\d+),[\\s]*(\\d.*\\d*)[\\s]*\\)")

    private fun toHexColor(color: String): String {
        val m = colorParser.matcher(color)
        if (m.find()) {
            val red = m.group(1).toInt()
            val green = m.group(2).toInt()
            val blue = m.group(3).toInt()
            val alpha = m.group(4).toFloat()

            var redHex = Integer.toHexString(red)
            if (redHex.length == 1) redHex = "0$redHex"

            var greenHex = Integer.toHexString(green)
            if (greenHex.length == 1) greenHex = "0$greenHex"

            var blueHex = Integer.toHexString(blue)
            if (blueHex.length == 1) blueHex = "0$blueHex"

            var alphaHex = Integer.toHexString(alpha.toInt())
            if (alphaHex.length == 1) alphaHex = "0$alphaHex"

            return "#" + (if (alpha == 1f) Tuils.EMPTYSTRING else alphaHex) + redHex + greenHex + blueHex
        } else return Tuils.EMPTYSTRING
    }

    companion object {
        @JvmField
        var ACTION_APPLY: String = BuildConfig.APPLICATION_ID + ".theme_apply"
        @JvmField
        var ACTION_REVERT: String = BuildConfig.APPLICATION_ID + ".theme_revert"
        @JvmField
        var ACTION_STANDARD: String = BuildConfig.APPLICATION_ID + ".theme_standard"

        @JvmField
        var NAME: String = "name"

        private const val BACKUP_SUFFIX = ".old"
    }
}
