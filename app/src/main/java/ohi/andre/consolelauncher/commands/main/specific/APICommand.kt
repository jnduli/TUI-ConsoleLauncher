package ohi.andre.consolelauncher.commands.main.specific

interface APICommand {

    fun willWorkOn(api: Int): Boolean
}
