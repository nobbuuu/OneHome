package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.error.TimeoutError;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Network implementation that can handle AylaLanRequests.
 * Requests that are AylaLanRequests are handled in the performRequest method by iterating
 * through each of their commands and gathering responses from the device for each. Once the
 * responses (or errors) have been gathered for each request, the method returns as if it were a
 * single request.
 *
 * Requests that are not AylaLanRequest objects are passed through to the "passthrough network",
 * a "real" network used by Volley. This network is passed in as a parameter to the constructor.
 */
public class AylaLocalNetwork implements Network {
    private final static String LOG_TAG = "LocalNetwork";

    /**
     * Network we will pass requests to that are not AylaLanRequests
     */
    private Network _passthroughNetwork;

    /**
     * Constructor
     * @param passthroughNetwork Network used to pass non-AylaLanRequests to
     */
    public AylaLocalNetwork(Network passthroughNetwork) {
        _passthroughNetwork = passthroughNetwork;
    }

    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        long startTime = System.nanoTime();
        Map<String, String> responseHeaders = Collections.emptyMap();

        if ( !(request instanceof AylaLanRequest) ) {
            // Pass this request through
            return _passthroughNetwork.performRequest(request);
        }

        // Handle this request ourselves. We need to send a local_reg packet to the device,
        // which will later make an HTTP request to our webserver with the reply. We will block
        // here until the reply comes through or the timeout has been exceeded.
        AylaLanRequest lanRequest = (AylaLanRequest)request;
        AylaDevice device = lanRequest.getDevice();
        if ( device == null ) {
            // Can't work without a device
            return new NetworkResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR.getRequestStatus(),
                    null, null, false, 0);
        }

        AylaLanModule lanModule = device.getLanModule();
        if ( lanModule == null ) {
            // Can't work without a LAN module
            return new NetworkResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR.getRequestStatus(),
                    null, null, false, 0);
        }

        List<LanCommand> lanCommands = lanRequest.getLanCommands();
        lanModule.registerCommands(lanCommands);

        // First send out the local_reg packet to let the module know we have a command
        lanModule.sendLocalRegistration();
        lanModule.setProcessingCommandBlock(true);

        TimeoutError timeoutError = null;
        for (LanCommand command : lanCommands) {
            AylaLog.d(LOG_TAG, "Waiting for response to: " + command);
            if (timeoutError != null) {
                // We're going to just set the error on all remaining commands
                command.setErrorResponse(timeoutError);
                continue;
            }

            // Now wait for the response
            long startCommandTime = System.nanoTime();

            // We know this variable is not really local, but taken from the command queue
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (command) {
                while (!command.receivedResponse()) {
                    // See if we timed out
                    if ( ((System.nanoTime() - startCommandTime) / 1000000) >
                            command.getRequestTimeout()) {
                        AylaLog.e(LOG_TAG, "Timed out waiting for command response: " + command);
                        timeoutError = new TimeoutError("Timed out waiting for command " +
                                "response: " + command);
                        command.setErrorResponse(timeoutError);
                        break;
                    }

                    try {
                        AylaLog.d(LOG_TAG, "Waiting for command response for " + command);
                        command.wait(command.getRequestTimeout());
                    } catch (InterruptedException e) {
                        // Do nothing
                        AylaLog.d(LOG_TAG, "Interrupted exception");
                    }
                }
            }
        }

        lanModule.setProcessingCommandBlock(false);
        long requestTime = System.nanoTime() - startTime;
        byte[] data = "OK".getBytes();
        return new NetworkResponse(NanoHTTPD.Response.Status.OK.getRequestStatus(), data,
                responseHeaders, false, requestTime / 1000000);
    }
}
