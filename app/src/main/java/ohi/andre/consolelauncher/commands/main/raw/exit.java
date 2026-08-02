package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.tuils.SystemUtils;

/**
 * Created by francescoandreuzzi on 21/05/2017.
 */

public class exit implements CommandAbstraction {
    @Override
    public String exec(CommandContext pack) throws Exception {
        SystemUtils.resetPreferredLauncherAndOpenChooser(pack.context);
        return null;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_exit;
    }

    @Override
    public String onArgNotFound(CommandContext pack, int indexNotFound) {
        return null;
    }

    @Override
    public String onNotArgEnough(CommandContext pack, int nArgs) {
        return null;
    }
}
