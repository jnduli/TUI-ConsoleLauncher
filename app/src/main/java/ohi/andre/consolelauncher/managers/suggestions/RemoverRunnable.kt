package ohi.andre.consolelauncher.managers.suggestions

import android.widget.LinearLayout

/**
 * Created by francescoandreuzzi on 11/03/2018.
 */
class RemoverRunnable(var suggestionsView: LinearLayout) : Runnable {
    var stop: Boolean = false
    var isGoingToRun: Boolean = false

    override fun run() {
        if (stop) {
            stop = false
        } else suggestionsView.removeAllViews()

        isGoingToRun = false
    }
}
