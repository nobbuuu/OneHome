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
 * Class for app lifecycle metrics. This is a subclass of abstract class {@link AylaMetric}.
 */
public class AylaAppLifecycleMetric extends AylaMetric {

    /**
     * Enumeration for all metric types represented using this class.
     */
    public enum MetricType {
        APP_LAUNCH("AppLaunch"),
        APP_BACKGROUND("AppBackground"),
        APP_TERMINATED("AppTerminated"),
        APP_CRASHED("AppCrashed");

        private String _stringValue;
        public String stringValue(){
            return _stringValue;
        }
        MetricType(String val){
            _stringValue = val;
        }
    }

    @Expose
    private Integer crashCount;         // Total crash count recorded in this app

    /**
     * Constructor
     * @param logLevel Severity level of the log
     * @param metricType One of the types defined in {@link MetricType}
     * @param methodName Name of method from which this log is generated
     */
    public AylaAppLifecycleMetric(LogLevel logLevel, MetricType metricType,
                                  String methodName){
        super(logLevel, metricType._stringValue, methodName);
    }

    /**
     * Sets value of crash count in this metric.
     * @param crashCount Total crash count recorded so far in this app.
     */
    public void setCrashCount(int crashCount){
        this.crashCount = crashCount;
    }
}
