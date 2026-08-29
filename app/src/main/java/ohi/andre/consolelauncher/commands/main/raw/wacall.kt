package ohi.andre.consolelauncher.commands.main.raw

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.tuils.LogTuils

class wacall : CommandAbstraction {

    override fun exec(pack: CommandContext): String? {
        val context = pack.context

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            LogTuils.log("wacall: READ_CONTACTS not granted, requesting permission")
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.READ_CONTACTS),
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            return context.getString(R.string.output_waitingpermission)
        }

        val number = pack.getString()
        val sanitized = sanitizeNumber(number)
        if (sanitized.isEmpty()) return context.getString(R.string.invalid_number)

        val contactId = findContactId(context, number)
        val callRow = contactId?.let { findWhatsAppCallRow(context, it) }

        val intent = if (callRow != null) {
            LogTuils.log("wacall: direct-call row found, mimetype=${callRow.second}")
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, callRow.first),
                    callRow.second
                )
            }
        } else {
            LogTuils.log("wacall: no direct-call row found (${installedPackagesSummary(context)}), falling back to wa.me")
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$sanitized"))
        }

        return try {
            context.startActivity(intent)
            context.getString(R.string.calling) + " " + number
        } catch (e: ActivityNotFoundException) {
            LogTuils.log("wacall: no app found to handle intent", e)
            context.getString(R.string.output_nopermissions)
        } catch (e: SecurityException) {
            LogTuils.log("wacall: security exception launching intent", e)
            context.getString(R.string.output_nopermissions)
        }
    }

    override fun helpRes(): Int = R.string.help_wacall

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.CONTACTNUMBER)

    override fun priority(): Int = 5

    override fun onNotArgEnough(pack: CommandContext, nArgs: Int): String? {
        return pack.context.getString(helpRes())
    }

    override fun onArgNotFound(pack: CommandContext, indexNotFound: Int): String? {
        return pack.context.getString(R.string.output_numbernotfound)
    }

    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        private fun sanitizeNumber(number: String): String = number.filter { it.isDigit() }

        private fun findContactId(context: Context, number: String): String? {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                arrayOf(number),
                null
            ) ?: return null

            cursor.use {
                if (!it.moveToFirst()) return null
                return it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
            }
        }

        private fun findWhatsAppCallRow(context: Context, contactId: String): Pair<Long, String>? {
            val cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.RES_PACKAGE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} LIKE ?",
                arrayOf(contactId, "%whatsapp%"),
                null
            ) ?: return null

            var regularMatch: Pair<Long, String>? = null
            var businessMatch: Pair<Long, String>? = null

            cursor.use {
                val idCol = it.getColumnIndex(ContactsContract.Data._ID)
                val mimeCol = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val pkgCol = it.getColumnIndex(ContactsContract.Data.RES_PACKAGE)

                while (it.moveToNext()) {
                    val mime = it.getString(mimeCol) ?: continue
                    LogTuils.log("wacall: whatsapp-related mimetype seen: $mime")

                    val lower = mime.lowercase()
                    if (lower.contains("video")) continue
                    if (!lower.contains("call") && !lower.contains("voip")) continue

                    val match = it.getLong(idCol) to mime
                    if (pkgCol >= 0 && it.getString(pkgCol) == WHATSAPP_BUSINESS_PACKAGE) {
                        if (businessMatch == null) businessMatch = match
                    } else {
                        if (regularMatch == null) regularMatch = match
                    }
                }
            }

            return regularMatch ?: businessMatch
        }

        private fun installedPackagesSummary(context: Context): String {
            val pm = context.packageManager
            return "whatsapp installed=${isPackageInstalled(pm, WHATSAPP_PACKAGE)}, " +
                "business installed=${isPackageInstalled(pm, WHATSAPP_BUSINESS_PACKAGE)}"
        }

        private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
            return try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
