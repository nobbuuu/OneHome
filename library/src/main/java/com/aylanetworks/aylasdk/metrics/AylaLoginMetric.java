package com.aylanetworks.aylasdk.metrics;

import com.aylanetworks.aylasdk.AylaNetworks;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

/**
 * Class for login related metrics. Subclassed from {@link AylaMetric}.
 */
public class AylaLoginMetric extends AylaMetric {

    @Expose
    private String authProvider;        // type of auth provider
    @Expose
    private String result;              // result of the sign-in operation
    @Expose
    private String error;               // error information if sign-in failed
    @Expose
    private Long requestTotalTime;      // total time taken for this request

    /**
     * Enumeration for all metric types represented using this class.
     */
    public enum MetricType {
        LOGIN_SUCCESS("LoginSuccess"),
        LOGIN_FAILURE("LoginFailure"),
        LAN_LOGIN_SUCCESS("LANLoginSuccess"),
        LAN_LOGIN_FAILURE("LANLoginFailure"),
        LOGOUT_SUCCESS("LogoutSuccess"),
        LOGOUT_FAILURE("LogoutFailure");

        private String _stringValue;
        MetricType(String val){
            _stringValue = val;
        }
        public String stringValue(){
            return _stringValue;
        }
    }

    /**
     * Constructor
     * @param logLevel Severity of the log.
     * @param metricType One of the metric types defined in {@link MetricType}.
     * @param methodName Name of the method from which this metric was generated.
     * @param authProvider Type of auth provider for this login session.
     * @param result Result of the sign-in. One of the types defined in
     * {@link com.aylanetworks.aylasdk.metrics.AylaMetric.Result}
     * @param error Error information if sign-in failed.
     */
    public AylaLoginMetric(LogLevel logLevel, MetricType metricType, String methodName,
                           String authProvider, Result result, String error) {
        super(logLevel, metricType.stringValue(), methodName);
        this.result = result.stringValue();
        this.authProvider = authProvider;
        this.error = error;
    }

    /**
     * Set total time taken for this request if the request was successful.
     * @param totalTime Total time taken for the request in milliseconds.
     */
    public void setRequestTotalTime(long totalTime){
        this.requestTotalTime = totalTime;
    }

}
