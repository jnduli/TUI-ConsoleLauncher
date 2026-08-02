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
import java.util.regex.Pattern

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class NotificationService : NotificationListenerService() {

    private val UPDATE_TIME = 2000L
    private val LINES_LABEL = "Lines"
    private val ANDROID_LABEL_PREFIX = "android."
    private val NULL_LABEL = "null"

    private var pastNotifications: HashMap<String, MutableList<Notification>>? = null
    private val handler = Handler()

    private var format: String? = null
    private var color = 0
    private var maxOptionalDepth = 0
    private var enabled = false
    private var click = false
    private var longClick = false
    private var active = false

    private var queue: Queue<StatusBarNotification>? = null

    private val PKG = "%pkg"
    private val APP = "%app"
    private val NEWLINE = "%n"
    private val timePattern: Pattern = Pattern.compile("^%t[0-9]*$")

    private var manager: PackageManager? = null
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

                while (true) {
                    if (isInterrupted) return

                    if (queue != null) {
                        var sbn: StatusBarNotification?
                        while (queue!!.poll().also { sbn = it } != null) {
                            val notification: android.app.Notification = sbn!!.notification ?: continue

                            val pack = sbn!!.packageName

                            val appName: String = try {
                                manager!!.getApplicationInfo(pack, 0).loadLabel(manager!!).toString()
                            } catch (e: PackageManager.NameNotFoundException) {
                                "null"
                            }

                            val nApp = notificationManager!!.getAppState(pack)
                            if (nApp != null && !nApp.enabled) {
                                continue
                            }

                            if (nApp == null && !notificationManager!!.default_app_state) {
                                continue
                            }

                            val f = nApp?.format ?: format
                            val textColor = nApp?.color?.let { Color.parseColor(it) } ?: color

                            var s: CharSequence = ViewTuils.span(f, textColor)

                            val bundle: Bundle? = NotificationCompat.getExtras(notification)

                            if (bundle != null) {
                                val m = formatPattern.matcher(s)
                                while (m.find()) {
                                    val match = m.group(0)!!
                                    if (!match.startsWith(PKG) && !match.startsWith(APP) && !match.startsWith(NEWLINE) && !timePattern.matcher(
                                            match
                                        ).matches()
                                    ) {
                                        val length = m.group(1)
                                        val colorGroup = m.group(2)
                                        var value = m.group(3)

                                        if (value.isNullOrEmpty()) value = m.group(4)

                                        value = value?.trim()
                                        if (value == null) continue

                                        if (value.isEmpty()) continue

                                        if (value == "ttl") value = "title"
                                        else if (value == "txt") value = "text"

                                        val temp = Pattern.compile(":").split(value)
                                        val split: Array<String> = if (value.endsWith(":")) {
                                            Array(temp.size + 1) { i -> if (i < temp.size) temp[i] else Tuils.EMPTYSTRING }
                                        } else temp

                                        // because the last one is the default text, but only if there is more than one label
                                        var stopAt = split.size
                                        if (stopAt > 1) stopAt--

                                        var text: CharSequence? = null
                                        for (j in 0 until stopAt) {
                                            if (split[j].contains(LINES_LABEL)) {
                                                val array = bundle.getCharSequenceArray(ANDROID_LABEL_PREFIX + split[j])
                                                if (array != null) {
                                                    for (c in array) {
                                                        text = if (text == null) c else TextUtils.concat(text, Tuils.NEWLINE, c)
                                                    }
                                                }
                                            } else {
                                                text = bundle.getCharSequence(ANDROID_LABEL_PREFIX + split[j])
                                            }

                                            if (text != null && text.isNotEmpty()) break
                                        }

                                        if (text == null || text.isEmpty()) {
                                            text = if (split.size == 1) NULL_LABEL else split[split.size - 1]
                                        }

                                        var stringed = text.toString().trim()

                                        try {
                                            val l = length!!.toInt()
                                            stringed = stringed.substring(0, l)
                                        } catch (e: Exception) {
                                        }

                                        val spanned: CharSequence = try {
                                            ViewTuils.span(stringed, Color.parseColor(colorGroup))
                                        } catch (e: Exception) {
                                            stringed
                                        }

                                        s = TextUtils.replace(s, arrayOf(m.group(0)), arrayOf(spanned))
                                    }
                                }
                            }

                            val text = s.toString()

                            if (notificationManager!!.match(text)) continue

                            val found = isInPastNotifications(pack, text)

                            if (found == 2) continue

                            val n = Notification(System.currentTimeMillis(), text, pack, notification.contentIntent)

                            if (found == 1) {
                                val ns = ArrayList<Notification>()
                                ns.add(n)
                                pastNotifications!![pack] = ns
                            } else if (found == 0) {
                                pastNotifications!![pack]!!.add(n)
                            }

                            s = TextUtils.replace(s, arrayOf(PKG, APP, NEWLINE), arrayOf(pack, appName, Tuils.NEWLINE))
                            var st = s.toString()
                            while (st.contains(NEWLINE)) {
                                s = TextUtils.replace(s, arrayOf(NEWLINE), arrayOf(Tuils.NEWLINE))
                                st = s.toString()
                            }

                            try {
                                s = TimeManager.instance.replace(s)
                            } catch (e: Exception) {
                                LogTuils.log(e)
                            }

                            OutputTuils.sendOutput(
                                this@NotificationService.applicationContext,
                                s,
                                type = TerminalManager.CATEGORY_NO_COLOR,
                                action = if (click) notification.contentIntent else null,
                                longAction = if (longClick) n else null
                            )

                            if (replyManager != null) replyManager!!.onNotification(sbn, s)
                        }
                    }

                    try {
                        sleep(UPDATE_TIME)
                    } catch (e: InterruptedException) {
                        LogTuils.log(e)
                        return
                    }
                }
            }
        }

        manager = packageManager
        enabled = XMLPrefsManager.getBoolean(Notifications.show_notifications) ||
            XMLPrefsManager.get(Notifications.show_notifications).equals("enabled", ignoreCase = true)

        pastNotifications = HashMap()

        format = XMLPrefsManager.get(Notifications.notification_format)
        color = XMLPrefsManager.getColor(Notifications.default_notification_color)

        click = XMLPrefsManager.getBoolean(Notifications.click_notification)
        longClick = XMLPrefsManager.getBoolean(Notifications.long_click_notification)

        maxOptionalDepth = XMLPrefsManager.getInt(Behavior.max_optional_depth)

        handler.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()

                for (entry in pastNotifications!!.entries) {
                    val notifications = entry.value

                    val it = notifications.iterator()
                    while (it.hasNext()) {
                        if (now - it.next().time >= UPDATE_TIME) it.remove()
                    }
                }

                handler.postDelayed(this, UPDATE_TIME)
            }
        })

        queue = ArrayBlockingQueue(5)
        bgThread!!.start()

        active = true
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
        if (replyManager != null) {
            replyManager!!.dispose(this)
            replyManager = null
        }

        if (notificationManager != null) {
            notificationManager!!.dispose()
            notificationManager = null
        }

        bgThread!!.interrupt()
        bgThread = null

        if (pastNotifications != null) {
            pastNotifications!!.clear()
            pastNotifications = null
        }

        if (queue != null) {
            queue!!.clear()
            queue = null
        }

        active = false
    }

    override fun onDestroy() {
        super.onDestroy()

        // ondestroy won't ever be called
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!enabled) return

        queue!!.offer(sbn)
    }

    //    0 = not found
    //    1 = the app wasnt found -> this is the first notification from this app
    //    2 = found
    private fun isInPastNotifications(pkg: String, text: String): Int {
        try {
            val notifications = pastNotifications!![pkg] ?: return 1
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
