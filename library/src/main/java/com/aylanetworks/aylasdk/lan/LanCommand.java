package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.error.AylaError;

/**
 * Represents a LAN command sent to the device. LAN commands are queued by the AylaLanModule for
 * the device, and are returned via responses to requests for commands from the device.
 */
public abstract class LanCommand {
    protected String _moduleResponse;
    protected AylaError _responseError;
    private int _requestTimeout;

    public LanCommand() {
        _requestTimeout = AylaNetworks.sharedInstance().getSystemSettings().defaultNetworkTimeoutMs;
    }

    /**
     * Returns the cleartext payload for the LAN command. The response body is a
     * JSON string containing the command data in the format appropriate for the specific command.
     *
     * This method must be overridden by implementers to return the JSON payload of the LAN command.
     *
     * @return Unencrypted response JSON for this command.
     */
    public abstract String getPayload();

    /**
     * Returns true if the command expects to receive a request from the module as a result of
     * this command. Some commands, such as the {@link CreateDatapointCommand}, do not expect an
     * additional request from the module after delivering the command payload, and will return
     * false from this method instead.
     *
     * @return true if the command expects to receive a request from the module, false otherwise
     */
    public boolean expectsModuleRequest() {
        return true;
    }

    /**
     * The CreateDatapointCommand is currently the only command that expects an ack to be
     * received after the command has been delivered, and only if the property being updated is
     * configured to be ack-enabled
     * @return true if this command needs to wait for an ack from the module before returning
     * success
     */
    public boolean needsAck() {
        return false;
    }

    /**
     * Sets the response for this command and notifies waiters that we have been processed
     *
     * @param response Response from the module
     */
    public void setModuleResponse(String response) {
        _moduleResponse = response;
        synchronized (this) {
            notify();
        }
    }

    /**
     * Returns the response text from the module, if received
     *
     * @return the module's response string, or null if a response was not received
     */
    public String getModuleResponse() {
        return _moduleResponse;
    }

    public void setModuleError(AylaError error) {
        _responseError = error;
        synchronized (this) {
            notify();
        }
    }

    /**
     * Sets an error response for this command and notifies waiters that we have been processed
     *
     * @param error Error encountered while trying to service this command
     */
    public void setErrorResponse(AylaError error) {
        _responseError = error;
        synchronized (this) {
            notify();
        }
    }

    /**
     * Returns the error from the module, if received
     *
     * @return the module's returned error, or null if an error was not received
     */
    public AylaError getResponseError() {
        return _responseError;
    }

    /**
     * Returns true if this command has received a response from the device. The response may be
     * an error or a string, retreived by getResponseError() or getModuleResponse().
     *
     * @return True if this command has received a response
     */
    public boolean receivedResponse() {
        return _moduleResponse != null || _responseError != null;
    }

    /**
     * Returns the timeout in milliseconds for this command.
     *
     * @return Timeout for this command in milliseconds
     */
    public int getRequestTimeout() {
        return _requestTimeout;
    }

    /**
     * Sets the timeout in milliseconds for this command.
     *
     * @param timeout Time in milliseconds we will wait for this command to complete
     */
    public void setRequestTimeout(int timeout) {
        _requestTimeout = timeout;
    }
}
