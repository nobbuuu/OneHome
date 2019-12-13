package com.aylanetworks.aylasdk.error;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * Most SDK APIs take an ErrorListener as the final argument. The ErrorListener contains a single
 * method, {@link #onErrorResponse(AylaError)}, that is called if an asynchronous API call fails.
 * The specific error is returned to the caller via this method.
 */
public interface ErrorListener {
    /**
     * Callback method that an error has been occurred with the
     * provided error code and optional user-readable message.
     *
     * @param error The {@link AylaError} result for a failed operation
     */
    void onErrorResponse(AylaError error);
}
