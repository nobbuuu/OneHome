package com.aylanetworks.aylasdk.error;/*
 * {PROJECT_NAME}
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * InvalidArgumentErrors are generated when an API method is called with invalid arguments, such
 * as null values where values are required, etc.
 */
public class InvalidArgumentError extends AylaError {
    public InvalidArgumentError(String detailMessage) {
        super(ErrorType.InvalidArgument, detailMessage);
    }
    public InvalidArgumentError(String detailMessage, Throwable cause) {
        super(ErrorType.InvalidArgument, detailMessage, cause);
    }
}
