package ohi.andre.consolelauncher.commands.main.raw

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.tuils.Tuils

class call : CommandAbstraction {

    override fun exec(pack: CommandContext): String? {
        if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(pack.context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                pack.context as Activity,
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE),
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            return pack.context.getString(R.string.output_waitingpermission)
        }

        val number = pack.getString() ?: return pack.context.getString(R.string.invalid_number)

        val s = StringBuilder(Tuils.EMPTYSTRING)
        for (c in number) {
            if (c == '#') s.append(Uri.encode("#")) else s.append(c)
        }

        val uri = Uri.parse("tel:$s") ?: return pack.context.getString(R.string.invalid_number)

        val intent = Intent(Intent.ACTION_CALL, uri)

        return try {
            (pack.context as Activity).runOnUiThread { pack.context.startActivity(intent) }
            pack.res.getString(R.string.calling) + " " + number
        } catch (e: SecurityException) {
            pack.res.getString(R.string.output_nopermissions)
        }
    }

    override fun helpRes(): Int = R.string.help_call

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.CONTACTNUMBER)

    override fun priority(): Int = 5

    override fun onNotArgEnough(pack: CommandContext, nArgs: Int): String? {
        return pack.context.getString(helpRes())
    }

    override fun onArgNotFound(pack: CommandContext, indexNotFound: Int): String? {
        return pack.res.getString(R.string.output_numbernotfound)
    }
}
