package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaNetworks;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

// For more information, see the WLAN Local Support document found here:
// https://docs.google.com/document/d/1TuCeJsavozN8yWskM3KT612jMcH45DlfC_rb06myReM

/**
 * Represents a LAN command sent to the device. LAN commands are queued by the AylaLanModule for
 * the device, and are returned via responses to requests for commands from the device.
 */
public class AylaLanCommand extends LanCommand {
    private static final String LOG_TAG = "AylaLanCommand";


    @Expose
    private int cmd_id;
    @Expose
    public String method;
    @Expose
    public String resource;
    @Expose
    public String data;
    @Expose
    public String uri;

    private static int __nextLanCommandId = 0;

    /**
     * Constructor
     * @param method HTTP method as a string, e.g. "GET" or "POST"
     * @param resource Resource to request, e.g. "property.json?name=MyProperty"
     * @param data Additional data for the request, optional
     * @param uri URI the device should deliver the response to, e.g.
     *            "/local_lan/property/datapoint.json"
     */
    public AylaLanCommand(String method, String resource, String data, String uri) {
        super();
        cmd_id = __nextLanCommandId++;
        this.method = method;
        this.resource = resource;
        this.data = (data == null ? "" : data);
        this.uri = uri;
    }

    /**
     * Returns the ID for this command. These IDs are auto-generated when the object is
     * created.
     *
     * @return The ID for this command
     */
    public int getCommandId() {
        return this.cmd_id;
    }

    /**
     * Helper method to return the cleartext payload for the LAN command. The response body is a
     * JSON string containing the command data in the format required by the device.
     *
     * @return Unencrypted response JSON for this command
     */
    public String getPayload() {
        // Response bodies are in this format:
        // {
        //    "cmds" :  [
        //    {
        //        "cmd": {
        //             "cmd_id": "<cmd-cmd_id>", (unsigned 32-bit int)
        //             "method": "<cmd-method>",
        //             "resource": "<cmd-resource>",
        //             "data": "<data-for-cmd>",
        //             "uri": "<uri-for-response>"
        //         }
        //    }]
        // }

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

    /**
     * Returns an AylaLanCommand initialized for a request to get a property
     * @param propertyName Name of the property to get
     * @return The AylaLanCommand initialized to get the provided property's value
     */
    public static AylaLanCommand newGetPropertyCommand(String propertyName) {
        return new AylaLanCommand("GET", "property.json?name=" + propertyName, null,
                "/local_lan/property/datapoint.json");
    }

    public String toString() {
        return "LanCmd[" + getCommandId() + "]=" + resource;
    }
}
