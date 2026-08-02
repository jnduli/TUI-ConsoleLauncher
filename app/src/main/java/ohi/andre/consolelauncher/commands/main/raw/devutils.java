package ohi.andre.consolelauncher.commands.main.raw;


import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.tuils.Tuils;

import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.tuils.StringTuils;

/**
 * Created by francescoandreuzzi on 22/08/2017.
 */

public class devutils extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        notify {
            @Override
            public String exec(CommandContext pack) {
                List<String> text = (List<String>) pack.getList();

                String title, txt = null;
                if(text.size() == 0) return null;
                else {
                    title = text.remove(0);
                    if(text.size() >= 2) txt = StringTuils.toPlanString(text, Tuils.SPACE);
                }

                NotificationManagerCompat.from(pack.context).notify(200,
                        new NotificationCompat.Builder(pack.context)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle(title)
                            .setContentText(txt)
                            .build());

                return null;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.TEXTLIST};
            }
        },
        check_notifications {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(CommandContext pack) {
                return "Notification access: " + NotificationManagerCompat.getEnabledListenerPackages(pack.context).contains(BuildConfig.APPLICATION_ID) + Tuils.NEWLINE + "Notification service running: " + NotificationService.isRunning(pack.context);
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
        public String onNotArgEnough(CommandContext pack, int n) {
            return pack.context.getString(R.string.help_devutils);
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return null;
        }
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(CommandContext pack, String param) {
        return Param.get(param);
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_devutils;
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

    @Override
    protected String doThings(CommandContext pack) {
        return null;
    }
}
