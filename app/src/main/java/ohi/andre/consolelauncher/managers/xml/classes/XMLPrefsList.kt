package ohi.andre.consolelauncher.managers.xml.classes

interface XMLPrefsList {
    fun get(o: Any?): XMLPrefsEntry?
    fun add(key: String, value: String?)
    fun size(): Int
    fun values(): List<String>
    fun clear()
}
