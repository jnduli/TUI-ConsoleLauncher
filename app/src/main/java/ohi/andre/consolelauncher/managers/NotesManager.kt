package ohi.andre.consolelauncher.managers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

/**
 * Created by francescoandreuzzi on 12/02/2018.
 */
class NotesManager(var mContext: Context) {
    private val path = "notes.xml"
    private val name = "NOTES"
    private val noteNode = "note"

    var oldNotes: CharSequence? = null
    var hasChanged: Boolean = false

    var classes: MutableSet<Class?>
    var notes: MutableList<Note>

    var optionalPattern: Pattern
    var footer: String
    var header: String
    var divider: String
    var color: Int
    var lockedColor: Int

    var allowLink: Boolean
    var linkColor: Int = 0

    var receiver: BroadcastReceiver

    var packageManager: PackageManager?

    private fun load(context: Context?, loadClasses: Boolean) {
        if (loadClasses) classes.clear()
        notes.clear()

        val file = File(Tuils.getFolder(context), path)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, name)
        }

        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, name)
            if (o == null) {
                Tuils.sendXMLParseError(context, path)
                return
            }
        } catch (e: SAXParseException) {
            Tuils.sendXMLParseError(context, path, e)
            return
        } catch (e: Exception) {
            Tuils.log(e)
            return
        }

        val root = o[1] as Element

        val nodes = root.getElementsByTagName("*")

        for (count in 0..<nodes.length) {
            val node = nodes.item(count)

            if (node.nodeType == Node.ELEMENT_NODE) {
                val e = node as Element
                val name = e.nodeName

                if (name == noteNode) {
                    val time = XMLPrefsManager.getLongAttribute(e, CREATION_TIME)
                    val text =
                        XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)
                    val lock = XMLPrefsManager.getBooleanAttribute(e, LOCK)

                    notes.add(Note(time, text!!, lock))
                } else if (loadClasses) {
                    val id: Int
                    try {
                        id = name.toInt()
                    } catch (ex: Exception) {
                        continue
                    }

                    val color: Int
                    try {
                        color = XMLPrefsManager.getStringAttribute(
                            e,
                            XMLPrefsManager.VALUE_ATTRIBUTE
                        ).toColorInt()
                    } catch (ex: Exception) {
                        continue
                    }

                    classes.add(Class(id, color))
                }
            }
        }

        notes.sort()
        invalidateNotes()
    }

    var colorPattern: Pattern = Pattern.compile("(\\d+|#[\\da-zA-Z]{6,8})\\(([^)]*)\\)")
    var countPattern: Pattern = Pattern.compile("%c", Pattern.CASE_INSENSITIVE)
    var lockPattern: Pattern = Pattern.compile("%l", Pattern.CASE_INSENSITIVE)
    var rowPattern: Pattern = Pattern.compile("%r", Pattern.CASE_INSENSITIVE)
    var uriPattern: Pattern = Pattern.compile("(http[s]?:[^\\s]+|www\\.[^\\s]*)\\.[a-z]+")

    //    noteview can't be changed too much, it may be shared
    init {
        classes = HashSet<Class?>()
        notes = ArrayList<Note>()

        packageManager = mContext.packageManager

        val optionalSeparator = "\\" + XMLPrefsManager.get(Behavior.optional_values_separator)
        val optional = "%\\(([^$optionalSeparator]*)$optionalSeparator([^)]*)\\)"
        optionalPattern = Pattern.compile(optional, Pattern.CASE_INSENSITIVE)

        color = XMLPrefsManager.getColor(Theme.notes_color)
        lockedColor = XMLPrefsManager.getColor(Theme.notes_locked_color)

        footer = XMLPrefsManager.get(Ui.notes_footer)
        header = XMLPrefsManager.get(Ui.notes_header)
        divider = XMLPrefsManager.get(Ui.notes_divider)
        divider = Tuils.patternNewline.matcher(divider).replaceAll(Tuils.NEWLINE)

        allowLink = XMLPrefsManager.getBoolean(Behavior.notes_allow_link)
        if (allowLink) {
            linkColor = XMLPrefsManager.getColor(Theme.link_color)
        }
        Note.sorting = XMLPrefsManager.getInt(Behavior.notes_sorting)

        load(mContext, true)

        val filter = IntentFilter()
        filter.addAction(ACTION_ADD)
        filter.addAction(ACTION_RM)
        filter.addAction(ACTION_CLEAR)
        filter.addAction(ACTION_LS)
        filter.addAction(ACTION_LOCK)
        filter.addAction(ACTION_CP)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                if (intent.getIntExtra(BROADCAST_COUNT, 0) < broadcastCount) return
                broadcastCount++
                when (intent.action) {
                    ACTION_ADD -> {
                        var text = intent.getStringExtra(TEXT) ?: return
                        var lock = false
                        val split: Array<String?> =
                            text.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        var startAt = 0

                        val beforeSpace = if (split.size >= 2) split[0] else null
                        if (beforeSpace != null) {
                            if ((beforeSpace == "true" || beforeSpace == "false")) {
                                lock = beforeSpace.toBoolean()
                                startAt++
                            }

                            val ar = arrayOfNulls<String>(split.size - startAt)
                            System.arraycopy(split, startAt, ar, 0, ar.size)
                            text = Tuils.toPlanString(ar, Tuils.SPACE)
                        }

                        addNote(text, lock)
                    }
                    ACTION_RM -> {
                        val s = intent.getStringExtra(TEXT) ?: return
                        rmNote(s)
                    }
                    ACTION_CLEAR -> {
                        clearNotes(context)
                    }
                    ACTION_LS -> {
                        lsNotes(context)
                    }
                    ACTION_LOCK -> {
                        val text = intent.getStringExtra(TEXT)
                        val lock = intent.getBooleanExtra(LOCK, false)
                        lockNote(context, text!!, lock)
                    }
                    ACTION_CP -> {
                        val s = intent.getStringExtra(TEXT) ?: return
                        cpNote(s)
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(mContext.applicationContext)
            .registerReceiver(receiver, filter)
    }

    private fun invalidateNotes() {
        var header = this.header
        val mh = optionalPattern.matcher(header)
        while (mh.find()) {
            header = header.replace(
                mh.group(0),
                if (mh.groupCount() == 2) mh.group(if (notes.isNotEmpty()) 1 else 2) else Tuils.EMPTYSTRING
            )
        }

        if (header.isNotEmpty()) {
            var h = countPattern.matcher(header).replaceAll(notes.size.toString())
            h = Tuils.patternNewline.matcher(h).replaceAll(Tuils.NEWLINE)
            oldNotes = Tuils.span(h, this.color)
        } else {
            oldNotes = Tuils.EMPTYSTRING
        }

        var ns: CharSequence = Tuils.EMPTYSTRING
        for (j in notes.indices) {
            val n: Note = notes[j]

            var t: CharSequence = n.text
            t = lockPattern.matcher(t).replaceAll(n.lock.toString())
            t = rowPattern.matcher(t).replaceAll((j + 1).toString())
            t = countPattern.matcher(t).replaceAll(notes.size.toString())

            t = Tuils.span(t, if (n.lock) lockedColor else this.color)

            t = TimeManager.instance.replace(t, n.creationTime)

            if (allowLink) {
                val m = uriPattern.matcher(t)
                while (m.find()) {
                    var g = m.group()

                    //                    www.
                    if (g.startsWith("w")) {
                        g = "http://$g"
                    }

                    val u = g.toUri() ?: continue

                    val sp = SpannableString(m.group())
                    sp.setSpan(LongClickableSpan(u), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sp.setSpan(
                        ForegroundColorSpan(linkColor),
                        0,
                        sp.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    t = TextUtils.replace(t, arrayOf<String>(m.group()), arrayOf<CharSequence>(sp))
                }
            }

            ns = TextUtils.concat(ns, t, if (j != notes.size - 1) divider else Tuils.EMPTYSTRING)
        }

        oldNotes = TextUtils.concat(oldNotes, ns)

        var footer = this.footer
        val mf = optionalPattern.matcher(footer)
        while (mf.find()) {
            footer = footer.replace(
                mf.group(0),
                if (mf.groupCount() == 2) mf.group(if (notes.isNotEmpty()) 1 else 2) else Tuils.EMPTYSTRING
            )
        }

        if (footer.isNotEmpty()) {
            var h = countPattern.matcher(footer).replaceAll(notes.size.toString())
            h = Tuils.patternNewline.matcher(h).replaceAll(Tuils.NEWLINE)
            oldNotes = TextUtils.concat(oldNotes, Tuils.span(h, this.color))
        }

        val m = colorPattern.matcher(oldNotes)
        while (m.find()) {
            val match = m.group()
            val idColor = m.group(1)
            var t: CharSequence? = m.group(2)

            var color: Int
            if (idColor!!.startsWith("#")) {
//                    color
                color = try {
                    idColor.toColorInt()
                } catch (e: Exception) {
                    Color.RED
                }
            } else {
//                    id
                try {
                    val id = idColor.toInt()
                    val c = findClass(id)
                    color = c!!.color
                } catch (e: Exception) {
                    color = Color.RED
                }
            }

            t = Tuils.span(t.toString(), color)
            oldNotes = TextUtils.replace(oldNotes, arrayOf<String>(match), arrayOf<CharSequence>(t))
        }

        hasChanged = true
    }

    fun getOldNotesString(): CharSequence {
        hasChanged = false
        return oldNotes!!
    }

    fun addNote(s: String, lock: Boolean) {
        val t = System.currentTimeMillis()

        notes.add(Note(t, s, lock))
        notes.sort()

        val file = File(Tuils.getFolder(mContext), path)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, name)
        }

        val output = XMLPrefsManager.add(
            file,
            noteNode,
            arrayOf<String?>(CREATION_TIME, XMLPrefsManager.VALUE_ATTRIBUTE, LOCK),
            arrayOf<String?>(t.toString(), s, lock.toString())
        )
        if (output != null) {
            if (output.isNotEmpty()) Tuils.sendOutput(mContext, output)
            else Tuils.sendOutput(mContext, R.string.output_error)
        }

        invalidateNotes()
    }

    fun rmNote(s: String) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(mContext, R.string.note_not_found)
            return
        }

        val time: Long = notes.removeAt(index).creationTime

        val file = File(Tuils.getFolder(mContext), path)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, name)
        }

        val output = XMLPrefsManager.removeNode(
            file,
            arrayOf<String?>(CREATION_TIME),
            arrayOf(time.toString())
        )
        if (output != null) {
            if (output.isNotEmpty()) Tuils.sendOutput(mContext, output)
        }

        invalidateNotes()
    }

    fun cpNote(s: String) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(mContext, R.string.note_not_found)
            return
        }

        val text: String = notes[index].text

        (mContext as Activity).runOnUiThread {
            val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("note", text)
            clipboard.setPrimaryClip(clip)
            Tuils.sendOutput(mContext, mContext.getString(R.string.copied) + Tuils.SPACE + text)
        }
    }

    fun clearNotes(context: Context?) {
        val iterator: MutableIterator<Note> = notes.iterator()
        while (iterator.hasNext()) {
            val n = iterator.next()
            if (!n.lock) iterator.remove()
        }

        val file = File(Tuils.getFolder(context), path)
        if (!file.exists()) XMLPrefsManager.resetFile(file, name)

        val output = XMLPrefsManager.removeNode(
            file,
            arrayOf<String?>(LOCK),
            arrayOf(false.toString()),
            true,
            true
        )
        if (output != null && output.isNotEmpty()) Tuils.sendOutput(Color.RED, context, output)

        invalidateNotes()
    }

    fun lsNotes(c: Context?) {
        val builder = StringBuilder()

        for (j in notes.indices) {
            val n: Note = notes[j]
            builder.append(" - ").append(j + 1)
                .append(if (n.lock) " [locked]" else Tuils.EMPTYSTRING).append(" -> ")
                .append(n.text).append(Tuils.NEWLINE)
        }

        Tuils.sendOutput(c, builder.toString().trim { it <= ' ' })
    }

    fun lockNote(context: Context?, s: String, lock: Boolean) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(context, R.string.note_not_found)
            return
        }

        val n: Note = notes[index]
        n.lock = lock
        notes.sort()

        val time = n.creationTime

        val file = File(Tuils.getFolder(context), path)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, name)
        }

        val output = XMLPrefsManager.set(
            file,
            noteNode,
            arrayOf<String?>(CREATION_TIME),
            arrayOf(time.toString()),
            arrayOf<String?>(
                LOCK
            ),
            arrayOf(lock.toString()),
            true
        )
        if (output != null && output.isNotEmpty()) Tuils.sendOutput(context, output)

        invalidateNotes()
    }

    fun findNote(s: String): Int {
        var s = s
        try {
            val index = s.toInt() - 1
            if (index < 0 || index >= notes.size) return -1
            return index
        } catch (e: Exception) {
        }

        s = s.lowercase(Locale.getDefault()).trim { it <= ' ' }

        var note: CharSequence
        var c = 0
        while (c < notes.size) {
            val n: Note = notes[c]

            var text = n.text

            text = lockPattern.matcher(text).replaceAll(n.lock.toString())
            text = rowPattern.matcher(text).replaceAll((c + 1).toString())
            text = countPattern.matcher(text).replaceAll(notes.size.toString())

            note = text

            val m = colorPattern.matcher(notes[c].text)
            while (m.find()) {
                val match = m.group()
                val idColor = m.group(1)
                var t: CharSequence? = m.group(2)

                var color: Int
                if (idColor!!.startsWith("#")) {
//                    color
                    color = try {
                        idColor.toColorInt()
                    } catch (e: Exception) {
                        Color.RED
                    }
                } else {
//                    id
                    try {
                        val id = idColor.toInt()
                        val cl = findClass(id)
                        color = cl!!.color
                    } catch (e: Exception) {
                        color = Color.RED
                    }
                }

                t = Tuils.span(t.toString(), color)
                note = TextUtils.replace(note, arrayOf<String>(match), arrayOf<CharSequence>(t))
            }

            if (note.toString().lowercase(Locale.getDefault()).startsWith(s)) break
            c++
        }

        if (c == notes.size) {
            return -1
        }
        return c
    }

    fun findClass(id: Int): Class? {
        val classIterator = classes.iterator()
        while (classIterator.hasNext()) {
            val cl = classIterator.next()
            if (cl?.id == id) return cl
        }

        return null
    }

    fun dispose(context: Context) {
        LocalBroadcastManager.getInstance(context.applicationContext)
            .unregisterReceiver(receiver)
    }

    class Class(var id: Int, var color: Int)

    class Note(var creationTime: Long, var text: String, var lock: Boolean) :
        Comparable<Note?> {
        override fun compareTo(o: Note?): Int {
            if (o == null) return 1
            when (sorting) {
                SORTING_TIME_UPDOWN -> return (creationTime - o.creationTime).toInt()
                SORTING_TIME_DOWNUP -> return (o.creationTime - creationTime).toInt()
                SORTING_ALPHA_UPDOWN -> return Tuils.alphabeticCompare(text, o.text)
                SORTING_ALPHA_DOWNUP -> return Tuils.alphabeticCompare(o.text, text)
                SORTING_LOCK_BEFORE -> if (lock) {
                    if (o.lock) return 0
                    return -1
                } else {
                    if (o.lock) return 1
                    return 0
                }

                SORTING_UNLOCK_BEFORE -> if (lock) {
                    if (o.lock) return 0
                    return 1
                } else {
                    if (o.lock) return -1
                    return 0
                }

                else -> return 1
            }
        }

        override fun toString(): String {
            return "$creationTime : $text"
        }

        companion object {
            private const val SORTING_TIME_UPDOWN = 0
            private const val SORTING_TIME_DOWNUP = 1
            private const val SORTING_ALPHA_UPDOWN = 2
            private const val SORTING_ALPHA_DOWNUP = 3
            private const val SORTING_LOCK_BEFORE = 4
            private const val SORTING_UNLOCK_BEFORE = 5

            var sorting: Int = Int.MAX_VALUE
        }
    }

    companion object {
        @JvmField
        var ACTION_RM: String = BuildConfig.APPLICATION_ID + ".rm_note"
        @JvmField
        var ACTION_ADD: String = BuildConfig.APPLICATION_ID + ".add_note"
        @JvmField
        var ACTION_CLEAR: String = BuildConfig.APPLICATION_ID + ".clear_notes"
        @JvmField
        var ACTION_LS: String = BuildConfig.APPLICATION_ID + ".ls_notes"
        @JvmField
        var ACTION_LOCK: String = BuildConfig.APPLICATION_ID + ".lock_notes"
        @JvmField
        var ACTION_CP: String = BuildConfig.APPLICATION_ID + ".cp_notes"

        @JvmField
        var BROADCAST_COUNT: String = "broadcastCount"
        var CREATION_TIME: String = "creationTime"
        @JvmField
        var TEXT: String = "text"
        @JvmField
        var LOCK: String = "lock"

        @JvmField
        var broadcastCount: Int = 0
    }
}
