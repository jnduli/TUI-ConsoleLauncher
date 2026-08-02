package ohi.andre.consolelauncher.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.launchable.AliasLauncher
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.regex.Matcher
import java.util.regex.Pattern

class AliasManager(private val context: Context) {

    private val aliases: MutableList<AliasLauncher> = ArrayList()

    private val paramMarker: String = XMLPrefsManager.get(Behavior.alias_param_marker)
    private val parameterPattern: Pattern = Pattern.compile(Pattern.quote(paramMarker))
    private val paramSeparator: String = XMLPrefsManager.get(Behavior.alias_param_separator)
    private val aliasLabelFormat: String = XMLPrefsManager.get(Behavior.alias_content_format)
    private val replaceAllMarkers: Boolean = XMLPrefsManager.getBoolean(Behavior.alias_replace_all_markers)

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_ADD -> add(context, intent.getStringExtra(NAME)!!, intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)!!)
                ACTION_RM -> remove(context, intent.getStringExtra(NAME)!!)
                ACTION_LS -> Tuils.sendOutput(context, printAliases())
            }
        }
    }

    init {
        // kept for parity with the IntentFilter the receiver was originally built with;
        // the receiver itself is never registered (pre-existing, see dispose()).
        val filter = IntentFilter()
        filter.addAction(ACTION_ADD)
        filter.addAction(ACTION_LS)
        filter.addAction(ACTION_RM)

        reload()
    }

    fun printAliases(): String {
        var output = Tuils.EMPTYSTRING
        for (a in aliases) {
            output = output + a.aliasName + " --> " + a.commandTemplate + Tuils.NEWLINE
        }

        return output.trim()
    }

    //    [0] = aliasValue
    //    [1] = aliasName
    //    [2] = residualString
    fun getAlias(alias: String, supportSpaces: Boolean): Array<String?> {
        if (!supportSpaces) {
            return arrayOf(getALias(alias), alias, Tuils.EMPTYSTRING)
        }

        var args = Tuils.EMPTYSTRING
        var currentAlias = alias
        while (true) {
            val aliasValue = getALias(currentAlias)
            if (aliasValue != null) return arrayOf(aliasValue, currentAlias, args)

            val index = currentAlias.lastIndexOf(Tuils.SPACE)
            if (index == -1) return arrayOf(null, null, currentAlias)

            args = (currentAlias.substring(index + 1) + Tuils.SPACE + args).trim()
            currentAlias = currentAlias.substring(0, index)
        }
    }

    //    this prevents some errors related to the % sign
    private val SECURITY_REPLACEMENT = "{#@"
    private val securityPattern: Pattern = Pattern.compile(Pattern.quote(SECURITY_REPLACEMENT))

    fun format(aliasValue: String, params: String): String {
        val trimmedParams = params.trim()
        if (trimmedParams.isEmpty()) return aliasValue

        val before = aliasValue.length
        var value = parameterPattern.matcher(aliasValue).replaceAll(SECURITY_REPLACEMENT)
        val replaced = (value.length - before) / Math.abs(SECURITY_REPLACEMENT.length - paramMarker.length)

        val split = Regex(Pattern.quote(paramSeparator)).split(trimmedParams, replaced)

        for (s in split) {
            value = securityPattern.matcher(value).replaceFirst(s)
        }

        if (replaceAllMarkers) value = securityPattern.matcher(value).replaceAll(split[0])

        return value
    }

    private fun getALias(name: String): String? {
        for (a in aliases) {
            if (name == a.aliasName) return a.commandTemplate
        }

        return null
    }

    private fun removeAlias(name: String): Boolean {
        val iterator = aliases.iterator()
        while (iterator.hasNext()) {
            if (name == iterator.next().aliasName) {
                iterator.remove()
                return true
            }
        }

        return false
    }

    private val pv: Pattern = Pattern.compile("%v", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val pa: Pattern = Pattern.compile("%a", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    fun formatLabel(aliasName: String, aliasValue: String): String {
        var a = aliasLabelFormat
        a = Tuils.patternNewline.matcher(a).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))
        a = pv.matcher(a).replaceAll(Matcher.quoteReplacement(aliasValue))
        a = pa.matcher(a).replaceAll(Matcher.quoteReplacement(aliasName))
        return a
    }

    fun reload() {
        aliases.clear()

        val root = Tuils.getFolder(this.context) ?: return
        val file = File(root, PATH)

        try {
            if (!file.exists()) file.createNewFile()

            BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
                var line: String?
                readLoop@ while (reader.readLine().also { line = it } != null) {
                    val splatted = Pattern.compile("=").split(line!!)
                    if (splatted.size < 2) continue@readLoop

                    var name = splatted[0]
                    var value = Tuils.EMPTYSTRING

                    for (c in 1 until splatted.size) {
                        value += splatted[c]
                        if (c != splatted.size - 1) value += "="
                    }

                    name = name.trim()
                    value = value.trim()

                    if (name.equals(value, ignoreCase = true)) {
                        Tuils.sendOutput(
                            Color.RED, context,
                            context.getString(R.string.output_notaddingalias1) + Tuils.SPACE + name + Tuils.SPACE + context.getString(
                                R.string.output_notaddingalias2
                            )
                        )
                    } else if (value.startsWith(name + Tuils.SPACE)) {
                        Tuils.sendOutput(
                            Color.RED, context,
                            context.getString(R.string.output_notaddingalias1) + Tuils.SPACE + name + Tuils.SPACE + context.getString(
                                R.string.output_notaddingalias3
                            )
                        )
                    } else {
                        aliases.add(AliasLauncher.create(name, value, parameterPattern))
                    }
                }
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    fun dispose() {
        LocalBroadcastManager.getInstance(context.applicationContext).unregisterReceiver(receiver)
    }

    fun add(context: Context, name: String, value: String) {
        for (a in aliases) {
            if (name == a.aliasName) {
                Tuils.sendOutput(context, R.string.unavailable_name)
                return
            }
        }

        try {
            FileOutputStream(File(Tuils.getFolder(context), PATH), true).use { fos ->
                fos.write((Tuils.NEWLINE + name + "=" + value).toByteArray())
            }

            aliases.add(AliasLauncher.create(name, value, parameterPattern))
        } catch (e: Exception) {
            Tuils.sendOutput(context, e.toString())
        }
    }

    fun remove(context: Context, name: String) {
        reload()

        if (!removeAlias(name)) {
            Tuils.sendOutput(context, R.string.invalid_name)
            return
        }

        try {
            val inputFile = File(Tuils.getFolder(context), PATH)
            val tempFile = File(Tuils.getFolder(context), PATH + "2")

            BufferedReader(FileReader(inputFile)).use { reader ->
                BufferedWriter(FileWriter(tempFile)).use { writer ->
                    val prefix = "$name="
                    var line: String?
                    readLoop@ while (reader.readLine().also { line = it } != null) {
                        if (line!!.startsWith(prefix)) continue@readLoop
                        writer.write(line + Tuils.NEWLINE)
                    }
                }
            }

            tempFile.renameTo(inputFile)
        } catch (e: Exception) {
            Tuils.sendOutput(context, e.toString())
        }
    }

    fun getAliases(excludeEmtpy: Boolean): List<AliasLauncher> {
        val l = ArrayList(aliases)
        if (excludeEmtpy) {
            for (c in l.indices) {
                if (l[c].aliasName.isEmpty()) {
                    l.removeAt(c)
                    break
                }
            }
        }

        return l
    }

    companion object {
        const val ACTION_LS = BuildConfig.APPLICATION_ID + ".alias_ls"
        const val ACTION_ADD = BuildConfig.APPLICATION_ID + ".alias_add"
        const val ACTION_RM = BuildConfig.APPLICATION_ID + ".alias_rm"

        const val NAME = "name"

        const val PATH = "alias.txt"
    }
}
