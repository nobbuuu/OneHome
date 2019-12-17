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
 * Class for device registration related metrics. Subclassed from {@link AylaMetric}.
 */
public class AylaRegistrationMetric extends AylaMetric {

    /**
     * Enumeration for all metric types represented using this class.
     */
    public enum MetricType {
        REGISTRATION_SUCCESS("RegistrationSuccess"),
        REGISTRATION_FAILURE("RegistrationFailure"),
        UNREGISTER_SUCCESS("UnregistrationSuccess"),
        UNREGISTER_FAILURE("UnregistrationFailure");

        private String _stringValue;
        public String stringValue(){
            return _stringValue;
        }
        MetricType(String val){
            _stringValue = val;
        }
    }

    @Expose
    private String registrationType;        // device registration type
    @Expose
    private String result;                  // result of the operation
    @Expose
    private String error;                   // error information if an error occurred
    @Expose
    private String dsn;                     // DSN of the device being registered or unregistered
    @Expose
    private long requestTotalTime;          // total time taken for this request


    /**
     * Constructor
     * @param logLevel Severity level of the log.
     * @param metricType One of the types defined in {@link MetricType}.
     * @param methodName Name of the method from which this metric was generated.
     * @param registrationType Device registration type.
     * @param dsn DSN of the device.
     * @param result Result of the operation.
     * {@link Result}
     * @param error Error information if register or unregister failed.
     */
    public AylaRegistrationMetric(LogLevel logLevel, MetricType metricType,
                                  String methodName, String registrationType, String dsn,
                                  Result result, String error) {
        super(logLevel, metricType.stringValue(), methodName);
        this.registrationType = registrationType;
        this.dsn = dsn;
        this.result = result.stringValue();
        this.error = error;
    }

    /**
     * Set total time taken for this request if the request was successful.
     * @param requestTotalTime Total time taken for the request in milliseconds.
     */
    public void setRequestTotalTime(long requestTotalTime) {
        this.requestTotalTime = requestTotalTime;
    }
}
