package com.ceco.gm2.gravitybox;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

public class WifiManagerWrapper {
    public static final String WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    public static final String EXTRA_WIFI_AP_STATE = "wifi_state";

    public static final int WIFI_STATE_DISABLING = 0;
    public static final int WIFI_STATE_DISABLED = 1;
    public static final int WIFI_STATE_ENABLING = 2;
    public static final int WIFI_STATE_ENABLED = 3;
    public static final int WIFI_STATE_UNKNOWN = 4;
    public static final String WIFI_SAVED_STATE = "wifi_saved_state";
    
    public static final int WIFI_AP_STATE_DISABLING = 10;
    public static final int WIFI_AP_STATE_DISABLED = 11;
    public static final int WIFI_AP_STATE_ENABLING = 12;
    public static final int WIFI_AP_STATE_ENABLED = 13;
    public static final int WIFI_AP_STATE_FAILED = 14;

    private Context mContext;
    private WifiManager mWifiManager;
    private WifiApStateChangeListener mApStateChangeListener;
    private BroadcastReceiver mApStateChangeReceiver;

    public interface WifiApStateChangeListener {
        void onWifiApStateChanged(int wifiApState);
    }

    public WifiManagerWrapper(Context context, WifiApStateChangeListener listener) {
        mContext = context;
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        setWifiApStateChangeListener(listener);
    }

    public WifiManagerWrapper(Context context) {
        this(context, null);
    }

    public void setWifiApStateChangeListener(WifiApStateChangeListener listener) {
        if (listener == null) return;

        mApStateChangeListener = listener;
        registerApStateChangeReceiver();
    }

    private void registerApStateChangeReceiver() {
        if (mContext == null || mApStateChangeReceiver != null)
            return;

        mApStateChangeReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(WIFI_AP_STATE_CHANGED_ACTION) &&
                        intent.hasExtra(EXTRA_WIFI_AP_STATE)) {
                    int state = intent.getIntExtra(EXTRA_WIFI_AP_STATE, WIFI_AP_STATE_FAILED);
                    if (mApStateChangeListener != null) {
                        mApStateChangeListener.onWifiApStateChanged(state);
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter(WIFI_AP_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mApStateChangeReceiver, intentFilter);
    }

    public int getWifiState() {
        return mWifiManager.getWifiState();
    }

    public int getWifiApState() {
        return (Integer) XposedHelpers.callMethod(mWifiManager, "getWifiApState");
    }

    public boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    public void setWifiEnabled(boolean enable) {
        mWifiManager.setWifiEnabled(enable);
    }

    public boolean isWifiApEnabled() {
        return (getWifiApState() == WIFI_AP_STATE_ENABLED);
    }

    @SuppressLint("NewApi")
    public void setWifiApEnabled(boolean enable) {
        try {
            final ContentResolver cr = mContext.getContentResolver();

            int wifiState = getWifiState(); 
            if (enable && (wifiState == WIFI_STATE_ENABLING ||
                    wifiState == WIFI_STATE_ENABLED)) {
                setWifiEnabled(false);
                if (Build.VERSION.SDK_INT > 16) {
                    Settings.Global.putInt(cr, WIFI_SAVED_STATE, 1);
                } else {
                    Settings.Secure.putInt(cr, WIFI_SAVED_STATE, 1);
                }
            }

            Class<?>[] paramArgs = new Class<?>[2];
            paramArgs[0] = WifiConfiguration.class;
            paramArgs[1] = boolean.class;
            XposedHelpers.callMethod(mWifiManager, "setWifiApEnabled", paramArgs, null, enable);

            if (!enable) {
                int wifiSavedState = 0;
                try {
                    if (Build.VERSION.SDK_INT > 16) {
                        wifiSavedState = Settings.Global.getInt(cr, WIFI_SAVED_STATE);
                    } else {
                        wifiSavedState = Settings.Secure.getInt(cr, WIFI_SAVED_STATE);
                    }
                } catch (Settings.SettingNotFoundException e) {
                    //
                }
                if (wifiSavedState == 1) {
                    setWifiEnabled(true);
                    if (Build.VERSION.SDK_INT > 16) {
                        Settings.Global.putInt(cr, WIFI_SAVED_STATE, 0);
                    } else {
                        Settings.Secure.putInt(cr, WIFI_SAVED_STATE, 0);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}