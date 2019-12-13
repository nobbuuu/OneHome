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
 * Class for wifi setup metrics. This is a subclass of abstract class {@link AylaMetric}.
 */
public class AylaSetupMetric extends AylaMetric {

    /**
     * Enumeration for all metric types represented using this class.
     */
    public enum MetricType {
        SETUP_SUCCESS("SetupSuccess"),
        SETUP_FAILURE("SetupFailure");

        private String _stringValue;
        public String stringValue(){
            return _stringValue;
        }
        MetricType(String val){
            _stringValue = val;
        }
    }

    @Expose
    private String setupSessionId;      // unique id to track this setup session
    @Expose
    private String result;              // result of the operation
    @Expose
    private String error;               // error information if the operation failed
    @Expose
    private boolean isSecureSetup;      // false if device uses cleartext setup
    @Expose
    private String deviceSecurityType;  // securoty type of the device's AP
    @Expose
    private Long requestTotalTime;      // total time taken for the request to complete

    /**
     * Constructor
     * @param logLevel Severity level of the log.
     * @param metricType One of the metric types defined in {@link MetricType}.
     * @param methodName Name of the method from which this metric was generated.
     * @param setupSessionId Unique identifier for this setup session.
     * @param result Result of the sign-in. One of the types defined in
     * {@link com.aylanetworks.aylasdk.metrics.AylaMetric.Result}
     * @param error Error information if operation failed.
     */
    public AylaSetupMetric(LogLevel logLevel, MetricType metricType, String
            methodName, String setupSessionId, Result result, String error) {
        super(logLevel, metricType.stringValue(), methodName);
        this.setupSessionId = setupSessionId;
        this.result = result.stringValue();
        this.error = error;
    }

    /**
     * Set total time for this request in the metric json.
     * @param totalTime total time for this request
     */
    public void setRequestTotalTime(long totalTime){
        this.requestTotalTime = totalTime;
    }

    /**
     * Set to true if device uses secure server for setup.
     * @param isSecureSetup true if device uses secure server for setup.
     */
    public void secureSetup(boolean isSecureSetup){
        this.isSecureSetup = isSecureSetup;
    }

    /**
     * Set device's security type in the metric json.
     * @param securityType Security type of the device (WPA2_Personal, WPA, WEP, None)
     */
    public void setDeviceSecurityType(String securityType){
        this.deviceSecurityType = securityType;
    }
}
