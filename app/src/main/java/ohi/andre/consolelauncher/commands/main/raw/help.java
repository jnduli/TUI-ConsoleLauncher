package ohi.andre.consolelauncher.commands.main.raw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.tuils.Tuils;

import ohi.andre.consolelauncher.tuils.StringTuils;

public class help implements CommandAbstraction {

    @Override
    public String exec(CommandContext pack) throws Exception {
        CommandContext info = (CommandContext) pack;
        CommandAbstraction cmd = info.get(CommandAbstraction.class);
        int res = cmd == null ? R.string.output_commandnotfound : cmd.helpRes();
        return "Priority: " + info.cmdPrefs.getPriority(cmd) + Tuils.NEWLINE + info.res.getString(res);
    }

    @Override
    public int helpRes() {
        return R.string.help_help;
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.COMMAND};
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public String onNotArgEnough(CommandContext pack, int nArgs) {
        CommandContext info = (CommandContext) pack;
        List<String> toPrint = new ArrayList<>(Arrays.asList(info.commandGroup.getCommandNames()));

        Collections.sort(toPrint, StringTuils::alphabeticCompare);

        StringTuils.addPrefix(toPrint, Tuils.DOUBLE_SPACE);
        StringTuils.addSeparator(toPrint, Tuils.TRIBLE_SPACE);
        StringTuils.insertHeaders(toPrint, true);

        return StringTuils.toPlanString(toPrint, "");
    }

    @Override
    public String onArgNotFound(CommandContext pack, int index) {
        CommandContext info = (CommandContext) pack;
        return info.res.getString(R.string.output_commandnotfound);
    }

}
