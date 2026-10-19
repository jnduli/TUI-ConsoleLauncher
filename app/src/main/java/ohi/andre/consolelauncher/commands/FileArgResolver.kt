package ohi.andre.consolelauncher.commands

import java.io.File
import ohi.andre.consolelauncher.managers.FileManager
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.Tuils

/** Resolves the [CommandAbstraction.FILE] arg type: quoted paths and plain paths. */
object FileArgResolver {

    @Suppress("UNCHECKED_CAST")
    fun file(input: String, cd: File): ArgInfo {
        val trimmed = input.trim()
        if ((trimmed.startsWith("\"") || trimmed.startsWith("'")) &&
            (trimmed.substring(1).contains("\"") || trimmed.substring(1).contains("'"))
        ) {
            val afterFirst = trimmed.substring(1)

            var endIndex = afterFirst.indexOf("\"")
            if (endIndex == -1) endIndex = afterFirst.indexOf("'")

            if (endIndex != -1) {
                val file = afterFirst.substring(0, endIndex)
                val residual = afterFirst.substring(endIndex + 1)

                val f = if (afterFirst.startsWith("/")) File(file) else File(cd, file)

                return ArgInfo(f, residual, true, 1)
            }
        }

        val strings = ArgExtractors.textList(trimmed).arg as MutableList<String>

        var toVerify = Tuils.EMPTYSTRING
        var count = 0
        while (count < strings.size) {
            toVerify = toVerify + strings[count]

            val info = getFile(toVerify, cd)
            if (info.file != null && info.notFound == null) {
                var c = count
                while (c-- >= 0) strings.removeAt(0)

                val residual = StringTuils.toPlanString(strings, Tuils.SPACE)
                return ArgInfo(info.file, residual, true, 1)
            }

            toVerify = toVerify + Tuils.SPACE
            count++
        }

        return ArgInfo(null, trimmed, false, 0)
    }

    private fun getFile(path: String, cd: File): FileManager.DirInfo = FileManager.cd(cd, path)
}
