package ohi.andre.consolelauncher.managers.launchable

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.util.regex.Pattern

/**
 * A user-defined alias/text-command shortcut. Unlike [AppLauncher]/[WebLauncher], it has no
 * [android.content.Intent] representation: "launching" one means expanding [commandTemplate]
 * with the caller-supplied args and re-entering the command pipeline (see
 * MainManager.AliasTrigger), not starting an activity.
 */
@Parcelize
@Serializable
@XmlSerialName("alias", "", "")
data class AliasLauncher(
    val aliasName: String,
    val commandTemplate: String,
    val isParametrized: Boolean = false,
    override var launchTimes: Int = 0
) : Launchable, android.os.Parcelable {

    @kotlinx.serialization.Transient
    override var componentName: ComponentName? = null

    override fun names(): Set<String> = setOf(aliasName)

    override fun name(): String = aliasName

    override fun storageKey(): String = aliasName

    override fun toIntent(context: Context): Intent =
        throw UnsupportedOperationException("AliasLauncher has no Intent; it re-enters the command pipeline instead")

    companion object {
        @JvmStatic
        fun create(name: String, value: String, parameterPattern: Pattern): AliasLauncher {
            return AliasLauncher(name, value, parameterPattern.matcher(value).find())
        }
    }
}
