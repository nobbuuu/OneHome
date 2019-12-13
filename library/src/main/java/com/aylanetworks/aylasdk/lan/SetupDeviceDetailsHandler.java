package com.aylanetworks.aylasdk.lan;

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * Android_Aura
 * <p>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * HTTP server handler used to handle details(GET .status/json) received from LAN-connected setup
 * device.
 */
public class SetupDeviceDetailsHandler extends AylaHttpRouteTarget {
    private static final String LOG_TAG = "SetupDeviceDetailsHandler";

    @Override
    public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource,
                                   Map<String, String> urlParams,
                                   NanoHTTPD.IHTTPSession session) {
        String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
        AylaLog.d(LOG_TAG, "POST status from " + clientIP);

        AylaDevice device = getDevice(uriResource, session);

        if (device != null) {
            AylaLanModule module = device.getLanModule();
            if (module != null) {
                return module.handleGetStatusRequest(uriResource, urlParams, session);
            }
        }
        AylaLog.d(LOG_TAG, "SetupDeviceDetailsHandler returning 404 No LAN module found");
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "No LAN module found");
    }
}
