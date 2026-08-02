package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import android.net.Uri;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;

public class rate implements CommandAbstraction {

    @Override
    public String exec(CommandContext pack) {
        final CommandContext info = (CommandContext) pack;
        try {
            info.context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" +
                    info.context.getPackageName())));
        } catch (android.content.ActivityNotFoundException anfe) {
            info.context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" +
                    info.context.getPackageName())));
        }

        return info.res.getString(R.string.output_rate);
    }

    @Override
    public int helpRes() {
        return R.string.help_rate;
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
    public String onNotArgEnough(CommandContext info, int nArgs) {
        return null;
    }

    @Override
    public String onArgNotFound(CommandContext info, int index) {
        return null;
    }

}
