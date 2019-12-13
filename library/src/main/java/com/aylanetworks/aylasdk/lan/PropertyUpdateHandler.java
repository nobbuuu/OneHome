package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * HTTP server handler used to handle property update events from LAN-connected devices
 */
public class PropertyUpdateHandler extends AylaHttpRouteTarget {
    private static final String LOG_TAG = "PropertyUpdateHandler";

    @Override
    public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource,
                                   Map<String, String> urlParams,
                                   NanoHTTPD.IHTTPSession session) {
        String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
        AylaLog.d(LOG_TAG, "POST datapoint update from " + clientIP);

        AylaDevice device = getDevice(uriResource, session);

        if (device != null) {
            AylaLanModule module = device.getLanModule();
            if (module != null) {
                if (uriResource.getUri().endsWith("ack.json")) {
                    return module.handleDatapointAck(uriResource, urlParams, session);
                } else {
                    return module.handlePropertyUpdateRequest(uriResource, urlParams, session);
                }
            }
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "No LAN module found");
    }
}
