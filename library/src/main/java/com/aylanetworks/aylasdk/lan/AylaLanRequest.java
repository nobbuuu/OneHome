package com.aylanetworks.aylasdk.lan;

import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.metrics.AylaLatencyMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * Request objects for LAN operations. An AylaLanRequest contains a list of AylaLanCommands
 * intended to be processed as a single operation.
 * <p>
 * All AylaLanRequest objects return a LanResponse, which contains the list of completed commands
 * that either have a response or an error returned. Each command holds its own response or error.
 * <p>
 * When this request is processed in a queue that was initialized with an {@link AylaLocalNetwork}
 * object, it will process each LAN command in order and gather the results. Even if each LAN
 * command fails, the AylaLanRequest can still succeed, though each LanCommand contained within
 * the request object will contain an error.
 * <p>
 * The AylaLanRequest itself can fail if the device is not found or the LAN session is not
 * running at the time the request is sent out.
 * <p>
 * Generally, this request type is used internally and the results collated into a different type
 * of response for an API. See {@link AylaDevice#fetchPropertiesLAN(String[], Response.Listener, ErrorListener)}
 * for an example of this.
 */
public class AylaLanRequest extends AylaAPIRequest<AylaLanRequest.LanResponse> {
    private List<LanCommand> _lanCommands;
    private WeakReference<AylaDevice> _deviceRef;
    private final static String LOG_TAG = "AylaLanRequest";

    /**
     * Constructor
     *
     * @param device AylaDevice this request is meant for
     * @param lanCommands List of commands that compose this request
     * @param sessionManager The {@link AylaSessionManager} that owns our device
     * @param successListener Listener to receive the results of a successful operation
     * @param errorListener Listener to receive errors in case of failure
     */
    public AylaLanRequest(AylaDevice device, List<LanCommand> lanCommands,
                          AylaSessionManager sessionManager,
                          Response.Listener<LanResponse> successListener,
                          final ErrorListener errorListener) {
        super(Method.GET, "local", null, LanResponse.class, sessionManager, successListener,
                errorListener);
        _deviceRef = new WeakReference<AylaDevice>(device);
        _lanCommands = lanCommands;
    }

    /**
     * Constructor taking in a single LanCommand
     * @param device AylaDevice this request is meant for
     * @param lanCommand Single command that composes this request
     * @param sessionManager The {@link AylaSessionManager} that owns our device
     * @param successListener Listener to receive the results of a successful operation
     * @param errorListener Listener to receive errors in case of failure
     */
    public AylaLanRequest(AylaDevice device, LanCommand lanCommand,
                          AylaSessionManager sessionManager,
                          Response.Listener<LanResponse> successListener,
                          final ErrorListener errorListener) {
        super(Method.GET, "local", null, LanResponse.class, sessionManager, successListener,
                errorListener);
        _deviceRef = new WeakReference<>(device);
        _lanCommands = new ArrayList<>();
        _lanCommands.add(lanCommand);
    }

    public List<LanCommand> getLanCommands() {
        return _lanCommands;
    }

    public AylaDevice getDevice() {
        return _deviceRef.get();
    }

    @Override
    protected Response<LanResponse> parseNetworkResponse(NetworkResponse response) {
        _networkResponseTimestamp = System.currentTimeMillis();
        _networkResponse = response;

        // Unregister our commands with the LAN module in case they were not already removed
        AylaDevice device = _deviceRef.get();
        if ( device != null ) {
            AylaLanModule module = device.getLanModule();
            if ( module != null ) {
                module.unregisterCommands(_lanCommands);
            }
        }

        if (response.statusCode == NanoHTTPD.Response.Status.OK.getRequestStatus()) {
            LanResponse lanResponse = new LanResponse();
            lanResponse.completedCommands = _lanCommands;

            AylaMetricsManager metricsManager = AylaNetworks.isShutDown() ? null
                    : AylaNetworks.sharedInstance().getMetricsManager();
            if(metricsManager != null && !metricsManager.isUploadPaused() &&
                    metricsManager.shouldLogLANLatency()){
                AylaLog.d(LOG_TAG, "Logging Latency now");
                AylaLatencyMetric latencyMetric = new AylaLatencyMetric(AylaMetric.LogLevel.INFO,
                        AylaLatencyMetric.MetricType.LAN_LATENCY, "parseNetworkResponse",
                        getUrl(), getNetworkTimeMs());
                latencyMetric.setMetricText("Number of commands: "+_lanCommands.size());
                metricsManager.setLANLatencyVariables(System.currentTimeMillis(),
                        getNetworkTimeMs());
                metricsManager.addMessageToUploadsQueue(latencyMetric);
            }else{
                AylaLog.d(LOG_TAG, "NOT logging Latency. NOT time yet!!");
            }

            return Response.success(lanResponse, HttpHeaderParser.parseCacheHeaders(response));
        }
        return Response.error(new NetworkError(response));
    }

    public static class LanResponse {
        public List<LanCommand> completedCommands;
    }
}
