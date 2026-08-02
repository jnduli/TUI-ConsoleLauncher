package ohi.andre.consolelauncher.commands;

import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ohi.andre.consolelauncher.commands.main.specific.APICommand;

/**
 * Holds the fixed set of commands the launcher supports. The list is built explicitly by the
 * caller (see MainManager) rather than discovered via reflection, so each command can declare
 * constructor dependencies instead of receiving every manager through a shared god object.
 */
public class CommandGroup {

    private final CommandAbstraction[] commands;
    private final String[] commandNames;

    public CommandGroup(List<CommandAbstraction> commands) {
        List<CommandAbstraction> cmdAbs = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (CommandAbstraction ca : commands) {
            if (ca instanceof APICommand && !((APICommand) ca).willWorkOn(Build.VERSION.SDK_INT)) {
                continue;
            }
            cmdAbs.add(ca);
            names.add(ca.getClass().getSimpleName());
        }

        Collections.sort(names);
        this.commandNames = names.toArray(new String[0]);

        Collections.sort(cmdAbs, (o1, o2) -> o2.priority() - o1.priority());
        this.commands = cmdAbs.toArray(new CommandAbstraction[0]);
    }

    public CommandAbstraction getCommandByName(String name) {
        for(CommandAbstraction c : commands) {
            if(c.getClass().getSimpleName().equals(name)) {
                return c;
            }
        }

        return null;
    }

    public CommandAbstraction[] getCommands() {
        return commands;
    }

    public String[] getCommandNames() {
        return commandNames;
    }

}
