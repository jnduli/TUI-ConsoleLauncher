package ohi.andre.consolelauncher.tuils

import android.app.admin.DevicePolicyManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.tuils.stuff.FakeLauncherActivity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object SystemUtils {

    @JvmStatic
    fun hasNotificationAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat.orEmpty().split(":").any { name ->
            ComponentName.unflattenFromString(name)?.packageName == BuildConfig.APPLICATION_ID
        }
    }

    @JvmStatic
    fun resetPreferredLauncherAndOpenChooser(context: Context) {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, FakeLauncherActivity::class.java)
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)

        val selector = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(selector)

        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }

    @JvmStatic
    fun openSettingsPage(c: Context, packageName: String?) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        c.startActivity(intent)
    }

    @JvmStatic
    fun requestAdmin(component: ComponentName?, explanation: String?): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
        }

    @JvmStatic
    fun webPage(url: String?): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    @JvmStatic
    fun isMyLauncherDefault(packageManager: PackageManager): Boolean {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val activities = ArrayList<ComponentName>()

        // You can use name of your package here as third argument
        packageManager.getPreferredActivities(arrayListOf(filter), activities, null)

        return activities.any { it.packageName == BuildConfig.APPLICATION_ID }
    }

    @JvmStatic
    fun hasInternetAccess(): Boolean {
        return try {
            val connection = URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection
            connection.responseCode == 204 && connection.contentLength == 0
        } catch (e: IOException) {
            Log.e(Tuils.TAG, e.toString())
            false
        }
    }

    @JvmStatic
    fun getTextFromClipboard(context: Context): String? {
        return try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }
}
