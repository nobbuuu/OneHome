package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

import java.util.Map;

/**
 * LAN command used to create a datapoint on a LAN-connected device
 */
public class CreateDatapointCommand<T> extends LanCommand {
    private static final String LOG_TAG = "CreateDPCommand";

    @Expose private String name;
    @Expose
    private T value;
    @Expose
    private String base_type;
    @Expose
    private Map<String, String> metadata;
    @Expose
    private String dsn;
    @Expose
    private String id;      // ID used to match ack responses to the command, only for ackEnabled

    // Storage for listeners to AylaProperty.createDatapointLAN so we can notify when an ACK
    // comes back
    private Response.Listener<AylaDatapoint<T>> _successListener;
    private ErrorListener _errorListener;
    private int _ackTimeout = 10;               // 10 seconds to wait for an ack by default

    private boolean _ackEnabled;

    final private boolean isNodePayload;

    public CreateDatapointCommand(AylaProperty<T> property, T value,
                                  Map<String, String> metadata, int ackTimeout) {
        super();
        this.name = property.getName();
        this.value = value;
        this.dsn = property.getOwner().getDsn();
        this.isNodePayload = property.getOwner().isNode();
        this.metadata = metadata;
        this.base_type = property.getBaseType();
        this._ackEnabled = property.isAckEnabled();
        _ackTimeout = ackTimeout;
        if (this._ackEnabled) {
            id = ObjectUtils.generateRandomToken(8);
        }
    }

    /**
     * Returns the name of the property updated with this command
     * @return the name of the property updated with this command
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the DSN of the device where this command was initiated from.
     * @return the DSN of the device where this command was initiated from.
     */
    public String getDsn() {
        return dsn;
    }

    /**
     * The value this command will set
     * @return the value this command will set
     */
    public T getValue() {
        return value;
    }

    public String getBaseType() {
        return base_type;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Returns the timeout in seconds to wait for an ack for a property update
     * @return the timeout in seconds to wait for an ack for a property update
     */
    public int getAckTimeout() {
        return _ackTimeout;
    }

    /**
     * Returns the ID used for ack-enabled properties
     * @return the ID used for ack-enabled properties, or null if this is not an ack-enabled command
     */
    public String getId() {
        return id;
    }

    public void prepareForAck(int timeoutInSeconds,
                           Response.Listener<AylaDatapoint<T>> successListener,
                           ErrorListener errorListener) {
        _ackTimeout = timeoutInSeconds;
        _successListener = successListener;
        _errorListener = errorListener;
    }

    public Response.Listener<AylaDatapoint<T>> getSuccessListener() {
        return _successListener;
    }

    public ErrorListener getErrorListener() {
        return _errorListener;
    }

    @Override
    public boolean expectsModuleRequest() {
        return false;
    }

    /**
     * We expect a request from the module after delivering our payload if the module will
     * deliver an ack. We know this if the property's ackEnabled value is true.
     *
     * @return True if the property's ackEnabled value is non-zero
     */
    @Override
    public boolean needsAck() {
        return _ackEnabled;
    }

    @Override
    public String getPayload() {

        // Response bodies are in this format:
        // {
        //    "properties" | "node_properties" :  [
        //    {
        //        "property": {
        //             "base_type": "boolean",
        //             "value": "1",
        //             "name": "My_Property_Name",
        //             "id": "blabla", // only for "needs_explicit_ack" properties ???
        //             "metadata": "metadata string"
        //             "dsn" : "<DSN string, for nodes only>"
        //         }
        //    }]
        // }

        Gson gson = AylaNetworks.sharedInstance().getGson();
        JsonElement myElement = gson.toJsonTree(this);

        JsonObject root = new JsonObject();
        JsonArray propertyArray = new JsonArray();
        JsonObject propertyObject = new JsonObject();
        propertyObject.add("property", myElement);
        propertyArray.add(propertyObject);

        root.add(isNodePayload ? "node_properties" : "properties", propertyArray);

        return root.toString();
    }

    @Override
    public String toString() {
        return "CreateDatapointCommand: " + name + "=" + value;
    }

    /**
     * Class representing the JSON datapoint ack message from the module in LAN mode
     */
    public static class CreateDatapointAck {
        @Expose
        public String id;
        @Expose
        public int ack_status;
        @Expose
        public Integer ack_message;
        @Expose
        public String dsn;              // Node DSN for node property acks

        public String toString() {
            return "ACK: [" + id + "] status: " + ack_status + " message: " +
                    ack_message;
        }
    }
}
