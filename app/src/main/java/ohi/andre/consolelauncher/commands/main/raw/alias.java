package ohi.andre.consolelauncher.commands.main.raw;

import java.io.File;
import java.util.ArrayList;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.AliasManager;
import ohi.andre.consolelauncher.tuils.Tuils;

import ohi.andre.consolelauncher.tuils.SystemUtils;
import ohi.andre.consolelauncher.tuils.FileTuils;
import ohi.andre.consolelauncher.tuils.StringTuils;

/**
 * Created by andre on 15/11/15.
 */
public class alias extends ParamCommand {

    private final AliasManager aliasManager;

    public alias(AliasManager aliasManager) {
        this.aliasManager = aliasManager;
    }

    // not a static enum: constants need access to the outer instance's aliasManager
    private abstract class Param implements ohi.andre.consolelauncher.commands.main.Param {

        private final String name;

        Param(String name) {
            this.name = name;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name;
        }

        @Override
        public String onNotArgEnough(CommandContext pack, int index) {
            return pack.context.getString(R.string.help_alias);
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return null;
        }
    }

    private final Param add = new Param("add") {
        @Override
        public String exec(CommandContext pack) {
            ArrayList<String> args = (ArrayList<String>) pack.getList();
            if(args.size() < 2) return pack.context.getString(R.string.output_lessarg);

            aliasManager.add(pack.context, args.remove(0), StringTuils.toPlanString(args, Tuils.SPACE));
            return null;
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.TEXTLIST};
        }
    };

    private final Param rm = new Param("rm") {
        @Override
        public String exec(CommandContext pack) {
            ArrayList<String> args = (ArrayList<String>) pack.getList();
            if(args.size() < 1) return pack.context.getString(R.string.output_lessarg);
            aliasManager.remove(pack.context, args.get(0));
            return null;
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.TEXTLIST};
        }
    };

    private final Param file = new Param("file") {
        @Override
        public String exec(CommandContext pack) {
            pack.context.startActivity(FileTuils.openFile(pack.context, new File(FileTuils.getFolder(pack.context), AliasManager.PATH)));
            return null;
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    };

    private final Param ls = new Param("ls") {
        @Override
        public String exec(CommandContext pack) {
            return aliasManager.printAliases();
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    };

    private final Param tutorial = new Param("tutorial") {
        @Override
        public int[] args() {
            return new int[0];
        }

        @Override
        public String exec(CommandContext pack) {
            pack.context.startActivity(SystemUtils.webPage("https://github.com/Andre1299/TUI-ConsoleLauncher/wiki/Alias"));
            return null;
        }
    };

    private Param[] values() {
        return new Param[] {add, rm, file, ls, tutorial};
    }

    @Override
    public String[] params() {
        return ohi.andre.consolelauncher.commands.main.Param.labels(values());
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(CommandContext pack, String param) {
        return ohi.andre.consolelauncher.commands.main.Param.find(values(), param);
    }

    @Override
    protected String doThings(CommandContext pack) {
        return null;
    }

    @Override
    public int helpRes() {
        return R.string.help_alias;
    }

    @Override
    public int priority() {
        return 2;
    }
}
