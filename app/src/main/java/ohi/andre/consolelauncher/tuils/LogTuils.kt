package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Array as ReflectArray

object LogTuils {

    @JvmStatic
    fun log(o: Any?) {
        when (o) {
            is Throwable -> Log.e("andre", "", o)
            is Array<*> -> Log.e("andre", o.contentToString())
            else -> Log.e("andre", o.toString())
        }
    }

    @JvmStatic
    fun log(o: Any?, o2: Any?) {
        val first = if (o is Array<*>) o.contentToString() else o.toString()
        val second = if (o2 is Array<*>) o2.contentToString() else o2.toString()
        Log.e("andre", "$first -- $second")
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
            File(FileTuils.getFolder(c), "crash.txt").outputStream().use { stream ->
                stream.write((Tuils.NEWLINE + Tuils.NEWLINE).toByteArray())

                if (o is Throwable) {
                    o.printStackTrace(PrintStream(stream))
                } else {
                    stream.write(o.toString().toByteArray())
                }

                stream.write((Tuils.NEWLINE + "----------------------------").toByteArray())
            }
        } catch (e1: Exception) {
        }
    }

    @JvmStatic
    fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw, true)
        throwable.printStackTrace(pw)
        return sw.buffer.toString()
    }
}
