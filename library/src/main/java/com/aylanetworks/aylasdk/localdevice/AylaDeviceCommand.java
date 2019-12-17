package com.aylanetworks.aylasdk.localdevice;

/**
 * AMAP_Android
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;

/**
 * Represents a command for a LocalDevice as fetched from the cloud service
 */
public class AylaDeviceCommand {
    @Expose public int id;
    @Expose public String cmd_type;
    @Expose public String data;
    @Expose public int device_id;
    @Expose public String method;
    @Expose public String resource;
    @Expose public boolean ack;
    @Expose public String acked_at;
    @Expose public String created_at;
    @Expose public String updated_at;

    // List of supported command types
    public static final String CMD_OTA = "ota.json";
    public static final String CMD_FACTORY_RESET = "reset.json?factory=1";

    private WeakReference<AylaLocalDevice> _deviceRef;

    /**
     * Called to acknowledge the status of a command operation. This method should be called once
     * a command has been serviced to relay the results to the cloud service.
     * @param responseCode HTTP response code as the result for this command
     * @param successListener Listener called upon successful completion
     * @param errorListener Listener called if an error occurred
     * @return the AylaAPIRequest for this operation, which may be used to cancel the operation
     */
    public AylaAPIRequest ack(int responseCode,
                              Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                              ErrorListener errorListener){
        AylaLocalDevice device = (_deviceRef == null ? null : _deviceRef.get());
        if (device == null) {
            errorListener.onErrorResponse(new PreconditionError("No device reference present"));
            return null;
        }

        AylaSessionManager sessionManager = _deviceRef.get().getSessionManager();
        String url = sessionManager.getDeviceManager().deviceServiceUrl("apiv1/dsns/" +
                device.getDsn() + "/cmds/" + id + "/ack.json");

        String json = "{\"status\": " + responseCode + "}";
        AylaAPIRequest request = new AylaJsonRequest<>(Request.Method.PUT, url, json, null,
                AylaAPIRequest.EmptyResponse.class, sessionManager,
                successListener, errorListener);

        return request;
    }

    public void setDevice(AylaLocalDevice device) {
        _deviceRef = new WeakReference<AylaLocalDevice>(device);
    }

    public static class Wrapper {
        @Expose public AylaDeviceCommand cmd;
    }

    /**
     * Returns an object created from  the "data" portion of the device command. If an
     * unsupported command type is found, this method will return null.
     * @return the command object contained within the data field of this object
     */
    public Object getCommand() {
        Gson gson = AylaNetworks.sharedInstance().getGson();
        String escapedData;
        String escapedURL;
        try {
            escapedData = URLDecoder.decode(data, "utf-8");
        } catch (UnsupportedEncodingException e) {
            escapedData = null;
        }

        AylaLog.i("BSK", "Escaped: " + escapedData + "\nunescaped: " + data);
        switch (resource) {
            case CMD_OTA:
                LocalOTACommand cmd = LocalOTACommand.fromData(data);
                if (cmd != null) {
                    cmd.command_id = id;
                }
                return cmd;
        }
        return null;
    }

    public static AylaDeviceCommand[] unwrap(Wrapper[] wrapped) {
        if (wrapped == null) {
            return null;
        }
        AylaDeviceCommand[] unwrappedCommands = new AylaDeviceCommand[wrapped.length];
        for (int i = 0; i < wrapped.length; i++) {
            unwrappedCommands[i] = wrapped[i].cmd;
        }

        return unwrappedCommands;
    }
}
