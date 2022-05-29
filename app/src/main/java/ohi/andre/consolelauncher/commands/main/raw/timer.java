package ohi.andre.consolelauncher.commands.main.raw;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.AlarmClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;

public class timer implements CommandAbstraction {
    @Override
    public String exec(ExecutePack pack) throws Exception {
        if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.SET_ALARM) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) pack.context, new String[]{Manifest.permission.SET_ALARM}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
            return pack.context.getString(R.string.output_waitingpermission);
        }

        String time = pack.getString();
        char lastChar = time.charAt(time.length()-1);
        String substring = time.substring(0, time.length() - 1);
        int seconds;
        try {
            seconds = Integer.parseInt(substring);
        } catch (NumberFormatException e) {
            return String.format("The time: %s is invalid.", substring);
        }
        if (lastChar == 'm') {
            seconds = seconds * 60;
        }
        // be harder on the time conditions here, ie check for s, otherwise  exception

        Intent timerIntent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "started from tui")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        pack.context.startActivity(timerIntent);
        return Integer.toString(seconds) + " seconds";
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public int helpRes() {
        return R.string.help_timer;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        MainPack info = (MainPack) pack;
        return info.res.getString(R.string.output_appnotfound);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        MainPack info = (MainPack) pack;
        return info.res.getString(helpRes());
    }
}
