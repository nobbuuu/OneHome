package com.aylanetworks.aylasdk;/*
 * Aura_Android 
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */

import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;

import java.util.HashMap;
import java.util.Map;

/**
 * An AylaJsonRequest is an AylaAPIRequest that contains a JSON body payload. The class will
 * return the body JSON and set the content type to MIME/JSON.
 *
 * @param <T> Class of the object returned in a successful response
 */
public class AylaJsonRequest<T> extends AylaAPIRequest<T> {
    protected byte[] _bodyData;

    public AylaJsonRequest(int method,
                           String url,
                           String jsonBody,
                           Map<String, String> headers,
                           Class<T> clazz,
                           AylaSessionManager sessionManager,
                           Response.Listener<T> successListener,
                           ErrorListener errorListener) {
        super(method, url, headers, clazz, sessionManager, successListener, errorListener);

        if (_additionalHeaders == null) {
            _additionalHeaders = new HashMap<>();
        }
        _bodyData = jsonBody.getBytes();
    }

    @Override
    public byte[] getBody() {
        return _bodyData;
    }

    @Override
    public String getBodyContentType() {
        return AylaHttpServer.MIME_JSON;
    }
}
