package ohi.andre.consolelauncher.managers.notifications

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import kotlinx.parcelize.Parcelize
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.ViewTuils

import java.util.ConcurrentModificationException
import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.regex.Matcher
import java.util.regex.Pattern

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class NotificationService : NotificationListenerService() {

    private val UPDATE_TIME = 2000L
    private val LINES_LABEL = "Lines"
    private val ANDROID_LABEL_PREFIX = "android."
    private val NULL_LABEL = "null"

    private val pastNotifications = HashMap<String, MutableList<Notification>>()
    private val handler = Handler()

    private var format: String? = null
    private var color = 0
    private var maxOptionalDepth = 0
    private var enabled = false
    private var click = false
    private var longClick = false
    private var active = false

    private val queue: Queue<StatusBarNotification> = ArrayBlockingQueue(5)

    private val PKG = "%pkg"
    private val APP = "%app"
    private val NEWLINE = "%n"
    private val timePattern: Pattern = Pattern.compile("^%t[0-9]*$")

    private var replyManager: ReplyManager? = null
    private var notificationManager: NotificationManager? = null

    private val formatPattern: Pattern =
        Pattern.compile("%(?:\\[(\\d+)\\])?(?:\\[([^]]+)\\])?(?:(?:\\{)([a-zA-Z\\.\\:\\s]+)(?:\\})|([a-zA-Z\\.\\:]+))")

    private var bgThread: StoppableThread? = null

    override fun onCreate() {
        super.onCreate()

        init()
    }

    private fun init() {
        try {
            notificationManager = NotificationManager.create(this)
            XMLPrefsManager.loadCommons(this)
        } catch (e: Exception) {
            LogTuils.log(e)
            return
        }

        replyManager = try {
            ReplyManager(this)
        } catch (error: VerifyError) {
            null
        }

        bgThread = object : StoppableThread() {
            override fun run() {
                super.run()

                if (!enabled) return

                while (!isInterrupted) {
                    drainQueue()

                    try {
                        sleep(UPDATE_TIME)
                    } catch (e: InterruptedException) {
                        LogTuils.log(e)
                        return
                    }
                }
            }
        }

        readSettings()
        pastNotifications.clear()
        handler.post(pruneOldNotifications)

        queue.clear()
        bgThread?.start()

        active = true
    }

    private fun readSettings() {
        enabled = XMLPrefsManager.getBoolean(Notifications.show_notifications) ||
            XMLPrefsManager.get(Notifications.show_notifications).equals("enabled", ignoreCase = true)

        format = XMLPrefsManager.get(Notifications.notification_format)
        color = XMLPrefsManager.getColor(Notifications.default_notification_color)

        click = XMLPrefsManager.getBoolean(Notifications.click_notification)
        longClick = XMLPrefsManager.getBoolean(Notifications.long_click_notification)

        maxOptionalDepth = XMLPrefsManager.getInt(Behavior.max_optional_depth)
    }

    /** Forgets notifications older than [UPDATE_TIME], so a repeat after that is shown again. Reschedules itself. */
    private val pruneOldNotifications: Runnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            for (notifications in pastNotifications.values) {
                val it = notifications.iterator()
                while (it.hasNext()) {
                    if (now - it.next().time >= UPDATE_TIME) it.remove()
                }
            }

            handler.postDelayed(this, UPDATE_TIME)
        }
    }

    /** Runs on [bgThread]: handles every queued notification. */
    private fun drainQueue() {
        while (true) {
            val sbn = queue.poll() ?: return
            // disposed while this thread was still draining the queue
            val notificationManager = notificationManager ?: return
            handleNotification(sbn, notificationManager)
        }
    }

    /** Formats [sbn] and prints it, unless it's filtered out, disabled for its app, or a recent duplicate. */
    private fun handleNotification(sbn: StatusBarNotification, notificationManager: NotificationManager) {
        val notification: android.app.Notification = sbn.notification ?: return
        val pack = sbn.packageName

        val nApp = notificationManager.getAppState(pack)
        if (nApp != null && !nApp.enabled) return
        if (nApp == null && !notificationManager.default_app_state) return

        val f = nApp?.format ?: format
        val textColor = nApp?.color?.let { Color.parseColor(it) } ?: color

        var s = fillFormatFields(ViewTuils.span(f, textColor), NotificationCompat.getExtras(notification))

        val text = s.toString()
        if (notificationManager.match(text)) return

        val found = isInPastNotifications(pack, text)
        if (found == 2) return

        val n = Notification(System.currentTimeMillis(), text, pack, notification.contentIntent)
        if (found == 1) {
            pastNotifications[pack] = arrayListOf(n)
        } else if (found == 0) {
            pastNotifications[pack]?.add(n)
        }

        s = expandPlaceholders(s, pack, appLabel(pack))

        OutputTuils.sendOutput(
            this@NotificationService.applicationContext,
            s,
            type = TerminalManager.CATEGORY_NO_COLOR,
            action = if (click) notification.contentIntent else null,
            longAction = if (longClick) n else null
        )

        replyManager?.onNotification(sbn, s)
    }

    private fun appLabel(pack: String): String = try {
        packageManager.getApplicationInfo(pack, 0).loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        "null"
    }

    /**
     * Replaces each `%[length][color]{label:label:default}` field in [format] with the first non-empty
     * notification extra it names (title, text, lines...). `%pkg`, `%app`, `%n` and `%t` are left for
     * [expandPlaceholders].
     */
    private fun fillFormatFields(format: CharSequence, bundle: Bundle?): CharSequence {
        if (bundle == null) return format

        var s = format
        val m = formatPattern.matcher(format)
        while (m.find()) {
            val match = m.group(0) ?: continue
            if (match.startsWith(PKG) || match.startsWith(APP) || match.startsWith(NEWLINE) || timePattern.matcher(match).matches()) {
                continue
            }

            val replacement = fieldText(m, bundle) ?: continue
            s = TextUtils.replace(s, arrayOf(match), arrayOf(replacement))
        }
        return s
    }

    /** The text for the format field [m] currently matches, trimmed to its length and colored; null to leave it as-is. */
    private fun fieldText(m: Matcher, bundle: Bundle): CharSequence? {
        val length = m.group(1)
        val colorGroup = m.group(2)
        var value = m.group(3)
        if (value.isNullOrEmpty()) value = m.group(4)

        value = value?.trim()
        if (value.isNullOrEmpty()) return null

        if (value == "ttl") value = "title"
        else if (value == "txt") value = "text"

        val temp = Pattern.compile(":").split(value)
        val labels: Array<String> = if (value.endsWith(":")) {
            Array(temp.size + 1) { i -> if (i < temp.size) temp[i] else Tuils.EMPTYSTRING }
        } else temp

        var stringed = (lookupExtra(labels, bundle) ?: if (labels.size == 1) NULL_LABEL else labels[labels.size - 1])
            .toString().trim()
        length?.toIntOrNull()?.let { stringed = stringed.take(it) }

        return try {
            ViewTuils.span(stringed, Color.parseColor(colorGroup))
        } catch (e: Exception) {
            stringed
        }
    }

    /**
     * The first non-empty extra among [labels] (`android.<label>`; `Lines` labels join the array with
     * newlines). When there's more than one label, the last one is the default text, not a label.
     */
    private fun lookupExtra(labels: Array<String>, bundle: Bundle): CharSequence? {
        var stopAt = labels.size
        if (stopAt > 1) stopAt--

        var text: CharSequence? = null
        for (j in 0 until stopAt) {
            if (labels[j].contains(LINES_LABEL)) {
                val array = bundle.getCharSequenceArray(ANDROID_LABEL_PREFIX + labels[j])
                if (array != null) {
                    for (c in array) {
                        text = if (text == null) c else TextUtils.concat(text, Tuils.NEWLINE, c)
                    }
                }
            } else {
                text = bundle.getCharSequence(ANDROID_LABEL_PREFIX + labels[j])
            }

            if (text != null && text.isNotEmpty()) return text
        }
        return null
    }

    /** Expands `%pkg`, `%app`, `%n` and the time fields. */
    private fun expandPlaceholders(formatted: CharSequence, pack: String, appName: String): CharSequence {
        var s = TextUtils.replace(formatted, arrayOf(PKG, APP, NEWLINE), arrayOf(pack, appName, Tuils.NEWLINE))
        while (s.toString().contains(NEWLINE)) {
            s = TextUtils.replace(s, arrayOf(NEWLINE), arrayOf(Tuils.NEWLINE))
        }

        return try {
            TimeManager.instance.replace(s)
        } catch (e: Exception) {
            LogTuils.log(e)
            s
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val destroy = intent.getBooleanExtra(DESTROY, false)
            if (destroy) dispose()
        }

        if (!active) init()

        return START_STICKY
    }

    private fun dispose() {
        replyManager?.dispose(this)
        replyManager = null

        notificationManager?.dispose()
        notificationManager = null

        bgThread?.interrupt()
        bgThread = null

        pastNotifications.clear()
        queue.clear()

        active = false
    }

    override fun onDestroy() {
        super.onDestroy()

        // ondestroy won't ever be called
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!enabled || !active) return

        queue.offer(sbn)
    }

    //    0 = not found
    //    1 = the app wasnt found -> this is the first notification from this app
    //    2 = found
    private fun isInPastNotifications(pkg: String, text: String): Int {
        try {
            val notifications = pastNotifications[pkg] ?: return 1
            for (n in notifications) if (n.text == text) return 2
        } catch (e: ConcurrentModificationException) {
        }
        return 0
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    @Parcelize
    class Notification(
        @JvmField val time: Long,
        @JvmField val text: String,
        @JvmField val pkg: String,
        @JvmField val pendingIntent: PendingIntent?
    ) : Parcelable

    companion object {
        const val DESTROY = "destroy"

        @JvmStatic
        fun isRunning(context: Context): Boolean {
            val collectorComponent = ComponentName(context, NotificationService::class.java)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var collectorRunning = false
            val runningServices = manager.getRunningServices(Integer.MAX_VALUE) ?: return false

            for (service in runningServices) {
                if (service.service == collectorComponent) {
                    if (service.pid == Process.myPid()) {
                        collectorRunning = true
                    }
                }
            }

            return collectorRunning
        }
    }
}
