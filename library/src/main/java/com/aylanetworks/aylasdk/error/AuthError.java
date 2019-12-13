package com.aylanetworks.aylasdk.error;/*
 * {PROJECT_NAME}
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.AuthFailureError;
import com.android.volley.VolleyError;

/**
 * An AuthError is returned when the server fails to authenticate a request. Generally an
 * AuthError indicates that the session is not valid and the user needs to sign-in again. In the
 * case of LAN requests made directly to devices, AuthErrors indicate errors related to the
 * secure key exchange or failed encryption.
 */
public class AuthError extends AylaError {
    private String _detailMessage;

    public AuthError(String message, Throwable cause) {
        super(ErrorType.AuthError, message, cause);
        _detailMessage= message;
    }

    @Override
    public String getMessage() {
        Throwable cause = getCause();
        if (cause instanceof VolleyError) {
            VolleyError volleyError = (VolleyError)cause;
            if (volleyError.networkResponse != null) {
                return "Received HTTP status " + volleyError.networkResponse.statusCode;
            }
        }
        return "Unauthorized";
    }
    public String getDetailMessage() {
        return _detailMessage;
    }
}
