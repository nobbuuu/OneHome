package com.aylanetworks.aylasdk.metrics;

import com.aylanetworks.aylasdk.AylaNetworks;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

/**
 * Class for cloud and lan mode latency metrics. Subclassed from {@link AylaMetric}.
 */
public class AylaLatencyMetric extends AylaMetric {

    /**
     * Enumeration for all metric types represented using this class.
     */
    public enum MetricType {
        CLOUD_LATENCY("CloudLatency"),
        LAN_LATENCY("LANModeLatency");

        private String _stringValue;
        public String stringValue(){
            return _stringValue;
        }
        MetricType(String val){
            _stringValue = val;
        }
    }

    @Expose
    private String requestUrl;      // request URL
    @Expose
    private String error;           // error if operation failed
    @Expose
    private String response;        // response for the request
    @Expose
    private long requestTotalTime;  // total time taken for this request

    /**
     * Constructor
     * @param logLevel Severity level of the log.
     * @param metricType One of the types defined in {@link MetricType}
     * @param methodName Name of the method from which this metric was generated.
     * @param requestURL Request URL.
     * @param requesTotalTime Total time taken for this request to complete.
     */
    public AylaLatencyMetric(LogLevel logLevel, MetricType metricType, String methodName,
                             String requestURL, long requesTotalTime) {
        super(logLevel, metricType.stringValue(), methodName);
        this.requestTotalTime = requesTotalTime;
        this.requestUrl = requestURL;
    }
}
