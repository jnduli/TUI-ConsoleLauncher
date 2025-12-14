package ohi.andre.consolelauncher

import java.text.DecimalFormat
import android.app.ActivityManager
import android.os.Handler
import ohi.andre.consolelauncher.UIManager.Label
import ohi.andre.consolelauncher.managers.TimeManager


abstract class UIRunnable(val uiManager: UIManager, val handler: Handler, val label: UIManager.Label, val rerunDelayMillis: Long) : Runnable{

    abstract fun text(): CharSequence

    override fun run() {
        uiManager.updateText(label, text())
        handler.postDelayed(this, rerunDelayMillis)
    }
}

class TimeRunnable(uiManager: UIManager, handler: Handler) : UIRunnable(uiManager, handler, label=Label.time, rerunDelayMillis = 1000) {

    override fun text(): CharSequence {
        return TimeManager.instance.getCharSequence(
            uiManager.mContext,
            uiManager.getLabelSize(label),
            "%t0"
        )
    }
}

/**
 * An object containing the logic and constants for converting raw byte counts
 * into human-readable strings (KB, MB, GB, etc.).
 */
object ByteFormatter {
    
    // Using binary multipliers (powers of 1024) - Standard for OS/memory
    private const val KILOBYTE: Long = 1024
    private const val MEGABYTE: Long = KILOBYTE * 1024
    private const val GIGABYTE: Long = MEGABYTE * 1024
    private const val TERABYTE: Long = GIGABYTE * 1024
    private const val PETABYTE: Long = TERABYTE * 1024
    
    // Used for formatting the output string to two decimal places
    private val DECIMAL_FORMAT = DecimalFormat("#.##")
    
    fun toHumanReadableSize(bytes: Long): String {
        // Handle negative or zero bytes
        if (bytes <= 0) return "0 Bytes"

        // Determine the correct unit and calculate the result
        return when {
            bytes >= PETABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / PETABYTE)} PB"
            bytes >= TERABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / TERABYTE)} TB"
            bytes >= GIGABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / GIGABYTE)} GB"
            bytes >= MEGABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / MEGABYTE)} MB"
            bytes >= KILOBYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / KILOBYTE)} KB"

            else ->
                "$bytes Bytes" // Less than 1 KB
        }.toString()
    }
}


class RamRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.ram, rerunDelayMillis = 3000) {

    override fun text(): CharSequence {
        val activityManager = uiManager.activityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val available = memory.availMem
        // TODO: increase api version support to the one for this
        val totalMem = memory.totalMem
        return ByteFormatter.toHumanReadableSize(available) + " / " + ByteFormatter.toHumanReadableSize(totalMem)
    }

}
/**

    private inner class RamRunnable : Runnable {
        private val AV = "%av"
        private val TOT = "%tot"

        var ramPatterns: MutableList<Pattern?>? = null
        var ramFormat: String? = null

        var color: Int = 0

        override fun run() {
            if (ramFormat == null) {
                ramFormat = XMLPrefsManager.get(Behavior.ram_format) as String?

                color = XMLPrefsManager.getColor(Theme.ram_color)
            }

            if (ramPatterns == null) {
                ramPatterns = ArrayList<Pattern?>()

                ramPatterns!!.add(
                    Pattern.compile(
                        AV + "tb",
                        Pattern.CASE_INSENSITIVE or Pattern.LITERAL
                    )
                )
                ramPatterns!!.add(
                    Pattern.compile(
                        AV + "gb",
                        Pattern.CASE_INSENSITIVE or Pattern.LITERAL
                    )
                )
                ramPatterns!!.add(
                    Pattern.compile(
                        AV + "mb",
                        Pattern.CASE_INSENSITIVE or Pattern.LITERAL
                    )
                )
                ramPatterns!!.add(
                    Pattern.compile(
                        AV + "kb",
                        Pattern.CASE_INSENSITIVE or Pattern.LITERAL
                    )
                )
                ramPatterns!!.add(
                    Pattern.compil
 **/