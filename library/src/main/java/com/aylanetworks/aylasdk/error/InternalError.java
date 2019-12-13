package com.aylanetworks.aylasdk.error;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * An InternalError represents an unrecoverable error that occurred within the SDK. These errors
 * should not happen during runtime and indicate a problem with the internal state of the SDK.
 */
public class InternalError extends AylaError {
    public InternalError(String detailMessage) {
        super(AylaError.ErrorType.Internal, detailMessage);
    }
    public InternalError(String detailMessage, Throwable cause) {
        super(AylaError.ErrorType.Internal, detailMessage, cause);
    }}
