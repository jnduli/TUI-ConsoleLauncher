package ohi.andre.consolelauncher.commands.main.raw;

import android.bluetooth.BluetoothAdapter;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;

public class bluetooth implements CommandAbstraction {

    @Override
    public String exec(CommandContext pack) {
        CommandContext info = (CommandContext) pack;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if(adapter == null) return info.context.getString(R.string.output_bluetooth_unavailable);

        if(adapter.isEnabled()) {
            adapter.disable();
            return info.context.getString(R.string.output_bluetooth) + " false";
        } else {
            adapter.enable();
            return info.context.getString(R.string.output_bluetooth) + " true";
        }
    }

    @Override
    public int helpRes() {
        return R.string.help_bluetooth;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 2;
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
