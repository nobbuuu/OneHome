package com.aylanetworks.aylasdk.error;/*
 * {PROJECT_NAME}
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * A ServerError represents a non-200-level HTTP error code that was received from the server.
 * The specific HTTP response code may be obtained via {@link #getServerResponseCode()}.
 */
public class ServerError extends AylaError {
    private int _serverResponseCode;
    private byte[] _serverResponseData;

    public ServerError(int serverResponseCode,
                       byte[] serverResponseData,
                       String detailMessage, Throwable cause) {
        super(ErrorType.ServerError, detailMessage, cause);
        _serverResponseCode = serverResponseCode;
        _serverResponseData = serverResponseData;
    }

    /**
     * Returns the HTTP response code received from the server (e.g. 501, 404, etc.)
     * @return The HTTP response code received from the server
     */
    public int getServerResponseCode() {
        return _serverResponseCode;
    }

    /**
     * Returns the data payload from the HTTP server, or null if no data was received in this
     * response
     * @return the payload of the server response, or null if no data was received
     */
    public byte[] getServerResponseData() {
        return _serverResponseData;
    }

    @Override
    public String getMessage() {
        return "Server error: " + getServerResponseCode() + " " + super.getMessage();
    }
}
