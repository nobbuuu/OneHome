package com.aylanetworks.aylasdk.util;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

public class NetworkUtils {
    public static String getWifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager)context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        return getIpAddress(ip);
    }

    /**
     * Returns the numeric representation (such as "127.0.0.1"). from a packed integer containing
     * the IP address.
     * @param ip
     */
    public static String getIpAddress(int ip){

        // Convert endian-ness if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ip = Integer.reverseBytes(ip);
        }
        byte[] ipBytes = BigInteger.valueOf(ip).toByteArray();
        String ipAddress = null;

        try {
            ipAddress = InetAddress.getByAddress(ipBytes).getHostAddress();
        } catch (UnknownHostException e) {
            ipAddress = null;
        }
        return ipAddress;
    }
}
