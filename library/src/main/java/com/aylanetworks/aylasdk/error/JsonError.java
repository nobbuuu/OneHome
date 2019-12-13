package com.aylanetworks.aylasdk.error;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * A JsonError is generated when the SDK encounters invalid JSON data that could not be parsed.
 * The {@link #getJson()}
 */
public class JsonError extends AylaError {
    private String _json;

    public JsonError(String json, String detailMessage, Throwable cause) {
        super(ErrorType.JsonError, detailMessage, cause);
        _json = json;
    }

    public String getJson() {
        return _json;
    }
}
