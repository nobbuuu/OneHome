package com.dream.onehome.utils;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.dream.onehome.common.OneHomeAplication;

import static android.content.Context.WIFI_SERVICE;

public class WifiUtils {

    private static final String TAG = "WifiUtils";
    public static String getConnectWifiSsid() {
        WifiManager wifiManager = (WifiManager) OneHomeAplication.getInstance().getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();
        String wifiName = ssid.replace("\"", "");

        Log.d(TAG, "connect wifi name = " + wifiName);

        return wifiName;
    }

}
