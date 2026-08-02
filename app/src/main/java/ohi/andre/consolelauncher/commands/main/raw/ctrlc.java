package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;

import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.tuils.StoppableThread;
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder;

/**
 * Created by francescoandreuzzi on 26/07/2017.
 */

public class ctrlc implements CommandAbstraction {

    private final ShellHolder shellHolder;

    public ctrlc(ShellHolder shellHolder) {
        this.shellHolder = shellHolder;
    }

    @Override
    public String exec(final CommandContext pack) throws Exception {
        new StoppableThread() {
            @Override
            public void run() {
                super.run();

                MainManager.interactive.kill();
                MainManager.interactive.close();
                MainManager.interactive = null;

                MainManager.interactive = shellHolder.build();

                pack.currentDirectory = XMLPrefsManager.get(File.class, Behavior.home_path);
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_HINT));
            }
        }.start();

        LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_NOROOT));

        return null;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_ctrlc;
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
