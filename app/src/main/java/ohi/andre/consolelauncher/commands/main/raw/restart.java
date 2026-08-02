package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class restart implements CommandAbstraction {

    @Override
    public String exec(CommandContext pack) {
        CommandContext info = (CommandContext) pack;
        ((Reloadable) info.context).reload();

        return pack.context.getString(R.string.restarting);
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
        return R.string.help_restart;
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
