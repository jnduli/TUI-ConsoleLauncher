package ohi.andre.consolelauncher.tuils.libsuperuser;

import android.content.Context;

import java.util.regex.Pattern;

import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.tuils.Tuils;

import ohi.andre.consolelauncher.tuils.OutputTuils;

/**
 * Created by francescoandreuzzi on 18/08/2017.
 */

public class ShellHolder {

    private Context context;

    public ShellHolder(Context context) {
        this.context = context;
    }

    Pattern p = Pattern.compile("^\\n");

    public Shell.Interactive build() {
        Shell.Interactive interactive = new Shell.Builder()
                .setOnSTDOUTLineListener(line -> {
                    line = p.matcher(line).replaceAll(Tuils.EMPTYSTRING);
                    OutputTuils.sendOutput(context, line, Integer.MAX_VALUE, TerminalManager.CATEGORY_OUTPUT);
                })
                .setOnSTDERRLineListener(line -> {
                    line = p.matcher(line).replaceAll(Tuils.EMPTYSTRING);
                    OutputTuils.sendOutput(context, line, Integer.MAX_VALUE, TerminalManager.CATEGORY_OUTPUT);
                })
                .open();
        interactive.addCommand("cd " + XMLPrefsManager.homeDirectory());
        return interactive;
    }
}
