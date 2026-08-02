package ohi.andre.consolelauncher.commands.main.specific;

import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;

/**
 * Created by francescoandreuzzi on 03/03/2017.
 */

public abstract class RedirectCommand implements CommandAbstraction {

    public List<Object> beforeObjects = new ArrayList<>();
    public List<Object> afterObjects = new ArrayList<>();

    public abstract String onRedirect(CommandContext pack);
    public abstract int getHint();
    public abstract boolean isWaitingPermission();

    public void cleanup() {
        beforeObjects.clear();
        afterObjects.clear();
    }
}
