/**
 * Ref: https://developer.android.com/reference/android/provider/AlarmClock
 */
package ohi.andre.consolelauncher.commands.main.raw;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.AlarmClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;

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
        ArrayList<String> args = pack.getList();
        if (args.size() < 1) {
            return "Timer must have at least one argument";
        }

        String time = args.get(0);
        char lastChar = Character.toLowerCase(time.charAt(time.length()-1));
        String substring = time.substring(0, time.length() - 1);
        String invalidFormat = String.format("The time: %s is invalid. Expected format is 5m or 5s or 5h", substring);

        int seconds;
        try {
            seconds = Integer.parseInt(substring);
        } catch (NumberFormatException e) {
            return invalidFormat;
        }
        if (lastChar == 's') {
            seconds = seconds;
        } else if (lastChar == 'm') {
            seconds = seconds * 60;
        } else if (lastChar == 'h') {
            seconds = seconds * 3600;
        } else {
            return invalidFormat;
        }

        if (seconds > (60 * 60 * 24)) {
            return "The timer cannot be longer than 24 hours";
        }
        // be harder on the time conditions here, ie check for s, otherwise  exception
        String alarmTitle = "started from tui";
        if (args.size() == 2) {
            alarmTitle = args.get(1);
        }
        Intent timerIntent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, alarmTitle)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        pack.context.startActivity(timerIntent);
        return String.format("Timer started for %s", time);
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.TEXTLIST};
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
