package com.aylanetworks.aylasdk.metrics;

/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.BuildConfig;
import com.aylanetworks.aylasdk.util.SystemInfoUtils;
import com.google.gson.annotations.Expose;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Abstract class for metrics message formatting. Subclass this class for each metric
 * type.
 */

public abstract class AylaMetric {

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm 'GMT'", Locale.US);
    @Expose
    private UUID logId;                 // unique id for each metric
    @Expose
    private String appVersion;          // app version
    @Expose
    private String sdkVersion;          // sdk version
    @Expose
    private String deviceType;          // phone type
    @Expose
    private String osVersion;           // Android OS version running on this phone
    @Expose
    private String networkType;         // network type from AylaConnectivity class. wifi or data
    @Expose
    private String metricType;          // type of this metric
    @Expose
    private String text;                // additional information about this metric
    @Expose
    private String methodName;          // name of method from which this metric was generated
    @Expose
    private long time;                  // current time in milliseconds
    @Expose
    private String level;               // log level
    @Expose
    private String senderId;            // unique sender id, uses package name of the app
    @Expose
    private AylaMetricsManager.LogType logType;             // log type. Metric for metrics, Log for logs
    @Expose
    private String generatedAt;         // date in format "yyyy-MM-dd HH:mm 'GMT'"
    @Expose
    private String appId;               //App id

    public enum Result{
        SUCCESS("Success"),
        FAILURE("Failure"),
        PARTIAL_SUCCESS("PartialSuccess");

        private String _stringValue;
        Result(String val){
            _stringValue = val;
        }

        public String stringValue(){
            return _stringValue;
        }
    }

    public enum LogLevel{
        INFO("Info"),
        WARNING("Warning"),
        ERROR("Error");

        private String _stringValue;
        LogLevel(String val){
            _stringValue = val;
        }

        public String stringValue(){
            return _stringValue;
        }
    }

    /**
     * Constructor
     * @param logLevel Log level.
     * @param metricType Type of this metric.
     * @param methodName Method from which this metric is generated.
     */
    public AylaMetric(LogLevel logLevel, String metricType, String methodName){
        this.level = logLevel.stringValue();
        logId = UUID.randomUUID();
        this.metricType = metricType;
        this.methodName = methodName;
        time = System.currentTimeMillis();
        appVersion = BuildConfig.VERSION_NAME;
        deviceType = SystemInfoUtils.getManufacturer() + " " + SystemInfoUtils.getModel();
        osVersion = SystemInfoUtils.getOSVersion();
        sdkVersion = BuildConfig.VERSION_NAME;
        senderId = AylaNetworks.sharedInstance().getContext().getApplicationContext()
                .getPackageName();
        this.logType = AylaMetricsManager.LogType.METRIC;
        text = "";
        generatedAt = getFormattedDateString(time);
        networkType = AylaNetworks.sharedInstance().getConnectivity().isWifiEnabled()? "Wifi":
                "Data";
        this.appId = AylaNetworks.sharedInstance().getSystemSettings().appId;
    }

    /**
     * Set additional data to be sent in text field of the metrics json. This field is
     * currently mandatory in Ayla Log service, and will be set to an empty string if no value is
     * set using this method.
     * @param text additional text to be added to the metric json.
     */
    public void setMetricText(String text){
        this.text = text;
    }

    private String getFormattedDateString(long time){
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return dateFormat.format(calendar.getTime());
    }
}
