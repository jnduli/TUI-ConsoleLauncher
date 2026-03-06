package ohi.andre.consolelauncher.commands.main.raw;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.Process;

import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.APICommand;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.Launchable;
import ohi.andre.consolelauncher.managers.AppLauncher;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Created by francescoandreuzzi on 24/03/2018.
 */

@TargetApi(Build.VERSION_CODES.N_MR1)
public class shortcut extends ParamCommand implements APICommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        use {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.VISIBLE_PACKAGE};
            }

            @Override
            public String exec(ExecutePack pack) {
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
            public String onNotArgEnough(ExecutePack pack, int n) {
                if(n == 1) return pack.context.getString(R.string.help_shortcut);

                pack.get();
                String id = pack.getString();

                AppLauncher info = null;

                Out:
                for(Launchable l : ((MainPack) pack).appsManager.shownApps()) {
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

            private String startShortcut(AppLauncher info, Context context) {
                if(info == null) return context.getString(R.string.app_shortcut_not_found);

                LauncherApps apps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                apps.startShortcut(info.getPackageName(), info.getShortcut(), null, null, Process.myUserHandle());

                return null;
            }
        },
        ls {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.VISIBLE_PACKAGE};
            }

            @Override
            public String exec(ExecutePack pack) {
                Launchable li = pack.getLaunchable();
                if(!(li instanceof AppLauncher)) return "[]";
                AppLauncher appLauncher = (AppLauncher) li;
                if(appLauncher.getShortcut() == null || appLauncher.getShortcut().length() == 0) return "[]";

                return "- " + appLauncher.getShortcut();
            }

            @Override
            public String onNotArgEnough(ExecutePack pack, int n) {
                List<Launchable> infos = ((MainPack) pack).appsManager.shownApps();
                StringBuilder builder = new StringBuilder();

                for(Launchable l : infos) {
                    if(!(l instanceof AppLauncher)) continue;
                    AppLauncher appLauncher = (AppLauncher) l;
                    if(appLauncher.getShortcut() == null || appLauncher.getShortcut().length() == 0) continue;

                    builder.append(appLauncher.label()).append(Tuils.NEWLINE);
                    builder.append(Tuils.DOUBLE_SPACE).append("- ").append(appLauncher.getShortcut()).append(Tuils.NEWLINE);
                }

                String s = builder.toString().trim();
                if(s.length() == 0) return "[]";

                return s;
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            Param[] ps = values();
            for (Param p1 : ps)
                if (p.endsWith(p1.label()))
                    return p1;
            return null;
        }

        static String[] labels() {
            Param[] ps = values();
            String[] ss = new String[ps.length];

            for (int count = 0; count < ps.length; count++) {
                ss[count] = ps[count].label();
            }

            return ss;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_shortcut);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return pack.context.getString(R.string.output_appnotfound);
        }
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public boolean willWorkOn(int api) {
//        return false;
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
        return Param.labels();
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }

}
