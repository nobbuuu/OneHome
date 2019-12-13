package com.aylanetworks.aylasdk.lan;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.ota.AylaLanOTADevice;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * Base class for objects that process incoming HTTP requests from LAN devices. The base class
 * provides a helper method to find the device the message is intended for.
 */
public class AylaHttpRouteTarget extends RouterNanoHTTPD.DefaultHandler  {
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

    /**
     * Returns an AylaDevice that is the intended target of the message. If a setup device has
     * been registered with the webserver, that will be returned. Otherwise the device that
     * matches the message's destination IP address will be returned, or null if no such device
     * is found.
     * @param uriResource uriResource passed into any of the get / post / put / delete methods
     * @param session Session passed into any of the above-mentioned methods
     * @return an AylaDevice that is the target of this message, or null if not found
     */
    protected AylaDevice getDevice(RouterNanoHTTPD.UriResource uriResource,
                                   NanoHTTPD.IHTTPSession session) {
        // See if we have a setup device. If so, we will use that.
        AylaDevice device = uriResource.initParameter(1, AylaSetupDevice.class);
        if ( device == null ) {
            AylaSessionManager sm = uriResource.initParameter(0, AylaSessionManager.class);
            if ( sm != null) {
                AylaDeviceManager dm;
                dm = sm.getDeviceManager();

                String clientIP = session.getHeaders().get(AylaHttpServer.HEADER_CLIENT_IP);
                device = dm.deviceWithLanIP(clientIP);
            }
        }

        return device;
    }

    protected AylaLanOTADevice getOTADevice(RouterNanoHTTPD.UriResource uriResource,
                                            NanoHTTPD.IHTTPSession session){
        AylaLanOTADevice device = uriResource.initParameter(1, AylaLanOTADevice.class);
        return device;
    }
}
