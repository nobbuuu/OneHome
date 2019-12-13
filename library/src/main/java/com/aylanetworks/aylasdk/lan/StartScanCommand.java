package com.aylanetworks.aylasdk.lan;

import com.aylanetworks.aylasdk.AylaNetworks;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

/**
 * Android_Aura
 * <p>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

public class StartScanCommand extends LanCommand {

    @Expose
    private int cmdId;
    @Expose
    private String method;
    @Expose
    private String resource;
    @Expose
    private String data;
    @Expose
    private String uri;

    public StartScanCommand() {
        super();
        this.cmdId = 1;
        this.method = "POST";
        this.resource = "wifi_scan.json";
        this.data = (data == null ? "" : data);
        this.uri = "/local_lan/wifi_scan.json";
    }

    @Override
    public boolean expectsModuleRequest() {
        return false;
    }

    @Override
    public String getPayload() {
        Gson gson = AylaNetworks.sharedInstance().getGson();
        JsonElement myElement = gson.toJsonTree(this);

        JsonObject root = new JsonObject();
        JsonArray commandArray = new JsonArray();
        JsonObject commandObject = new JsonObject();
        commandObject.add("cmd", myElement);
        commandArray.add(commandObject);
        root.add("cmds", commandArray);
        return root.toString();
    }
}
