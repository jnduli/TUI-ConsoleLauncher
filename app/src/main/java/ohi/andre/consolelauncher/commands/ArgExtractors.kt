package ohi.andre.consolelauncher.commands

import android.graphics.Color
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.Tuils

/** Per-[CommandAbstraction] arg-type extractors that are plain string parsing or manager lookups. */
object ArgExtractors {

    fun dataStoreType(input: String): ArgInfo? {
        val a = noSpaceString(input) ?: return null
        if (a.found) {
            val s = a.arg as String
            return try {
                HTMLExtractManager.StoreableValue.Type.valueOf(s)
                a
            } catch (e: Exception) {
                null
            }
        }

        a.found = false
        return a
    }

    fun numberLong(input: String): ArgInfo {
        val split = input.split(Tuils.SPACE)

        return try {
            val l = split[0].toLong()

            val builder = StringBuilder()
            for (c in 1 until split.size) {
                builder.append(split[c]).append(Tuils.SPACE)
            }
            ArgInfo(l, builder.toString().trim(), true, 1)
        } catch (e: Exception) {
            ArgInfo(null, input, false, 0)
        }
    }

    fun color(input: String): ArgInfo {
        val trimmed = input.trim()

        val space = trimmed.indexOf(Tuils.SPACE)
        val cl = trimmed.substring(0, if (space == -1) trimmed.length else space)
        val residual = if (space == -1) Tuils.EMPTYSTRING else trimmed.substring(space + 1)

        return try {
            Color.parseColor(cl)
            ArgInfo(cl, residual, true, 1)
        } catch (e: Exception) {
            ArgInfo(null, residual, false, 0)
        }
    }

    fun bln(input: String): ArgInfo {
        val used: String
        val notUsed: String?
        if (input.contains(Tuils.SPACE)) {
            val space = input.indexOf(Tuils.SPACE)
            used = input.substring(0, space)
            notUsed = input.substring(space + 1)
        } else {
            used = input
            notUsed = null
        }

        val result = used.toBoolean()
        return ArgInfo(result, notUsed, used.isNotEmpty(), if (used.isNotEmpty()) 1 else 0)
    }

    fun plainText(input: String?): ArgInfo? {
        if (input == null) return null

        var index = input.indexOf(Tuils.SPACE)
        if (index == -1) index = input.length

        return ArgInfo(
            input.substring(0, index),
            if (input.length > index) input.substring(index + 1) else null,
            true,
            1
        )
    }

    fun textList(input: String?): ArgInfo? {
        if (input == null) return null

        // Kotlin's Regex split, unlike Java's String.split, keeps trailing empty matches — drop
        // them to match the original Java semantics this parsing logic relies on.
        val strings = input.split(Regex(Tuils.SPACE + "+")).toMutableList()
        while (strings.isNotEmpty() && strings.last().isEmpty()) strings.removeAt(strings.size - 1)

        return ArgInfo(strings, null, true, strings.size)
    }

    fun noSpaceString(input: String?): ArgInfo? {
        if (input == null) return null

        var index = input.indexOf(Tuils.SPACE)
        if (index == -1) index = input.length

        return ArgInfo(
            input.substring(0, index),
            if (input.length > index) input.substring(index + 1) else null,
            true,
            1
        )
    }

    fun command(string: String, active: CommandGroup): ArgInfo {
        val i = noSpaceString(string)!!
        val name = i.arg as String

        var abstraction: CommandAbstraction? = null
        try {
            abstraction = active.getCommandByName(name)
        } catch (e: Exception) {
            LogTuils.log(e)
            LogTuils.toFile(e)
        }

        return ArgInfo(abstraction, i.residualString, abstraction != null, 1)
    }

    fun activityName(input: String, apps: AppsManager): ArgInfo {
        val info = apps.findLaunchableWithLabel(input, AppsManager.SHOWN_APPS)
        return ArgInfo(info, null, info != null, if (info != null) 1 else 0)
    }

    fun hiddenPackage(input: String, apps: AppsManager): ArgInfo {
        val info = apps.findLaunchableWithLabel(input, AppsManager.HIDDEN_APPS)
        return ArgInfo(info, null, info != null, if (info != null) 1 else 0)
    }

    fun allPackages(input: String, apps: AppsManager): ArgInfo {
        var info = apps.findLaunchableWithLabel(input, AppsManager.SHOWN_APPS)
        if (info == null) {
            info = apps.findLaunchableWithLabel(input, AppsManager.HIDDEN_APPS)
        }

        return ArgInfo(info, null, info != null, if (info != null) 1 else 0)
    }

    fun defaultApp(input: String, apps: AppsManager): ArgInfo {
        val info = apps.findLaunchableWithLabel(input, AppsManager.SHOWN_APPS)
        return if (info == null) ArgInfo(input, null, true, 1) else ArgInfo(info, null, true, 1)
    }

    fun contactNumber(input: String, contacts: ContactManager?): ArgInfo {
        val number = when {
            StringTuils.isPhoneNumber(input) -> input
            contacts == null -> null
            else -> contacts.findNumber(input)
        }

        return ArgInfo(number, null, number != null, 1)
    }

    fun integer(input: String): ArgInfo {
        val index = input.indexOf(Tuils.SPACE)
        val s = if (index == -1) input else input.substring(0, index)

        val n = try {
            s.toInt()
        } catch (e: NumberFormatException) {
            return ArgInfo(null, input, false, 0)
        }

        return ArgInfo(n, if (index == -1) null else input.substring(index + 1), true, 1)
    }
}
