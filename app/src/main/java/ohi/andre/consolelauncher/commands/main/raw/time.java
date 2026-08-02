package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.managers.TimeManager;

/**
 * Created by andre on 03/12/15.
 */
public class time implements CommandAbstraction {
    @Override
    public String exec(CommandContext pack) {
        int index = pack.getInt();
        return TimeManager.instance.replace("%t" + index).toString();
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.INT};
    }

    @Override
    public int helpRes() {
        return R.string.help_time;
    }

    @Override
    public String onArgNotFound(CommandContext info, int index) {
        return info.context.getString(R.string.invalid_integer);
    }

    @Override
    public String onNotArgEnough(CommandContext info, int nArgs) {
        return TimeManager.instance.replace("%t0").toString();
    }
}
