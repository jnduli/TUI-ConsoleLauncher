package ohi.andre.consolelauncher.managers.notifications.reply

import android.annotation.TargetApi
import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.serialization.Serializable
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.SharedPrefsXMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.classes.loadJsonList
import ohi.andre.consolelauncher.managers.xml.classes.saveJsonList
import ohi.andre.consolelauncher.managers.xml.options.Reply
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Created by francescoandreuzzi on 17/01/2018.
 */
class ReplyManager(context: Context) : XMLPrefsElement {

    private val context: Context = context

    @Serializable
    data class BoundAppRecord(val packageName: String, val applicationId: Int)

    private var notificationWears: MutableSet<NotificationWear>? = null
    private var values: XMLPrefsList
    private var receiver: BroadcastReceiver? = null
    private var enabled: Boolean

    init {
        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH

        val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
        values = SharedPrefsXMLPrefsList(prefs)

        if (enabled) {
            notificationWears = HashSet()
            instance = this

            XMLPrefsManager.seedSharedPrefsDefaults(prefs, Reply.values().toList())
            enabled = values.get(Reply.reply_enabled)?.value?.toBoolean() == true

            if (!enabled) {
                notificationWears = null
                boundApps = null
            } else {
                load()

                val filter = IntentFilter()
                filter.addAction(ACTION)
                filter.addAction(ACTION_UPDATE)
                filter.addAction(ACTION_LS)

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent) {
                        when (intent.action) {
                            ACTION -> {
                                val app = intent.getStringExtra(ID)
                                val what = intent.getStringExtra(WHAT)

                                // ID is either a bound app's numeric id or its package name
                                val id: Int = app?.toIntOrNull() ?: findApp(app)?.applicationId ?: run {
                                    OutputTuils.sendOutput(
                                        receiverContext,
                                        receiverContext.getString(R.string.reply_app_not_found) + Tuils.SPACE + app,
                                    )
                                    return
                                }

                                if (what == null) {
                                    check(id)
                                } else {
                                    if (id == -1) return
                                    replyTo(context, id, what)
                                }
                            }
                            ACTION_UPDATE -> load()
                            ACTION_LS -> ls(receiverContext)
                        }
                    }
                }

                this.receiver = receiver
                LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(receiver, filter)
            }
        }
    }

    private fun load() {
        val list = boundApps ?: ArrayList<BoundApp>().also { boundApps = it }
        list.clear()

        val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
        val records = loadJsonList<BoundAppRecord>(prefs, BOUND_APPS_KEY)

        val mgr = context.packageManager
        for (record in records) {
            val label = try {
                mgr.getApplicationInfo(record.packageName, 0).loadLabel(mgr).toString()
            } catch (e: Exception) {
                continue
            }
            list.add(BoundApp(record.applicationId, record.packageName, label))
        }

        nextUsableId = nextUsableId()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun onNotification(notification: StatusBarNotification, text: CharSequence) {
        if (!enabled) return

        val app = findApp(notification.packageName) ?: return
        val w = extractWearNotification(notification) ?: return

        val old = findNotificationWear(app)

        if (old != null && (w.pendingIntent == null || w.remoteInputs.isNullOrEmpty())) return
        if (old != null) notificationWears?.remove(old)

        w.text = text
        w.app = app

        notificationWears?.add(w)
    }

    private fun replyTo(context: Context, applicationId: Int, what: String) {
        if (!enabled) return

        val app = findApp(applicationId)
        if (app == null) {
            OutputTuils.sendOutput(context, context.getString(R.string.reply_id_not_found) + Tuils.SPACE + applicationId)
            return
        }

        val wear = findNotificationWear(applicationId)
        if (wear != null) replyTo(context, wear, what)
        else OutputTuils.sendOutput(context, R.string.reply_notification_not_found)
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private fun replyTo(context: Context, notificationWear: NotificationWear, what: String) {
        val remoteInputs = notificationWear.remoteInputs
        val localBundle = notificationWear.bundle

        val i = Intent(PrivateIOReceiver.ACTION_REPLY)
        i.putExtra(PrivateIOReceiver.BUNDLE, localBundle)
        i.putExtra(PrivateIOReceiver.REMOTE_INPUTS, remoteInputs)
        i.putExtra(PrivateIOReceiver.TEXT, what)
        i.putExtra(PrivateIOReceiver.PENDING_INTENT, notificationWear.pendingIntent)
        i.putExtra(PrivateIOReceiver.ID, notificationWear.id)
        i.putExtra(PrivateIOReceiver.CURRENT_ID, PrivateIOReceiver.currentId)

        LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(i)
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private fun extractWearNotification(statusBarNotification: StatusBarNotification): NotificationWear {
        val notificationWear = NotificationWear()

        val wearableExtender = Notification.WearableExtender(statusBarNotification.notification)
        for (action in wearableExtender.actions) {
            val rs = action.remoteInputs
            if (rs != null && rs.isNotEmpty()) {
                notificationWear.remoteInputs = rs
                // Actually I assume that there's only one action
                notificationWear.pendingIntent = action.actionIntent
                break
            }
        }

        notificationWear.bundle = statusBarNotification.notification.extras
        notificationWear.id = statusBarNotification.id

        return notificationWear
    }

    private fun findApp(applicationId: Int): BoundApp? = boundApps?.find { it.applicationId == applicationId }

    private fun findApp(pkg: String?): BoundApp? = boundApps?.find { it.packageName == pkg }

    private fun findNotificationWear(bapp: BoundApp): NotificationWear? =
        notificationWears?.find { it.app?.packageName == bapp.packageName }

    private fun findNotificationWear(id: Int): NotificationWear? =
        notificationWears?.find { it.app?.applicationId == id }

    fun dispose(context: Context) {
        try {
            receiver?.let { LocalBroadcastManager.getInstance(context.applicationContext).unregisterReceiver(it) }
        } catch (e: Exception) {
        }

        notificationWears?.clear()
        notificationWears = null

        boundApps?.clear()
        boundApps = null

        values.clear()

        instance = null
    }

    override fun getValues(): XMLPrefsList = values

    override fun write(c: Context, save: XMLPrefsSave, value: String) {
        values.add(save.label(), value)
    }

    override fun delete(): Array<String?>? = null

    override fun path(): String = PATH

    fun check(id: Int) {
        if (!enabled) return

        val app = findApp(id)
        if (app == null) {
            OutputTuils.sendOutput(context, context.getString(R.string.reply_id_not_found) + Tuils.SPACE + id)
            return
        }

        val wear = findNotificationWear(app)
        if (wear == null) {
            OutputTuils.sendOutput(context, R.string.reply_notification_not_found)
            return
        }

        OutputTuils.sendOutput(context, wear.text)
    }

    private fun nextUsableId(): Int {
        var nextUsableID = 0
        while (true) {
            val shouldRestart = boundApps?.any { it.applicationId == nextUsableID } == true
            if (!shouldRestart) return nextUsableID
            nextUsableID++
        }
    }

    fun ls(c: Context) {
        if (!enabled) return

        val builder = StringBuilder()
        if (instance != null) {
            boundApps?.forEach { a -> builder.append(a.packageName).append(" -> ").append(a.applicationId).append(Tuils.NEWLINE) }
        }
        var s = builder.toString()
        if (s.isEmpty()) s = "[]"

        OutputTuils.sendOutput(context, s)
    }

    companion object {
        const val PATH = "reply.xml"
        @JvmField
        val ACTION: String = BuildConfig.APPLICATION_ID + ".reply"
        const val ID = "id"
        const val WHAT = "what"
        @JvmField
        val ACTION_UPDATE: String = BuildConfig.APPLICATION_ID + ".update"
        @JvmField
        val ACTION_LS: String = BuildConfig.APPLICATION_ID + ".lsreplies"

        private const val BOUND_APPS_KEY = "bound_apps"

        @JvmField
        var boundApps: MutableList<BoundApp>? = null

        @JvmField
        var instance: ReplyManager? = null

        @JvmField
        var nextUsableId: Int = 0

        @JvmStatic
        fun bind(c: Context, pkg: String): String? {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val records = loadJsonList<BoundAppRecord>(prefs, BOUND_APPS_KEY)
            val index = records.indexOfFirst { it.packageName == pkg }
            if (index >= 0) records[index] = BoundAppRecord(pkg, nextUsableId)
            else records.add(BoundAppRecord(pkg, nextUsableId))
            saveJsonList(prefs, BOUND_APPS_KEY, records)
            return null
        }

        @JvmStatic
        fun unbind(c: Context, pkg: String): String? {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val records = loadJsonList<BoundAppRecord>(prefs, BOUND_APPS_KEY)
            val removed = records.removeIf { it.packageName == pkg }
            saveJsonList(prefs, BOUND_APPS_KEY, records)
            return if (removed) null else Tuils.EMPTYSTRING
        }
    }
}
