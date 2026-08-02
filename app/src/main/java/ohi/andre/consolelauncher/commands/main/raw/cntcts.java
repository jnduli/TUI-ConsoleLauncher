package ohi.andre.consolelauncher.commands.main.raw;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.ContactManager;
import ohi.andre.consolelauncher.tuils.Tuils;

import ohi.andre.consolelauncher.tuils.StringTuils;

/**
 * Created by francescoandreuzzi on 11/05/2017.
 */

public class cntcts extends ParamCommand {

    private final ContactManager contacts;

    public cntcts(ContactManager contacts) {
        this.contacts = contacts;
    }

    // not a static enum: constants need access to the outer instance's contacts
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
        public String onArgNotFound(CommandContext pack, int index) {
            return null;
        }

        @Override
        public String onNotArgEnough(CommandContext pack, int n) {
            return pack.context.getString(R.string.help_cntcts);
        }
    }

    private final Param ls = new Param("ls") {
        @Override
        public String exec(CommandContext pack) {
            List<String> list = contacts.listNamesAndNumbers();
            StringTuils.insertHeaders(list, false);
            return StringTuils.toPlanString(list);
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    };

    private final Param add = new Param("add") {
        @Override
        public String exec(CommandContext pack) {
            Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
            intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
            pack.context.startActivity(intent);

            return null;
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    };

    private final Param rm = new Param("rm") {
        @Override
        public String exec(CommandContext pack) {
            if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) pack.context, new String[]{Manifest.permission.WRITE_CONTACTS}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
                return pack.context.getString(R.string.output_waitingpermission);
            }

            contacts.delete(pack.getString());
            return null;
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.CONTACTNUMBER};
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return pack.context.getString(R.string.output_numbernotfound);
        }
    };

    private final Param edit = new Param("edit") {
        @Override
        public String exec(CommandContext pack) {
            Intent editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setDataAndType(contacts.fromPhone(pack.getString()), ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            pack.context.startActivity(editIntent);

            return null;
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.CONTACTNUMBER};
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return pack.context.getString(R.string.output_numbernotfound);
        }
    };

    private final Param l = new Param("l") {
        @Override
        public String exec(CommandContext pack) {
            String[] about = contacts.about(pack.getString());
            StringBuilder builder = new StringBuilder();

            builder.append(about[ContactManager.NAME]).append(Tuils.NEWLINE);
            builder.append("\t\t").append(about[ContactManager.NUMBERS].replaceAll(Tuils.NEWLINE, Tuils.NEWLINE + "\t\t")).append(Tuils.NEWLINE);
            builder.append("ID: ").append(about[ContactManager.CONTACT_ID]).append(Tuils.NEWLINE);
            builder.append("Contacted ").append(about[ContactManager.TIME_CONTACTED]).append(" time(s)").append(Tuils.NEWLINE);

            return builder.toString();
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.CONTACTNUMBER};
        }

        @Override
        public String onArgNotFound(CommandContext pack, int index) {
            return pack.context.getString(R.string.output_numbernotfound);
        }
    };

    private Param[] values() {
        return new Param[] {ls, add, rm, edit, l};
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(CommandContext pack, String param) {
        return ohi.andre.consolelauncher.commands.main.Param.find(values(), param);
    }

    @Override
    public String[] params() {
        return ohi.andre.consolelauncher.commands.main.Param.labels(values());
    }

    @Override
    protected String doThings(CommandContext pack) {
        if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) pack.context, new String[]{Manifest.permission.READ_CONTACTS}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
            return pack.context.getString(R.string.output_waitingpermission);
        }
        if (contacts == null) {
            return pack.context.getString(R.string.output_error);
        }
        return null;
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_cntcts;
    }
}
