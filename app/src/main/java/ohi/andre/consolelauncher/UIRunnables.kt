package ohi.andre.consolelauncher

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