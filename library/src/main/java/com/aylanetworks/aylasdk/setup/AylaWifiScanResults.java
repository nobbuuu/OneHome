package com.aylanetworks.aylasdk.setup;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.google.gson.annotations.Expose;

/**
 * Returned via a call to {@link AylaSetup#fetchDeviceAccessPoints}, this class represents the
 * set of discovered WiFi access points discovered by the mobile device.
 */
public class AylaWifiScanResults {

    @Expose
    public long mtime;
    @Expose
    public Result[] results;

    public static class Result {

        /**
         * The Wi-Fi network name.
         */
        @Expose
        public String ssid;

        /**
         * Wi-Fi BSS types, could be one of "AP", "Ad hoc", and "Unknown".
         */
        @Expose
        public String type;

        /**
         * The channel value in MHZ of the Wi-Fi network.
         */
        @Expose
        public int chan;

        /**
         * The detected signal level in dBm, also known as the RSSI.
         */
        @Expose
        public int signal;

        /**
         * Converted bar-graph intensity from the detected signal strength.
         * Any signal should give at least one bar.  Max signal is 5 bars.
         * Lean towards giving 5 bars for a wide range of usable signals.
         */
        @Expose
        public int bars;

        /**
         * The security types returned, could be one of "None", "WEP", "WPA",
         * "WPA2 Personal AES", "WPA2 Personal Mixed", or "Unknown" (e.g., for WPS).
         */
        @Expose
        public String security;

        /**
         * The address of the access point.
         */
        @Expose
        public String bssid;

        public boolean isSecurityOpen() {
             // Note that the first letter of the security type "None" for open Wi-Fi should
             // always be upper-cased. However, as some linux gateways were released with
             // security type set to "none" we have to do case-insensitive comparision here.
             return "None".equalsIgnoreCase(security);
        }
    }

    public static class Wrapper {
        @Expose
        public AylaWifiScanResults wifi_scan;
    }
}
