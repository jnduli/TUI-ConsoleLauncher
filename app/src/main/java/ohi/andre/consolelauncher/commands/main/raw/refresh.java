package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.managers.AliasManager;
import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.ContactManager;

public class refresh implements CommandAbstraction {

    private final AliasManager aliasManager;
    private final AppsManager appsManager;
    private final ContactManager contacts;

    public refresh(AliasManager aliasManager, AppsManager appsManager, ContactManager contacts) {
        this.aliasManager = aliasManager;
        this.appsManager = appsManager;
        this.contacts = contacts;
    }

    @Override
    public String exec(CommandContext pack) {
        appsManager.fill();
        aliasManager.reload();
        if (contacts != null) contacts.refreshContacts(pack.context);

        return pack.res.getString(R.string.output_refresh);
    }

    @Override
    public int helpRes() {
        return R.string.help_refresh;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public String onNotArgEnough(CommandContext info, int nArgs) {
        return null;
    }

    @Override
    public String onArgNotFound(CommandContext info, int index) {
        return null;
    }
}
