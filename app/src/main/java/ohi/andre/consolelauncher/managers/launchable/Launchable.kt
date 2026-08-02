package ohi.andre.consolelauncher.managers.launchable

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.util.Log
import it.andreuzzi.comparestring2.StringableObject
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.util.Locale

private val TAG: String = "Launchable"

enum class Visibility {
    SHOWN, HIDDEN
}

@Serializable
sealed interface Launchable: Comparable<Launchable>, StringableObject, Parcelable {
    var launchTimes: Int
    var componentName: ComponentName?

    fun launch(context: Context) {
        launchTimes++
        val intent = toIntent(context)
        context.startActivity(intent)
    }

    fun toIntent(context: Context): Intent
    fun names(): Set<String>
    fun name(): String
    fun storageKey(): String

    override fun getString(): String = name()
    override fun getLowercaseString(): String = name().lowercase(Locale.getDefault())

    override fun compareTo(other: Launchable): Int {
        return getLowercaseString().compareTo(other.getLowercaseString())
    }

    companion object {
        fun listFromXml(xml: String): List<Launchable> {
            return try {
                XML.decodeFromString<LaunchableList>(xml).launchables
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding launchables list", e)
                emptyList()
            }
        }

        fun listToXml(launchables: List<Launchable>): String {
            return XML.encodeToString(LaunchableList(launchables))
        }

        fun labelList(infos: List<Launchable>, sort: Boolean): MutableList<String> {
            val labels = infos.mapTo(ArrayList()) { it.name() }
            if (sort) labels.sort()
            return labels
        }
    }
}

@Serializable
@XmlSerialName("launchables", "", "")
data class LaunchableList(val launchables: List<Launchable>)
