package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import java.util.regex.Pattern

object ViewTuils {

    private var globalTypeface: Typeface? = null
    @JvmField
    var fontPath: String? = null

    private val fontFilePattern = Pattern.compile(".[ot]tf$")

    @JvmStatic
    fun getTypeface(context: Context): Typeface? {
        globalTypeface?.let { return it }

        try {
            XMLPrefsManager.loadCommons(context)
        } catch (e: Exception) {
            return null
        }

        val systemFont = XMLPrefsManager.getBoolean(Ui.system_font)
        val fallback = { if (systemFont) Typeface.DEFAULT else Typeface.createFromAsset(context.assets, "lucida_console.ttf") }

        if (systemFont) {
            return fallback().also { globalTypeface = it }
        }

        val folder = FileTuils.getFolder(context) ?: return fallback()

        val font = folder.listFiles()?.firstOrNull { fontFilePattern.matcher(it.name).find() }
        if (font != null) {
            fontPath = font.absolutePath
            globalTypeface = try {
                Typeface.createFromFile(font) ?: throw UnsupportedOperationException()
            } catch (e: Exception) {
                null
            }
        }

        return globalTypeface ?: fallback().also { globalTypeface = it }
    }

    @JvmStatic
    fun cancelFont() {
        globalTypeface = null
        fontPath = null
    }

    @JvmStatic
    @JvmOverloads
    fun span(
        text: CharSequence?,
        foreColor: Int? = null,
        bgColor: Int? = null,
        size: Int? = null,
        context: Context? = null
    ): SpannableString {
        val spannable = (text ?: Tuils.EMPTYSTRING).let { if (it is SpannableString) it else SpannableString(it) }
        val end = spannable.length

        if (size != null && context != null) {
            spannable.setSpan(AbsoluteSizeSpan(convertSpToPixels(size.toFloat(), context)), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (foreColor != null) {
            spannable.setSpan(ForegroundColorSpan(foreColor), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (bgColor != null) {
            spannable.setSpan(BackgroundColorSpan(bgColor), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable
    }

    @JvmStatic
    fun highlightSubstring(bgColor: Int, text: SpannableString, section: String, fromIndex: Int): Int {
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
    fun dpToPx(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp * density)
    }

    @JvmStatic
    fun mmToPx(metrics: DisplayMetrics, mm: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm.toFloat(), metrics).toInt()
    }
}
