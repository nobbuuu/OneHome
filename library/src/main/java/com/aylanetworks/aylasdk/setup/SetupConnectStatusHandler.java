package com.aylanetworks.aylasdk.setup;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */


import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.lan.AylaHttpRouteTarget;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanModule;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * HTTP handler used to process connect status messages from the device used by {@link AylaSetup}
 * when configuring the device's WiFi settings
 */
public class SetupConnectStatusHandler extends AylaHttpRouteTarget {
    private static final String LOG_TAG = "SetupConnect";

    @Override
    public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource,
                                   Map<String, String> urlParams,
                                   NanoHTTPD.IHTTPSession session) {
        String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
        AylaLog.d(LOG_TAG, "POST connect status from setup device:" + clientIP);

        AylaSetupDevice setupDevice = uriResource.initParameter(1, AylaSetupDevice.class);

        if ( setupDevice != null ) {
            AylaLanModule module = setupDevice.getLanModule();
            if (module != null) {
                return module.handleSetupConnectStatus(uriResource, urlParams, session);
            }
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "No LAN module found");
    }
}

