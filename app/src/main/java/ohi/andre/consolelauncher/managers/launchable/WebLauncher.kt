package ohi.andre.consolelauncher.managers.launchable

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import ohi.andre.consolelauncher.WebActivity

@Parcelize
@Serializable
@XmlSerialName("web", "", "")
data class WebLauncher(val name: String, val url: String, override var launchTimes: Int = 0) : Launchable, android.os.Parcelable {

    @kotlinx.serialization.Transient
    override var componentName: ComponentName? = ComponentName("ohi.andre.consolelauncher", "ohi.andre.consolelauncher.WebActivity")

    override fun toIntent(context: Context): Intent {
        val intent = Intent(context, WebActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                putExtra(WebActivity.URL_EXTRA, url)
        }
        return intent
    }

    fun launchWithQuery(context: Context, query: String) {
        val resolvedUrl = url.replace("%s", Uri.encode(query))
        context.startActivity(
            Intent(context, WebActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(WebActivity.URL_EXTRA, resolvedUrl)
        )
    }

    override fun names() : Set<String>{
        return setOf(name)
    }

    override fun name(): String {
        return name
    }

    override fun storageKey(): String = name
}
