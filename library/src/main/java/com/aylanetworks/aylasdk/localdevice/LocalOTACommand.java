package com.aylanetworks.aylasdk.localdevice;

import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Android_Aura
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

public class LocalOTACommand {
    private static final String LOG_TAG = "LocalOTACommand";

    @Expose public String url;
    @Expose public String type;
    @Expose public String ver;
    @Expose public long size;
    @Expose public String checksum;
    @Expose public String source;
    @Expose public String api_url;

    // Copied from the containing AylaDeviceCommand
    public int command_id;

    public LocalOTACommand(){

    }

    public static LocalOTACommand fromData(String data) {
        // Object is stored under an "ota" variable
        try {
            JSONObject obj = new JSONObject(data);
            String dataContents = obj.getString("ota");
            if (dataContents == null) {
                AylaLog.e(LOG_TAG, "Failed to find ota field in JSON: " + data);
                return null;
            }

            return AylaNetworks.sharedInstance().getGson().fromJson(dataContents, LocalOTACommand.class);
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "Failed to parse data JSON");
        }
        return null;
    }
}
