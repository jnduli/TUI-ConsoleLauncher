package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;

/**
 * Created by andre on 07/11/15.
 */
public class clear implements CommandAbstraction {

    @Override
    public String exec(CommandContext info) throws Exception {
        LocalBroadcastManager.getInstance(info.context.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_CLEAR));
        return null;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_clear;
    }

    @Override
    public String onArgNotFound(CommandContext info, int index) {
        return null;
    }

    @Override
    public String onNotArgEnough(CommandContext info, int nArgs) {
        return null;
    }
}
