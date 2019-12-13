package com.aylanetworks.aylasdk.lan;

import com.aylanetworks.aylasdk.AylaNetworks;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

import java.util.Map;

/**
 * Android_Aura
 * <p/>
 * Copyright 2016 Ayla Networks, all rights reserved
 */
public class DeleteSessionCommand extends LanCommand {

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

    public DeleteSessionCommand() {
        super();
        this.cmdId = 0;
        this.method = "DELETE";
        this.resource = "local_reg.json";
        this.data = "delete_session";
        this.uri = "/local_lan";
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
