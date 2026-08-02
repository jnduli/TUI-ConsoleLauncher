package ohi.andre.consolelauncher.commands.main.raw;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand;
import ohi.andre.consolelauncher.tuils.interfaces.Redirectator;

/**
 * Created by francescoandreuzzi on 02/03/2017.
 */

public class sms extends RedirectCommand {

    private final Redirectator redirectator;

    public sms(Redirectator redirectator) {
        this.redirectator = redirectator;
    }

    @Override
    public String exec(CommandContext pack) throws Exception {
        if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions((Activity) pack.context, new String[]{Manifest.permission.READ_CONTACTS}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
            return pack.context.getString(R.string.output_waitingpermission);
        }

        beforeObjects.add(pack.getString());

        if(afterObjects.size() == 0) {
            redirectator.prepareRedirection(this);
        } else {
            return onRedirect(pack);
        }

        return null;
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.CONTACTNUMBER};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_sms;
    }

    @Override
    public String onNotArgEnough(CommandContext pack, int nArgs) {
        return pack.context.getString(helpRes());
    }

    @Override
    public String onArgNotFound(CommandContext pack, int index) {
        return pack.res.getString(R.string.output_numbernotfound);
    }

    @Override
    public String onRedirect(CommandContext pack) {
        String number = (String) beforeObjects.get(0);
        String message = (String) afterObjects.get(0);
        if(message.length() == 0) {
            redirectator.cleanup();
            return pack.res.getString(R.string.output_smsnotsent);
        }

        if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) pack.context, new String[]{Manifest.permission.SEND_SMS}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
            return pack.context.getString(R.string.output_waitingpermission);
        }

        redirectator.cleanup();

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, message, null, null);

            cleanup();

            return pack.res.getString(R.string.output_smssent);
        } catch (Exception ex) {
            cleanup();

            return ex.toString();
        }
    }

    @Override
    public int getHint() {
        return R.string.sms_hint;
    }

    @Override
    public boolean isWaitingPermission() {
        return beforeObjects.size() == 1 && afterObjects.size() == 1;
    }
}
