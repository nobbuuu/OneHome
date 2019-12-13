package com.aylanetworks.aylasdk.setup;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class AylaWifiStatus {
    @Expose
    private HistoryItem[] connect_history;
    @Expose
    private String dsn;
    @Expose
    private String device_service;
    @Expose
    private String mac;
    @Expose
    private long mtime;
    @Expose
    private String host_symname;
    @Expose
    private String connected_ssid;
    @Expose
    private int ant;
    @Expose
    private String wps;
    @Expose
    private int rssi;
    @Expose
    private int bars;
    @Expose
    private String state;

    public static class Wrapper {
        @Expose public AylaWifiStatus wifi_status;
    }

    public HistoryItem[] getConnectHistory() {
        return connect_history;
    }

    public String getConnectedSsid() {
        return connected_ssid;
    }

    public String getDeviceService() {
        return device_service;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("WifiStatus: connected_ssid=")
                .append(connected_ssid)
                .append(" history[");
        if (connect_history == null) {
            sb.append("null");
        } else {
            sb.append(connect_history.length);
        }
        sb.append("] =\n");

        if (connect_history != null) {
            for (HistoryItem item : connect_history) {
                sb.append("  [")
                        .append(item.ssid_info)
                        .append("] error: ")
                        .append(item.error).append("\n");
            }
        }

        return sb.toString();
    }

    public String getState() {
        return state;
    }

    public String getDsn() {
        return dsn;
    }

    public long getMtime() {
        return mtime;
    }

    public static class HistoryItem {
        @Expose
        public String ssid_info;
        @Expose
        public int ssid_len;
        @Expose
        public String bssid;
        @Expose
        public Error error;
        @Expose
        public String msg;
        @Expose
        public long mtime;
        @Expose
        public int last;
        @Expose
        public String ip_addr;
        @Expose
        public String netmask;
        @Expose
        public String default_route;
        @Expose
        public String[] dns_servers;

        public enum Error {
            @SerializedName("0")NoError(0),
            @SerializedName("1")ResourceProblem(1),
            @SerializedName("2")ConnectionTimedOut(2),
            @SerializedName("3")InvalidKey(3),
            @SerializedName("4")SSIDNotFound(4),
            @SerializedName("5")NotAuthenticated(5),
            @SerializedName("6")IncorrectKey(6),
            @SerializedName("7")DHCP_IP(7),
            @SerializedName("8")DHCP_GW(8),
            @SerializedName("9")DHCP_DNS(9),
            @SerializedName("10")Disconnected(10),
            @SerializedName("11")SignalLost(11),
            @SerializedName("12")DeviceServiceLookup(12),
            @SerializedName("13")DeviceServiceRedirect(13),
            @SerializedName("14")DeviceServiceTimedOut(14),
            @SerializedName("15")NoProfileSlots(15),
            @SerializedName("16")SecNotSupported(16),
            @SerializedName("17")NetTypeNotSupported(17),
            @SerializedName("18")ServerIncompatible(18),
            @SerializedName("19")ServiceAuthFailure(19),
            @SerializedName("20")InProgress(20);

            private int _code;


            Error(int code) {
                _code = code;
            }

            public int getCode() {
                return _code;
            }
        }
    }
}
