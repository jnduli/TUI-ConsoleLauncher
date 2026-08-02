package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Parcelable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.TerminalManager
import org.xml.sax.SAXParseException

object OutputTuils {

    @JvmStatic
    @JvmOverloads
    fun sendOutput(
        context: Context?,
        text: CharSequence?,
        color: Int = Integer.MAX_VALUE,
        type: Int = TerminalManager.CATEGORY_OUTPUT,
        action: Any? = null,
        longAction: Any? = null
    ) {
        val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, text)
        intent.putExtra(PrivateIOReceiver.COLOR, color)
        intent.putExtra(PrivateIOReceiver.TYPE, type)

        if (action is String) intent.putExtra(PrivateIOReceiver.ACTION, action)
        else if (action is Parcelable) intent.putExtra(PrivateIOReceiver.ACTION, action)

        if (longAction is String) intent.putExtra(PrivateIOReceiver.LONG_ACTION, longAction)
        else if (longAction is Parcelable) intent.putExtra(PrivateIOReceiver.LONG_ACTION, longAction)

        LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
    }

    @JvmStatic
    @JvmOverloads
    fun sendOutput(context: Context?, res: Int, color: Int = Integer.MAX_VALUE) {
        sendOutput(context, context!!.getString(res), color)
    }

    @JvmStatic
    fun sendInput(context: Context, text: String?) {
        val intent = Intent(PrivateIOReceiver.ACTION_INPUT)
        intent.putExtra(PrivateIOReceiver.TEXT, text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    @JvmStatic
    fun sendXMLParseError(context: Context?, PATH: String, e: SAXParseException) {
        sendOutput(
            context,
            context!!.getString(R.string.output_xmlproblem1) + Tuils.SPACE + PATH + context.getString(R.string.output_xmlproblem2) + Tuils.NEWLINE + context.getString(R.string.output_errorlabel) +
                "File: " + e.systemId + Tuils.NEWLINE +
                "Message" + e.message + Tuils.NEWLINE +
                "Line" + e.lineNumber + Tuils.NEWLINE +
                "Column" + e.columnNumber,
            Color.RED
        )
    }

    @JvmStatic
    fun sendXMLParseError(context: Context?, PATH: String) {
        sendOutput(context, context!!.getString(R.string.output_xmlproblem1) + Tuils.SPACE + PATH + context.getString(R.string.output_xmlproblem2), Color.RED)
    }
}
