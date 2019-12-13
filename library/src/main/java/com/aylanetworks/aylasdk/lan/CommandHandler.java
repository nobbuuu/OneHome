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
 * Handles LAN mode command requests from a device. This handler will find the appropriate LAN
 * module and route the request to it's handleLanCommandRequest() method.
 */
public class CommandHandler extends AylaHttpRouteTarget {
    private static final String LOG_TAG = "CommandHandler";

    @Override
    public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource,
                                  Map<String, String> urlParams,
                                  NanoHTTPD.IHTTPSession session) {
        AylaDevice device = getDevice(uriResource, session);

        if ( device == null ) {
            // Nobody around to service the request
            String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
            AylaLog.e(LOG_TAG, "No device for LAN command from " + clientIP);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "No device found");
        }

        AylaLog.d(LOG_TAG, "GET COMMAND request from " + device.getDsn());

        AylaLanModule module = device.getLanModule();
        if ( module != null ) {
            return module.handleLanCommandRequest(uriResource, urlParams, session);
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "No LAN module found");
    }
}
