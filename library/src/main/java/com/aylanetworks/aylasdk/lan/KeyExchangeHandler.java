package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

public class KeyExchangeHandler extends AylaHttpRouteTarget {
    private static final String LOG_TAG = "KeyExchange";

    @Override
    public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String>
            urlParams, NanoHTTPD.IHTTPSession session) {

        // Get the input data length
        Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
        String keyExchangeJSON = ObjectUtils.inputStreamToString(session.getInputStream(),
                contentLength);

        if (keyExchangeJSON == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    AylaHttpServer.MIME_PLAINTEXT, "Could not read POST body");
        }

        // {"key_exchange":{"ver":1,"random_1":"0JXFK+yPz07LzHV1","time_1":107092651451943,
        // "proto":1,"key_id":65478}}

        Gson gson = AylaNetworks.sharedInstance().getGson();
        AylaLanModule.KeyExchangeWrapper wrapper = gson.fromJson(keyExchangeJSON,
                AylaLanModule.KeyExchangeWrapper.class);

        AylaLanModule.KeyExchange keyExchange = wrapper.keyExchange;

        AylaDevice device = getDevice(uriResource, session);
        if (device != null) {
            AylaLanModule module = device.getLanModule();
            if (module != null) {
                return module.handleKeyExchangeRequest(keyExchange);
            }
        }
        String jsonErrorBody = AylaLanModule.getJSONErrorBody("No device or LAN module found");
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                AylaHttpServer.MIME_JSON, jsonErrorBody);
    }
}
