package com.aylanetworks.aylasdk.error;/*
 * {PROJECT_NAME}
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

/**
 * A NetworkError is generated when network conditions prevent a request from being completed.
 * Usually these errors occur when the mobile device is not connected to the Internet, cannot
 * reach the Ayla Cloud Service, or in the case of LAN mode devices, the device could not be
 * reached via the current network.
 */
public class NetworkError extends AylaError {
    public NetworkError(String detailMessage, Throwable cause) {
        super(ErrorType.NetworkError, detailMessage, cause);
    }

    @Override
    public String getMessage() {
        Throwable t = getCause();
        if ( t != null) {
            return t.getMessage();
        }

        if (super.getMessage() == null) {
            return "Network error (unspecified)";
        }
        return super.getMessage();
    }
}
