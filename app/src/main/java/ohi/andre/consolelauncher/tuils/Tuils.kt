package ohi.andre.consolelauncher.tuils

import android.Manifest
import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dalvik.system.DexFile
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.music.Song
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.stuff.FakeLauncherActivity
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Reader
import java.io.StringWriter
import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.NoSuchElementException
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object Tuils {

    const val TAG: String = "Tuils"

    const val SPACE: String = " "
    const val DOUBLE_SPACE: String = "  "
    const val NEWLINE: String = "\n"
    const val TRIBLE_SPACE: String = "   "
    const val DOT: String = "."
    const val EMPTYSTRING: String = ""
    private const val TUI_FOLDER = "t-ui"
    const val MINUS: String = "-"

    @JvmField
    var patternNewline: Pattern = Pattern.compile("%n", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    private var globalTypeface: Typeface? = null
    @JvmField
    var fontPath: String? = null

    private val calculusPattern: Pattern = Pattern.compile("([\\+\\-\\*\\/\\^])(\\d+\\.?\\d*)")

    @JvmStatic
    fun textCalculus(inputArg: Double, text: String): Double {
        var input = inputArg
        val m = calculusPattern.matcher(text)
        while (m.find()) {
            val operator = m.group(1)!![0]
            val value = m.group(2)!!.toDouble()

            when (operator) {
                '+' -> input += value
                '-' -> input -= value
                '*' -> input *= value
                '/' -> input /= value
                '^' -> input = Math.pow(input, value)
            }

            log("now im", input)
        }

        return input
    }

    @JvmStatic
    fun getTypeface(context: Context): Typeface? {
        if (globalTypeface == null) {
            try {
                XMLPrefsManager.loadCommons(context)
            } catch (e: Exception) {
                return null
            }

            val systemFont = XMLPrefsManager.getBoolean(Ui.system_font)
            if (systemFont) {
                globalTypeface = Typeface.DEFAULT
            } else {
                val tui = getFolder(context)
                if (tui == null) {
                    return Typeface.createFromAsset(context.assets, "lucida_console.ttf")
                }

                val p = Pattern.compile(".[ot]tf$")

                var font: File? = null
                for (f in tui.listFiles() ?: emptyArray()) {
                    val name = f.name
                    if (p.matcher(name).find()) {
                        font = f
                        fontPath = f.absolutePath
                        break
                    }
                }

                if (font != null) {
                    try {
                        globalTypeface = Typeface.createFromFile(font)
                        if (globalTypeface == null) throw UnsupportedOperationException()
                    } catch (e: Exception) {
                        globalTypeface = null
                    }
                }
            }

            if (globalTypeface == null) globalTypeface = if (systemFont) Typeface.DEFAULT else Typeface.createFromAsset(context.assets, "lucida_console.ttf")
        }
        return globalTypeface
    }

    @JvmStatic
    fun cancelFont() {
        globalTypeface = null
        fontPath = null
    }

    @JvmStatic
    fun locationName(context: Context, lat: Double, lng: Double): String? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses!![0].getAddressLine(2)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun notificationServiceIsRunning(context: Context): Boolean {
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

    @JvmStatic
    fun arrayContains(array: IntArray?, value: Int): Boolean {
        if (array == null) return false

        for (i in array) {
            if (i == value) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readerToString(initialReader: Reader): String {
        val arr = CharArray(8 * 1024)
        val buffer = StringBuilder()
        var numCharsRead: Int
        while (initialReader.read(arr, 0, arr.size).also { numCharsRead = it } != -1) {
            buffer.append(arr, 0, numCharsRead)
        }
        initialReader.close()
        return buffer.toString()
    }

    @JvmStatic
    fun containsExtension(array: Array<String>, valueArg: String): Boolean {
        return try {
            val value = valueArg.lowercase(Locale.getDefault()).trim()
            for (s in array) {
                if (value.endsWith(s)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun getSongsInFolder(folder: File): List<Song> {
        val songs = ArrayList<Song>()

        val files = folder.listFiles()
        if (files == null || files.isEmpty()) {
            return songs
        }

        for (file in files) {
            if (file.isDirectory) {
                val s = getSongsInFolder(file)
                songs.addAll(s)
            } else if (containsExtension(MusicManager2.MUSIC_EXTENSIONS, file.name)) {
                songs.add(Song(file))
            }
        }

        return songs
    }

    @JvmStatic
    fun convertStreamToString(`is`: InputStream?): String {
        if (`is` == null) return EMPTYSTRING

        val s = java.util.Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else EMPTYSTRING
    }

    @JvmStatic
    @Throws(Exception::class)
    fun download(`in`: InputStream, file: File): Long {
        val out: OutputStream = FileOutputStream(file, false)

        val data = ByteArray(1024)

        var bytes: Long = 0

        var count: Int
        while (`in`.read(data).also { count = it } != -1) {
            out.write(data, 0, count)
            bytes += count
        }

        out.flush()
        out.close()
        `in`.close()

        return bytes
    }

    @JvmStatic
    @Throws(Exception::class)
    fun write(file: File, separator: String, vararg ss: String) {
        val headerStream = FileOutputStream(file, false)

        for (c in 0 until ss.size - 1) {
            headerStream.write(ss[c].toByteArray())
            headerStream.write(separator.toByteArray())
        }
        headerStream.write(ss[ss.size - 1].toByteArray())

        headerStream.flush()
        headerStream.close()
    }

    @JvmStatic
    fun dpToPx(context: Context, valueInDp: Float): Float {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics)
    }

    @JvmStatic
    fun hasNotificationAccess(context: Context): Boolean {
        val pkgName = BuildConfig.APPLICATION_ID
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    @JvmStatic
    fun resetPreferredLauncherAndOpenChooser(context: Context) {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, FakeLauncherActivity::class.java)
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)

        val selector = Intent(Intent.ACTION_MAIN)
        selector.addCategory(Intent.CATEGORY_HOME)
        selector.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(selector)

        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }

    @JvmStatic
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    fun openSettingsPage(c: Context, packageName: String?) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        c.startActivity(intent)
    }

    @JvmStatic
    fun requestAdmin(component: ComponentName?, explanation: String?): Intent {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
        return intent
    }

    @JvmStatic
    fun webPage(url: String?): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    @JvmStatic
    fun percentage(part: Double, total: Double): Double {
        return round(part * 100 / total, 2)
    }

    const val TERA: Int = 0
    const val GIGA: Int = 1
    const val MEGA: Int = 2
    const val KILO: Int = 3
    const val BYTE: Int = 4

    @JvmStatic
    fun formatSize(bytes: Long, unit: Int): Double {
        val convert = 1048576.0
        val smallConvert = 1024.0

        val result: Double = when (unit) {
            TERA -> (bytes / convert) / convert
            GIGA -> (bytes / convert) / smallConvert
            MEGA -> bytes / convert
            KILO -> bytes / smallConvert
            BYTE -> bytes.toDouble()
            else -> return -1.0
        }

        return round(result, 2)
    }

    @JvmStatic
    fun isMyLauncherDefault(packageManager: PackageManager): Boolean {
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)

        val filters = ArrayList<IntentFilter>()
        filters.add(filter)

        val myPackageName = BuildConfig.APPLICATION_ID
        val activities = ArrayList<ComponentName>()

        // You can use name of your package here as third argument
        packageManager.getPreferredActivities(filters, activities, null)

        for (activity in activities) {
            if (myPackageName == activity.packageName) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun span(text: CharSequence?, color: Int): SpannableString {
        return span(null, text, color, Integer.MAX_VALUE)
    }

    @JvmStatic
    fun span(context: Context?, size: Int, text: CharSequence?): SpannableString {
        return span(context, text, Integer.MAX_VALUE, size)
    }

    @JvmStatic
    fun span(context: Context?, text: CharSequence?, color: Int, size: Int): SpannableString {
        return span(context, Integer.MAX_VALUE, color, text, size)
    }

    @JvmStatic
    fun span(bgColor: Int, foreColor: Int, text: CharSequence?): SpannableString {
        return span(null, bgColor, foreColor, text, Integer.MAX_VALUE)
    }

    @JvmStatic
    fun span(context: Context?, bgColor: Int, foreColor: Int, textArg: CharSequence?, size: Int): SpannableString {
        val text = textArg ?: EMPTYSTRING

        val spannableString: SpannableString = if (text is SpannableString) text else SpannableString(text)

        if (size != Integer.MAX_VALUE && context != null) spannableString.setSpan(AbsoluteSizeSpan(convertSpToPixels(size.toFloat(), context)), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (foreColor != Integer.MAX_VALUE) spannableString.setSpan(ForegroundColorSpan(foreColor), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bgColor != Integer.MAX_VALUE) spannableString.setSpan(BackgroundColorSpan(bgColor), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return spannableString
    }

    @JvmStatic
    fun span(bgColor: Int, text: SpannableString, section: String, fromIndex: Int): Int {
        val index = text.toString().indexOf(section, fromIndex)
        if (index == -1) return index

        text.setSpan(BackgroundColorSpan(bgColor), index, index + section.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return index + section.length
    }

    @JvmStatic
    fun convertSpToPixels(sp: Float, context: Context): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()
    }

    @JvmStatic
    fun inputStreamToString(`is`: InputStream): String {
        val s = java.util.Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else EMPTYSTRING
    }

    @JvmStatic
    fun deleteContentOnly(dir: File) {
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) delete(f)
            f.delete()
        }
    }

    @JvmStatic
    fun delete(dir: File) {
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) delete(f)
            f.delete()
        }
        dir.delete()
    }

    @JvmStatic
    fun insertOld(c: Context, oldFile: File?): Boolean {
        if (oldFile == null || !oldFile.exists()) return false

        val oldPath = oldFile.absolutePath

        val oldFolder = File(getFolder(c), "old")
        if (!oldFolder.exists()) oldFolder.mkdir()

        val dest = File(oldFolder, oldFile.name)
        if (dest.exists()) dest.delete()

        return oldFile.renameTo(dest) && File(oldPath).delete()
    }

    @JvmStatic
    fun getOld(c: Context, name: String): File? {
        val old = File(getFolder(c), "old")
        val file = File(old, name)

        if (file.exists()) return file
        return null
    }

    @JvmStatic
    fun deepView(v: View) {
        log(v.toString())

        if (v !is ViewGroup) return

        log(v.childCount)
        for (c in 0 until v.childCount) deepView(v.getChildAt(c))

        log("end of parents of: $v")
    }

    private val deepClickListener = View.OnClickListener { v -> log(v.toString()) }

    @JvmStatic
    fun deepClickView(v: View) {
        v.setOnClickListener(deepClickListener)

        if (v !is ViewGroup) return

        for (c in 0 until v.childCount) deepClickView(v.getChildAt(c))
    }

    @JvmStatic
    @Throws(NoSuchElementException::class)
    fun scaleImage(view: ImageView, newX: Int, newY: Int) {
        // Get bitmap from the the ImageView.
        var bitmap: Bitmap?

        try {
            val drawing = view.drawable
            bitmap = (drawing as BitmapDrawable).bitmap
        } catch (e: NullPointerException) {
            throw NoSuchElementException("No drawable on given view")
        }

        // Get current dimensions AND the desired bounding box
        var width: Int

        try {
            width = bitmap!!.width
        } catch (e: NullPointerException) {
            throw NoSuchElementException("Can't find bitmap on given view/drawable")
        }

        val height = bitmap!!.height
        val xBounding = dpToPx(view.context, newX)
        val yBounding = dpToPx(view.context, newY)

        // Determine how much to scale: the dimension requiring less scaling is
        // closer to the its side. This way the image always stays inside your
        // bounding box AND either x/y axis touches it.
        val xScale = xBounding.toFloat() / width
        val yScale = yBounding.toFloat() / height
        val scale = if (xScale <= yScale) xScale else yScale

        // Create a matrix for the scaling and add the scaling data
        val matrix = Matrix()
        matrix.postScale(scale, scale)

        // Create a new bitmap and convert it to a format understood by the ImageView
        val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        width = scaledBitmap.width // re-use
        val height2 = scaledBitmap.height // re-use
        val result = BitmapDrawable(view.resources, scaledBitmap)

        // Apply the scaled bitmap
        view.setImageDrawable(result)

        // Now change ImageView's dimensions to match the scaled image
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.width = width
        params.height = height2
        view.layoutParams = params
    }

    @JvmStatic
    fun dpToPx(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp * density)
    }

    @JvmStatic
    fun sendOutput(context: Context?, res: Int) {
        sendOutput(Integer.MAX_VALUE, context, res)
    }

    @JvmStatic
    fun sendOutput(color: Int, context: Context?, res: Int) {
        sendOutput(color, context, context!!.getString(res))
    }

    @JvmStatic
    fun sendOutput(context: Context?, res: Int, type: Int) {
        sendOutput(Integer.MAX_VALUE, context, res, type)
    }

    @JvmStatic
    fun sendOutput(color: Int, context: Context?, res: Int, type: Int) {
        sendOutput(color, context, context!!.getString(res), type)
    }

    @JvmStatic
    fun sendOutput(context: Context?, s: CharSequence?) {
        sendOutput(Integer.MAX_VALUE, context, s)
    }

    @JvmStatic
    fun sendOutput(color: Int, context: Context?, s: CharSequence?) {
        sendOutput(color, context, s, TerminalManager.CATEGORY_OUTPUT)
    }

    @JvmStatic
    fun sendOutput(context: Context?, s: CharSequence?, type: Int) {
        sendOutput(Integer.MAX_VALUE, context, s, type)
    }

    @JvmStatic
    fun sendOutput(color: Int, context: Context?, s: CharSequence?, type: Int) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, s)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)
        LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
    }

    @JvmStatic
    fun sendOutput(mainPack: MainPack, s: CharSequence, type: Int) {
        sendOutput(mainPack.commandColor, mainPack.context, s, type)
    }

    @JvmStatic
    fun sendOutput(context: Context?, s: CharSequence?, type: Int, action: Any?) {
        sendOutput(Integer.MAX_VALUE, context, s, type, action)
    }

    @JvmStatic
    fun sendOutput(color: Int, context: Context?, s: CharSequence?, type: Int, action: Any?) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, s)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)

        if (action is String) intent.putExtra(PrivateIOReceiver.ACTION, action)
        else if (action is Parcelable) intent.putExtra(PrivateIOReceiver.ACTION, action)

        LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
    }

    @JvmStatic
    fun sendOutput(context: Context?, s: CharSequence?, type: Int, action: Any?, longAction: Any?) {
        sendOutput(Integer.MAX_VALUE, context, s, type, action, longAction)
    }

    @JvmStatic
    fun sendOutput(color: Int, context: Context?, s: CharSequence?, type: Int, action: Any?, longAction: Any?) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, s)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)

        if (action is String) intent.putExtra(PrivateIOReceiver.ACTION, action)
        else if (action is Parcelable) intent.putExtra(PrivateIOReceiver.ACTION, action)

        if (longAction is String) intent.putExtra(PrivateIOReceiver.LONG_ACTION, longAction)
        else if (longAction is Parcelable) intent.putExtra(PrivateIOReceiver.LONG_ACTION, longAction)

        LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
    }

    @JvmStatic
    fun sendInput(context: Context, text: String?) {
        val intent = Intent(PrivateIOReceiver.ACTION_INPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private var total: Long = -1

    @JvmStatic
    fun freeRam(mgr: ActivityManager, info: MemoryInfo): Double {
        mgr.getMemoryInfo(info)
        return info.availMem.toDouble()
    }

    @JvmStatic
    fun totalRam(): Long {
        if (total > 0) return total

        try {
            val reader = BufferedReader(InputStreamReader(FileInputStream("/proc/meminfo")))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("MemTotal")) {
                    line = line!!.replace(Regex("\\D+"), EMPTYSTRING)
                    return line!!.toLong() * 1024L
                }
            }
        } catch (e: Exception) {
        }
        return 0
    }

    @JvmStatic
    fun round(value: Double, placesArg: Int): Double {
        val places = if (placesArg < 0) 0 else placesArg

        return try {
            var bd = BigDecimal(value)
            bd = bd.setScale(places, RoundingMode.HALF_UP)
            bd.toDouble()
        } catch (e: Exception) {
            value
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getClassesInPackage(packageName: String, c: Context): List<String> {
        val classes = ArrayList<String>()
        val packageCodePath = c.packageCodePath
        val df = DexFile(packageCodePath)
        val iter = df.entries()
        while (iter.hasMoreElements()) {
            val className = iter.nextElement()
            if (className.contains(packageName) && !className.contains("$")) {
                classes.add(className.substring(className.lastIndexOf(".") + 1, className.length))
            }
        }

        return classes
    }

    @JvmStatic
    fun scale(from: IntArray, to: IntArray, n: Int): Int {
        return (to[1] - to[0]) * (n - from[0]) / (from[1] - from[0]) + to[0]
    }

    @JvmStatic
    fun toString(enums: Array<out Enum<*>>): Array<String> {
        return Array(enums.size) { count -> enums[count].name }
    }

    private fun getNicePath(filePath: String?): String {
        if (filePath == null) return "null"

        val home = XMLPrefsManager.get(File::class.java, Behavior.home_path).absolutePath

        return if (filePath == home) {
            "~"
        } else if (filePath.startsWith(home)) {
            "~" + filePath.replace(home, EMPTYSTRING)
        } else {
            filePath
        }
    }

    @JvmStatic
    fun find(o: Any?, array: Array<Any?>): Int {
        return find(o, listOf(*array))
    }

    @JvmStatic
    fun find(o: Any?, list: List<*>): Int {
        for (count in list.indices) {
            val x = list[count] ?: continue

            if (o === x) return count

            if (o is XMLPrefsSave) {
                try {
                    if (o.label() == x as? String) return count
                } catch (e: Exception) {
                }
            }

            if (o is String && x is XMLPrefsSave) {
                try {
                    if (x.label() == o) return count
                } catch (e: Exception) {
                }
            }

            try {
                if (o == x || x == o) return count
            } catch (e: Exception) {
                continue
            }
        }
        return -1
    }

    private val pd: Pattern = Pattern.compile("%d", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val pu: Pattern = Pattern.compile("%u", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val pp: Pattern = Pattern.compile("%p", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    @JvmStatic
    fun getHint(currentPath: String?): String? {
        if (!XMLPrefsManager.getBoolean(Ui.show_session_info)) return null

        var format = XMLPrefsManager.get(Behavior.session_info_format)
        if (format.isEmpty()) return null

        var deviceName = XMLPrefsManager.get(Ui.deviceName)
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = Build.DEVICE
        }

        var username = XMLPrefsManager.get(Ui.username)
        if (username == null) username = EMPTYSTRING

        format = pd.matcher(format).replaceAll(Matcher.quoteReplacement(deviceName))
        format = pu.matcher(format).replaceAll(Matcher.quoteReplacement(username))
        format = pp.matcher(format).replaceAll(Matcher.quoteReplacement(getNicePath(currentPath)))

        return format
    }

    @JvmStatic
    fun findPrefix(list: List<String>, prefix: String): Int {
        for (count in list.indices) {
            if (list[count].startsWith(prefix)) {
                return count
            }
        }
        return -1
    }

    @JvmStatic
    fun mmToPx(metrics: DisplayMetrics, mm: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm.toFloat(), metrics).toInt()
    }

    @JvmStatic
    fun insertHeaders(s: MutableList<String>, newLine: Boolean) {
        var current = 0.toChar()
        var count = 0
        while (count < s.size) {
            val st = s[count].trim().uppercase(Locale.getDefault())

            val c = st[0]
            if (current != c) {
                s.add(count, (if (newLine) NEWLINE else EMPTYSTRING) + c + (if (newLine) NEWLINE else EMPTYSTRING))
                current = c
            }
            count++
        }
    }

    @JvmStatic
    fun addPrefix(list: MutableList<String>, prefix: String) {
        for (count in list.indices) {
            list[count] = prefix + list[count]
        }
    }

    @JvmStatic
    fun addSeparator(list: MutableList<String>, separator: String) {
        for (count in list.indices) {
            list[count] = list[count] + separator
        }
    }

    @JvmStatic
    fun printApps(apps: MutableList<String>): String {
        if (apps.isEmpty()) return apps.toString()

        // insertHeaders inserts entries in place, so this needs a real (resizable) ArrayList copy
        val list = ArrayList(apps)
        list.sortWith { a, b -> alphabeticCompare(a, b) }

        addPrefix(list, DOUBLE_SPACE)
        insertHeaders(list, false)
        return toPlanString(list)
    }

    @JvmStatic
    fun toPlanString(strings: Array<String>?, separator: String): String {
        if (strings == null) {
            return EMPTYSTRING
        }

        var output = EMPTYSTRING
        for (count in strings.indices) {
            output += strings[count]
            if (count < strings.size - 1) output += separator
        }
        return output
    }

    @JvmStatic
    fun toPlanString(strings: Array<String>?): String {
        if (strings != null) {
            return toPlanString(strings, NEWLINE)
        }
        return EMPTYSTRING
    }

    @JvmStatic
    fun toPlanString(separator: String, strings: List<*>?): String {
        if (strings == null) {
            return EMPTYSTRING
        }

        var output = EMPTYSTRING
        for (count in strings.indices) {
            output += strings[count].toString()
            if (count < strings.size - 1) output += separator
        }
        return output
    }

    @JvmStatic
    fun nodeToString(node: Node): String? {
        return try {
            val transfac = TransformerFactory.newInstance()
            val trans = transfac.newTransformer()
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            trans.setOutputProperty(OutputKeys.INDENT, "yes")
            val sw = StringWriter()
            val result = StreamResult(sw)
            val source = DOMSource(node)
            trans.transform(source, result)

            sw.toString()
        } catch (e: TransformerException) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun log(o: Any?) {
        if (o is Throwable) {
            Log.e("andre", "", o)
        } else {
            val text: String = if (o is Array<*>) o.contentToString() else o.toString()
            Log.e("andre", text)
        }
    }

    @JvmStatic
    fun log(o: Any?, o2: Any?) {
        if (o is Array<*> && o2 is Array<*>) {
            Log.e("andre", o.contentToString() + " -- " + o2.contentToString())
        } else {
            Log.e("andre", "$o -- $o2")
        }
    }

    @JvmStatic
    fun log(o: Any?, to: PrintStream) {
        if (o is Throwable) {
            o.printStackTrace(to)
        } else {
            val text: String = if (o is Array<*>) o.contentToString() else o.toString()

            try {
                to.write(text.toByteArray())
            } catch (e: IOException) {
                log(e)
            }
        }
    }

    @JvmStatic
    fun log(o: Any?, o2: Any?, to: OutputStream) {
        try {
            if (o is Array<*> && o2 is Array<*>) {
                to.write((o.contentToString() + " -- " + o2.contentToString()).toByteArray())
            } else {
                to.write(("$o -- $o2").toByteArray())
            }
        } catch (e: Exception) {
            log(e)
        }
    }

    @JvmStatic
    fun hasInternetAccess(): Boolean {
        return try {
            val urlc = URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection
            (urlc.responseCode == 204 && urlc.contentLength == 0)
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> getDefaultValue(clazz: Class<T>): T {
        return ReflectArray.get(ReflectArray.newInstance(clazz, 1), 0) as T
    }

    @JvmStatic
    fun toFile(e: Exception) {
        log(e)
    }

    @JvmStatic
    fun toFile(c: Context, o: Any?) {
        if (o == null) return
        try {
            val stream = FileOutputStream(File(getFolder(c), "crash.txt"))
            stream.write((NEWLINE + NEWLINE).toByteArray())

            if (o is Throwable) {
                val ps = PrintStream(stream)
                o.printStackTrace(ps)
            } else {
                stream.write(o.toString().toByteArray())
            }

            stream.write((NEWLINE + "----------------------------").toByteArray())

            stream.close()
        } catch (e1: Exception) {
        }
    }

    @JvmStatic
    fun toPlanString(strings: List<String>?, separator: String): String {
        if (strings != null) {
            val array: Array<String> = strings.toTypedArray()
            return toPlanString(strings = array, separator = separator)
        }
        return EMPTYSTRING
    }

    @JvmStatic
    fun filesToPlanString(files: List<File>?, separator: String): String? {
        if (files == null || files.isEmpty()) {
            return null
        }

        val builder = StringBuilder()
        val limit = files.size - 1
        for (count in files.indices) {
            builder.append(files[count].name)
            if (count < limit) {
                builder.append(separator)
            }
        }
        return builder.toString()
    }

    @JvmStatic
    fun toPlanString(strings: List<String>?): String {
        return toPlanString(strings, NEWLINE)
    }

    @JvmStatic
    fun toPlanString(objs: Array<Any?>?, separator: String): String {
        if (objs == null) {
            return EMPTYSTRING
        }

        val output = StringBuilder()
        for (count in objs.indices) {
            output.append(objs[count])
            if (count < objs.size - 1) {
                output.append(separator)
            }
        }
        return output.toString()
    }

    private val unnecessarySpaces: Pattern = Pattern.compile("\\s{2,}")

    @JvmStatic
    fun removeUnncesarySpaces(string: String): String {
        return unnecessarySpaces.matcher(string).replaceAll(SPACE)
    }

    @JvmStatic
    fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw, true)
        throwable.printStackTrace(pw)
        return sw.buffer.toString()
    }

    @JvmStatic
    fun isAlpha(s: String?): Boolean {
        if (s == null) {
            return false
        }

        for (c in s.toCharArray()) {
            if (!Character.isLetter(c)) {
                return false
            }
        }

        return true
    }

    @JvmStatic
    fun isPhoneNumber(s: String?): Boolean {
        if (s == null) {
            return false
        }

        for (c in s.toCharArray()) {
            if (Character.isLetter(c)) {
                return false
            }
        }

        return true
    }

    // return 0 if only digit
    @JvmStatic
    fun firstNonDigit(s: String?): Char {
        if (s == null) {
            return 0.toChar()
        }

        for (c in s.toCharArray()) {
            if (!Character.isDigit(c)) {
                return c
            }
        }

        return 0.toChar()
    }

    @JvmStatic
    fun isNumber(s: String?): Boolean {
        if (s == null || s.isEmpty()) {
            return false
        }

        for (c in s.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false
            }
        }

        return true
    }

    @JvmStatic
    fun openFile(c: Context, f: File): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val u = buildFile(c, f)
        val mimetype = MimeTypes.getMimeType(f.absolutePath, f.isDirectory)

        intent.setDataAndType(u, mimetype)

        val flags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        intent.addFlags(flags)

        return intent
    }

    @JvmStatic
    fun shareFile(c: Context, f: File): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val u = buildFile(c, f)

        val mimetype = MimeTypes.getMimeType(f.absolutePath, f.isDirectory)

        intent.setDataAndType(u, mimetype)

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, u)

        return intent
    }

    private fun buildFile(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Uri.fromFile(file)
        } else {
            FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file)
        }
    }

    private fun getTuiFolder(c: Context?): File? {
        return c?.getExternalFilesDir(TUI_FOLDER)
        // File internalDir = Environment.getExternalStorageDirectory();
        // return new File(internalDir, TUI_FOLDER);
    }

    @JvmStatic
    fun eval(str: String): Double {
        return object {
            var pos = -1
            var ch = 0

            fun nextChar() {
                pos++
                ch = if (pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) x /= parseFactor()
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else if (ch >= 'a'.code && ch <= 'z'.code) {
                    while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                    val func = str.substring(startPos, pos)
                    x = parseFactor()
                    x = when (func) {
                        "sqrt" -> Math.sqrt(x)
                        "sin" -> Math.sin(Math.toRadians(x))
                        "cos" -> Math.cos(Math.toRadians(x))
                        "tan" -> Math.tan(Math.toRadians(x))
                        else -> throw RuntimeException("Unknown function: $func")
                    }
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.code)) x = Math.pow(x, parseFactor())

                return x
            }
        }.parse()
    }

    @JvmStatic
    fun getTextFromClipboard(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val item: ClipData.Item = manager.primaryClip!!.getItemAt(0)
                item.text.toString()
            } else {
                @Suppress("DEPRECATION")
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.text.ClipboardManager
                manager.text.toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun dpToPx(resources: Resources, dp: Int): Int {
        val displayMetrics = resources.displayMetrics
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
    }

    private const val FILEUPDATE_DELAY = 100L
    private var folder: File? = null

    @JvmStatic
    fun getFolder(c: Context?): File? {
        if (folder != null) return folder

        var elapsedTime = 0L
        while (elapsedTime < 1000) {
            val tuiFolder = getTuiFolder(c)
            if (tuiFolder != null && ((tuiFolder.exists() && tuiFolder.isDirectory) || tuiFolder.mkdir())) {
                folder = tuiFolder
                return folder
            }

            try {
                Thread.sleep(FILEUPDATE_DELAY)
            } catch (e: InterruptedException) {
            }

            elapsedTime += FILEUPDATE_DELAY
        }

        return null
    }

    @JvmStatic
    fun alphabeticCompare(s1: String, s2: String): Int {
        val cmd1 = removeSpaces(s1).lowercase(Locale.getDefault())
        val cmd2 = removeSpaces(s2).lowercase(Locale.getDefault())

        var count = 0
        while (count < cmd1.length && count < cmd2.length) {
            val c1 = cmd1[count]
            val c2 = cmd2[count]

            if (c1 < c2) {
                return -1
            } else if (c1 > c2) {
                return 1
            }
            count++
        }

        return if (s1.length > s2.length) {
            1
        } else if (s1.length < s2.length) {
            -1
        } else 0
    }

    private const val SPACE_REGEXP = "\\s"

    @JvmStatic
    fun removeSpaces(string: String): String {
        return string.replace(Regex(SPACE_REGEXP), EMPTYSTRING)
    }

    @JvmStatic
    fun getNetworkType(context: Context): String {
        val mTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "unknown"
        }
        var networkType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkType = mTelephonyManager!!.dataNetworkType
        }
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2g"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3g"
            TelephonyManager.NETWORK_TYPE_LTE -> "4g"
            else -> "unknown"
        }
    }

    @JvmStatic
    fun setCursorDrawableColor(editText: EditText, color: Int) {
        try {
            val fCursorDrawableRes = TextView::class.java.getDeclaredField("mCursorDrawableRes")
            fCursorDrawableRes.isAccessible = true
            val mCursorDrawableRes = fCursorDrawableRes.getInt(editText)
            val fEditor = TextView::class.java.getDeclaredField("mEditor")
            fEditor.isAccessible = true
            val editor = fEditor.get(editText)
            val clazz = editor.javaClass
            val fCursorDrawable = clazz.getDeclaredField("mCursorDrawable")
            fCursorDrawable.isAccessible = true
            val drawables = arrayOfNulls<Drawable>(2)
            @Suppress("DEPRECATION")
            drawables[0] = editText.context.resources.getDrawable(mCursorDrawableRes)
            @Suppress("DEPRECATION")
            drawables[1] = editText.context.resources.getDrawable(mCursorDrawableRes)
            drawables[0]!!.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            drawables[1]!!.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            fCursorDrawable.set(editor, drawables)
        } catch (ignored: Throwable) {
        }
    }

    @JvmStatic
    fun nOfBytes(file: File): Int {
        var count = 0
        return try {
            val `in` = FileInputStream(file)

            while (`in`.read() != -1) count++

            count
        } catch (e: IOException) {
            log(e)
            count
        }
    }

    @JvmStatic
    fun sendXMLParseError(context: Context?, PATH: String, e: SAXParseException) {
        sendOutput(
            Color.RED,
            context, context!!.getString(R.string.output_xmlproblem1) + SPACE + PATH + context.getString(R.string.output_xmlproblem2) + NEWLINE + context.getString(R.string.output_errorlabel) +
                "File: " + e.systemId + NEWLINE +
                "Message" + e.message + NEWLINE +
                "Line" + e.lineNumber + NEWLINE +
                "Column" + e.columnNumber
        )
    }

    @JvmStatic
    fun sendXMLParseError(context: Context?, PATH: String) {
        sendOutput(Color.RED, context, context!!.getString(R.string.output_xmlproblem1) + SPACE + PATH + context.getString(R.string.output_xmlproblem2))
    }
}
