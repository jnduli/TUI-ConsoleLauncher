package ohi.andre.consolelauncher.commands.main.raw;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.RssManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Rss;
import ohi.andre.consolelauncher.tuils.Tuils;

import ohi.andre.consolelauncher.tuils.FileTuils;
import ohi.andre.consolelauncher.tuils.LogTuils;

/**
 * Created by francescoandreuzzi on 30/09/2017.
 */

public class rss extends ParamCommand {

    private final RssManager rssManager;

    public rss(RssManager rssManager) {
        this.rssManager = rssManager;
    }

    // not a static enum: constants need access to the outer instance's rssManager
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
            return pack.context.getString(R.string.help_rss);
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return pack.context.getString(R.string.invalid_integer);
        }
    }

    private final Param add = new Param("add") {
        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            long tm = pack.get(long.class);
            String url = pack.getString();

            return rssManager.add(id, tm, url);
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.LONG, CommandAbstraction.PLAIN_TEXT};
        }
    };

    private final Param rm = new Param("rm") {
        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();

            return rssManager.rm(id);
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }
    };

    private final Param ls = new Param("ls") {
        @Override
        public String exec(CommandContext pack) {
            return rssManager.list();
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    };

    private final Param l = new Param("l") {
        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            return rssManager.l(id);
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }
    };

    private final Param show = new Param("show") {
        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            boolean show = pack.getBoolean();

            return rssManager.setShow(id, show);
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.BOOLEAN};
        }
    };

    private final Param update_time = new Param("update_time") {
        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            long tm = pack.get(long.class);

            return rssManager.setTime(id, tm);
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.LONG};
        }
    };

    private final Param time_format = new Param("time_format") {
        @Override
        public String exec(CommandContext pack) {
            return rssManager.setTimeFormat(pack.getInt(), pack.getString());
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }
    };

    private final Param format = new Param("format") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }

        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            String s = pack.getString();

            return rssManager.setFormat(id, s);
        }
    };

    private final Param color = new Param("color") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.COLOR};
        }

        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            String c = pack.getString();

            return rssManager.setColor(id, c);
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            if(index == 2) return pack.context.getString(R.string.output_invalidcolor);
            return super.onArgNotFound(pack, index);
        }
    };

    private final Param entry_tag = new Param("entry_tag") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }

        @Override
        public String exec(CommandContext pack) {
            return rssManager.setEntryTag(pack.getInt(), pack.getString());
        }
    };

    private final Param date_tag = new Param("date_tag") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }

        @Override
        public String exec(CommandContext pack) {
            return rssManager.setDateTag(pack.getInt(), pack.getString());
        }
    };

    private final Param last_check = new Param("last_check") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }

        @Override
        public String exec(CommandContext pack) {
            Node n = XMLPrefsManager.findNode(new File(FileTuils.getFolder(pack.context), RssManager.PATH), RssManager.RSS_LABEL, new String[] {RssManager.ID_ATTRIBUTE}, new String[] {String.valueOf(pack.getInt())});
            if(n == null) return pack.context.getString(R.string.id_notfound);

            Element el = (Element) n;

            String value = el.hasAttribute(RssManager.LASTCHECKED_ATTRIBUTE) ? el.getAttribute(RssManager.LASTCHECKED_ATTRIBUTE) : null;
            if(value == null) return pack.context.getString(R.string.rss_never_checked);

            try {
                return TimeManager.instance.replace(XMLPrefsManager.get(Rss.rss_time_format), Long.parseLong(value),
                        Integer.MAX_VALUE).toString();
            } catch (Exception e) {
                LogTuils.log(e);
                return pack.context.getString(R.string.output_error);
            }
        }
    };

    private final Param frc = new Param("frc") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }

        @Override
        public String exec(CommandContext pack) {
            if(!rssManager.updateRss(pack.getInt(), false, true)) return pack.context.getString(R.string.id_notfound);
            return null;
        }
    };

    private final Param info = new Param("info") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }

        @Override
        public String exec(CommandContext pack) {
            RssManager.Rss rss = rssManager.findId(pack.getInt());
            if(rss == null) return pack.context.getString(R.string.id_notfound);

            return rss.toString();
        }
    };

    private final Param include_if_matches = new Param("include_if_matches") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }

        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            String r = pack.getString();

            return rssManager.setIncludeIfMatches(id, r);
        }
    };

    private final Param exclude_if_matches = new Param("exclude_if_matches") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }

        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            String r = pack.getString();

            return rssManager.setExcludeIfMatches(id, r);
        }
    };

    private final Param add_command = new Param("add_command") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.PLAIN_TEXT};
        }

        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();

            String on = pack.getString();
            String regex = pack.getString();
            String cmd = pack.getString();

            try {
                Pattern.compile(regex);
            } catch (Exception e) {
                return e.toString();
            }

            return rssManager.addRegexCommand(id, on, regex, cmd);
        }
    };

    private final Param rm_command = new Param("rm_command") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }

        @Override
        public String exec(CommandContext pack) {
            return rssManager.rmRegexCommand(pack.getInt());
        }
    };

    private final Param wifi_only = new Param("wifi_only") {
        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.BOOLEAN};
        }

        @Override
        public String exec(CommandContext pack) {
            int id = pack.getInt();
            boolean w = pack.getBoolean();

            return rssManager.setWifiOnly(id, w);
        }
    };

    private final Param add_format = new Param("add_format") {
        @Override
        public String exec(CommandContext pack) {
            return rssManager.addFormat(pack.getInt(), pack.getString());
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
        }
    };

    private final Param rm_format = new Param("rm_format") {
        @Override
        public String exec(CommandContext pack) {
            return rssManager.removeFormat(pack.getInt());
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.INT};
        }
    };

    private final Param file = new Param("file") {
        @Override
        public String exec(CommandContext pack) {
            pack.context.startActivity(FileTuils.openFile(pack.context, new File(FileTuils.getFolder(pack.context), RssManager.PATH)));
            return null;
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    };

    private Param[] values() {
        return new Param[] {add, rm, ls, l, show, update_time, time_format, format, color, entry_tag, date_tag,
                last_check, frc, info, include_if_matches, exclude_if_matches, add_command, rm_command, wifi_only,
                add_format, rm_format, file};
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
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_rss;
    }

    @Override
    public String[] params() {
        return ohi.andre.consolelauncher.commands.main.Param.labels(values());
    }
}
