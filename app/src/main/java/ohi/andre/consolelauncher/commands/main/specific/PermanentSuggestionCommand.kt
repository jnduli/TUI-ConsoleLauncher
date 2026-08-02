package ohi.andre.consolelauncher.commands.main.specific

import ohi.andre.consolelauncher.commands.CommandAbstraction

abstract class PermanentSuggestionCommand : CommandAbstraction {

    abstract fun permanentSuggestions(): Array<String>
}
