package ohi.andre.consolelauncher.managers.launchable

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.OutputTuils

@Parcelize
@Serializable
@XmlSerialName("app", "", "")
data class AppLauncher(val packageName: String, val activityName: String, val label: String, var shortcut: String = "", override var launchTimes: Int = 0): Launchable, android.os.Parcelable {

    @Contextual
    override var componentName: ComponentName? = ComponentName(packageName, activityName)

    override fun toIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(componentName).setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val msg = "--> $packageName:$activityName:$label\n"
        val text = SpannableString(msg)
        val outputColor = XMLPrefsManager.getColor(Theme.output_color)
        text.setSpan(
            ForegroundColorSpan(outputColor),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        OutputTuils.sendOutput(context, text, type = TerminalManager.CATEGORY_OUTPUT)
        return intent
    }

    override fun name(): String {
        return label
    }

    override fun names(): Set<String> {
        return setOf(label, packageName)
    }

    override fun storageKey(): String = "$packageName-$activityName"

    fun format(info: PackageInfo): String {
        val installDate = TimeManager.instance.replace("%t0", info.firstInstallTime, Int.MAX_VALUE)
        val header = HEADER_TEMPLATE.format(info.packageName, info.versionCode, info.versionName, launchTimes, installDate)

        return header +
            section("Activities", info.activities?.map { it.name.replace(info.packageName, "") } ?: emptyList(), "\n") +
            section("Services", info.services?.map { it.name.replace(info.packageName, "") } ?: emptyList(), "\n") +
            section("Receivers", info.receivers?.map { it.name.replace(info.packageName, "") } ?: emptyList(), "\n") +
            section("Permissions", info.requestedPermissions?.map { it.substringAfterLast(".") } ?: emptyList(), ", ", trailingBlankLine = false)
    }

    companion object {
        private const val HEADER_TEMPLATE = "%s\nvrs: %s - %s\n\nlaunched_times: %s\n\nInstall: %s\n\n"

        private fun section(label: String, items: List<String>, separator: String, trailingBlankLine: Boolean = true): String {
            if (items.isEmpty()) return ""
            val body = "$label: \n" + items.joinToString(separator)
            return if (trailingBlankLine) "$body\n\n" else body
        }
    }
}
