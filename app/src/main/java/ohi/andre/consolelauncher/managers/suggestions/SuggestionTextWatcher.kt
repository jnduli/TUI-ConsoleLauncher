package ohi.andre.consolelauncher.managers.suggestions

import android.text.Editable
import android.text.TextWatcher
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged

/**
 * Created by francescoandreuzzi on 06/03/2018.
 */
class SuggestionTextWatcher(
    var suggestionsManager: SuggestionsManager,
    var textChanged: OnTextChanged
) : TextWatcher {
    var before: Int = Int.Companion.MIN_VALUE

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {
        suggestionsManager.requestSuggestion(s.toString())

        textChanged.textChanged(s.toString(), before)
        before = s.length
    }

    override fun afterTextChanged(s: Editable?) {}
}
