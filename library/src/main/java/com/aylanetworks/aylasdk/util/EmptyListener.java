package com.aylanetworks.aylasdk.util;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;

/**
 * Empty response listener for when you don't care about the response to an API request
 * @param <T> Class for the response listener
 */
public class EmptyListener<T> implements Response.Listener<T>, ErrorListener {
    @Override
    public void onResponse(T response) {

    }

    @Override
    public void onErrorResponse(AylaError error) {

    }
}
