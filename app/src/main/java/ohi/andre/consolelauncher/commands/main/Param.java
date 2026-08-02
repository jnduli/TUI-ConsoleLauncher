package ohi.andre.consolelauncher.commands.main;

import ohi.andre.consolelauncher.commands.CommandContext;

/**
 * Created by francescoandreuzzi on 10/06/2017.
 */

public interface Param {

    int[] args();
    String exec(CommandContext pack);
    String label();

    String onNotArgEnough(CommandContext pack, int n);
    String onArgNotFound(CommandContext pack, int index);

    /**
     * Shared lookup for commands whose Param "enum" was rewritten as plain instances (instead of
     * a real enum) so its members can close over per-command constructor-injected state.
     */
    static Param find(Param[] values, String p) {
        String lower = p.toLowerCase();
        for (Param p1 : values)
            if (lower.endsWith(p1.label()))
                return p1;
        return null;
    }

    static String[] labels(Param[] values) {
        String[] labels = new String[values.length];
        for (int count = 0; count < values.length; count++) {
            labels[count] = values[count].label();
        }
        return labels;
    }
}
