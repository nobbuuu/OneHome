package com.aylanetworks.aylasdk.error;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * A PreconditionError is generated when a request is made that can not be completed due to a
 * precondition not being met. Calling SDK methods before initializing the SDK or attempting to
 * interact with a device in Setup that has not yet been connected are examples of
 * situations that would produce a PreconditionError.
 */
public class PreconditionError extends AylaError {
    public PreconditionError(String message) {
        super(ErrorType.Precondition, message);
    }

    public PreconditionError(String message, Throwable cause) {
        super(ErrorType.Precondition, message, cause);
    }
}
