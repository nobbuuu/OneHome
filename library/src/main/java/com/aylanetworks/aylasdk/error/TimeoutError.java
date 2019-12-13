package com.aylanetworks.aylasdk.error;/*
 * {PROJECT_NAME}
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * A TimeoutError is generated when a network operation did not complete before the network
 * timeout time has elapsed.
 */
public class TimeoutError extends AylaError {
    public TimeoutError(String detailMessage) {
        super(ErrorType.Timeout, detailMessage, null);
    }

    public TimeoutError(String detailMessage, Throwable cause) {
        super(ErrorType.Timeout, detailMessage, cause);
    }
}
