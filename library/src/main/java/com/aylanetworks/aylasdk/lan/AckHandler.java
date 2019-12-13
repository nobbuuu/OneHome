package com.aylanetworks.aylasdk.lan;
/*
 * Aura_Android
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaDevice;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * HTTP handler used to handle connection status change events from LAN devices
 */
public class AckHandler extends AylaHttpRouteTarget {
    private final static String LOG_TAG = "ConnectStatusHandler";

    @Override
    public String getText() {
        return null;
    }

    @Override
    public String getMimeType() {
        return AylaHttpServer.MIME_JSON;
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        return NanoHTTPD.Response.Status.OK;
    }

    @Override
    public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String>
            urlParams, NanoHTTPD.IHTTPSession session) {
        AylaDevice device = getDevice(uriResource, session);
        if (device == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    AylaHttpServer.MIME_PLAINTEXT, "No device found");
        }

        AylaLanModule lanModule = device.getLanModule();
        if (lanModule == null) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    AylaHttpServer.MIME_PLAINTEXT, "Device is not in LAN mode");
        }

        return lanModule.handleDatapointAck(uriResource, urlParams, session);
    }
}

