package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.CommandContext;
import ohi.andre.consolelauncher.commands.main.specific.APICommand;
import ohi.andre.consolelauncher.tuils.Tuils;

public class data implements APICommand, CommandAbstraction {

    // scratch state for this command's own use only, lazily built by init()
    private WifiManager wifi;
    private Method setMobileDataEnabledMethod;
    private ConnectivityManager connectivityMgr;
    private Object connectMgr;

    @Override
    public String exec(CommandContext pack) {
        boolean active = toggle(pack);
        return pack.res.getString(R.string.output_data) + Tuils.SPACE + Boolean.toString(active);
    }

    private boolean toggle(CommandContext info) {
        if (connectivityMgr == null) {
            try {
                init(info);
            } catch (Exception e) {}
        }

        boolean mobileConnected;

        if (wifi == null)
            wifi = (WifiManager) info.context.getSystemService(Context.WIFI_SERVICE);

        if (wifi.isWifiEnabled())
            mobileConnected = true;
        else {
            NetworkInfo mobileInfo = connectivityMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            State state = mobileInfo.getState();
            mobileConnected = state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING;
        }

        try {
            setMobileDataEnabledMethod.invoke(connectMgr, !mobileConnected);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return !mobileConnected;
    }

    private void init(CommandContext info) throws Exception {
        connectivityMgr = (ConnectivityManager) info.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Class<?> conmanClass = Class.forName(connectivityMgr.getClass().getName());
        Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
        iConnectivityManagerField.setAccessible(true);
        connectMgr = iConnectivityManagerField.get(connectivityMgr);
        Class<?> iConnectivityManagerClass = Class.forName(connectMgr.getClass().getName());
        setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
        setMobileDataEnabledMethod.setAccessible(true);
    }

    @Override
    public int helpRes() {
        return R.string.help_data;
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
        return onNotArgEnough(info, 0);
    }

    @Override
    public boolean willWorkOn(int api) {
        return api < Build.VERSION_CODES.LOLLIPOP;
    }
}
