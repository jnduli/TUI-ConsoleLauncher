package ohi.andre.consolelauncher.commands.main.raw;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.Process;

import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.APICommand;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.launchable.Launchable;
import ohi.andre.consolelauncher.managers.launchable.AppLauncher;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Created by francescoandreuzzi on 24/03/2018.
 */

@TargetApi(Build.VERSION_CODES.N_MR1)
public class shortcut extends ParamCommand implements APICommand {

    private final AppsManager appsManager;

    public shortcut(AppsManager appsManager) {
        this.appsManager = appsManager;
    }

    // not a static enum: constants need access to the outer instance's appsManager
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
        public String onNotArgEnough(CommandContext pack, int n) {
            return pack.context.getString(R.string.help_shortcut);
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return pack.context.getString(R.string.output_appnotfound);
        }

        String startShortcut(AppLauncher info, Context context) {
            if(info == null) return context.getString(R.string.app_shortcut_not_found);

            LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            apps.startShortcut(info.getPackageName(), info.getShortcut(), null, null, Process.myUserHandle());

            return null;
        }
    }

    private final Param use = new Param("use") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.VISIBLE_PACKAGE};
        }

        @Override
        public String exec(CommandContext pack) {
            String id = pack.getString();
            Launchable li = pack.getLaunchable();

            if(!(li instanceof AppLauncher)) return pack.context.getString(R.string.app_shortcut_not_found);
            AppLauncher appLauncher = (AppLauncher) li;
            String shortcut = appLauncher.getShortcut();

            if(shortcut == null || shortcut.length() == 0) return "[]";

            if(id.equals("0") || id.equals(shortcut)) {
                return startShortcut(appLauncher, pack.context);
            }

            return pack.context.getString(R.string.id_notfound);
        }

        @Override
        public String onNotArgEnough(CommandContext pack, int n) {
            if(n == 1) return pack.context.getString(R.string.help_shortcut);

            pack.get();
            String id = pack.getString();

            AppLauncher info = null;

            Out:
            for(Launchable l : appsManager.shownApps()) {
                if(!(l instanceof AppLauncher)) continue;
                AppLauncher appLauncher = (AppLauncher) l;
                if(appLauncher.getShortcut() == null || appLauncher.getShortcut().length() == 0) continue;

                if(appLauncher.getShortcut().equals(id)) {
                    info = appLauncher;
                    break Out;
                }
            }

            return startShortcut(info, pack.context);
        }
    };

    private final Param ls = new Param("ls") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.VISIBLE_PACKAGE};
        }

        @Override
        public String exec(CommandContext pack) {
            Launchable li = pack.getLaunchable();
            if(!(li instanceof AppLauncher)) return "[]";
            AppLauncher appLauncher = (AppLauncher) li;
            if(appLauncher.getShortcut() == null || appLauncher.getShortcut().length() == 0) return "[]";

            return "- " + appLauncher.getShortcut();
        }

        @Override
        public String onNotArgEnough(CommandContext pack, int n) {
            List<Launchable> infos = appsManager.shownApps();
            StringBuilder builder = new StringBuilder();

            for(Launchable l : infos) {
                if(!(l instanceof AppLauncher)) continue;
                AppLauncher appLauncher = (AppLauncher) l;
                if(appLauncher.getShortcut() == null || appLauncher.getShortcut().length() == 0) continue;

                builder.append(appLauncher.name()).append(Tuils.NEWLINE);
                builder.append(Tuils.DOUBLE_SPACE).append("- ").append(appLauncher.getShortcut()).append(Tuils.NEWLINE);
            }

            String s = builder.toString().trim();
            if(s.length() == 0) return "[]";

            return s;
        }
    };

    private Param[] values() {
        return new Param[] {use, ls};
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(CommandContext pack, String param) {
        return ohi.andre.consolelauncher.commands.main.Param.find(values(), param);
    }

    @Override
    public boolean willWorkOn(int api) {
        return api >= Build.VERSION_CODES.N_MR1;
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_shortcut;
    }

    @Override
    public String[] params() {
        return ohi.andre.consolelauncher.commands.main.Param.labels(values());
    }

    @Override
    protected String doThings(CommandContext pack) {
        return null;
    }

}
