package ohi.andre.consolelauncher.managers.xml.classes

import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Created by francescoandreuzzi on 06/03/2018.
 */
class DomXMLPrefsList : XMLPrefsList {

    private val list: MutableList<XMLPrefsEntry> = ArrayList()

    fun add(entry: XMLPrefsEntry) {
        list.add(entry)
    }

    override fun add(key: String, value: String?) {
        list.add(XMLPrefsEntry(key, value))
    }

    override fun get(o: Any?): XMLPrefsEntry? {
        if (o is Int) return at(o)

        for (e in list) if (e == o) return e
        return null
    }

    fun at(index: Int): XMLPrefsEntry = list[index]

    override fun size(): Int = list.size

    override fun values(): List<String> {
        val vs = ArrayList<String>()
        for (entry in list) vs.add(entry.key + "=" + entry.value)
        return vs
    }

    override fun clear() {
        list.clear()
    }

    override fun toString(): String {
        val builder = StringBuilder()

        for (entry in list) {
            builder.append(entry.key).append(" -> ").append(entry.value).append(Tuils.NEWLINE)
        }

        return builder.toString().trim()
    }
}
