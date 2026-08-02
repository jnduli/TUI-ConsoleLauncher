package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.managers.ChangelogManager;

import okhttp3.OkHttpClient;

/**
 * Created by francescoandreuzzi on 26/03/2018.
 */

public class changelog implements CommandAbstraction {

    private final OkHttpClient client;

    public changelog(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public String exec(CommandContext pack) throws Exception {
        ChangelogManager.printLog(pack.context, client, true);
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
        return R.string.help_changelog;
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
