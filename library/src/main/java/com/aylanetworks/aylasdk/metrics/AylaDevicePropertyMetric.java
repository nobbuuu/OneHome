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
 * Class for device property change related metrics. Subclassed from {@link AylaMetric}.
 */
public class AylaDevicePropertyMetric extends AylaMetric {

    /**
     * Enumeration for all metric types represented using this class.
     */
    public enum MetricType {
        PROPERTY_ACK("PropertyAck"),
        LAN_CREATE_DATAPOINT("LANCreateDatapoint");

        private String _stringValue;
        public String stringValue(){
            return _stringValue;
        }
        MetricType(String val){
            _stringValue = val;
        }
    }

    @Expose
    private String dsn;             // DSN of the device
    @Expose
    private String propertyName;    // Name of the property
    @Expose
    private String ackTimestamp;    // Ack timestamp
    @Expose
    private String ackStatus;       // Ack status
    @Expose
    private String ackMessage;      // Ack message
    @Expose
    private String result;          // result of the operation
    @Expose
    private String error;           // error information if an error occurred

    /**
     * Constructor
     * @param logLevel Severity level of the log.
     * @param metricType One of the types defined in {@link MetricType}.
     * @param methodName Name of the method from which this metric was generated.
     * @param dsn DSN of the device.
     * @param propertyName Name of the property.
     * @param result Result of the operation.
     * {@link com.aylanetworks.aylasdk.metrics.AylaMetric.Result}
     * @param error Error information if an error occurred.
     */
    public AylaDevicePropertyMetric(LogLevel logLevel, MetricType metricType,
                                    String methodName, String dsn, String propertyName,
                                    Result result, String error) {
        super(logLevel, metricType.stringValue(), methodName);
        this.dsn = dsn;
        this.propertyName = propertyName;
        this.result = result.stringValue();
        this.error= error;
    }

    /**
     * Set Ack message that indicates success or failure of datapoint creation on the device.
     * @param ackMessage Ack message for the property datapoint.
     */
    public void setAckMessage(String ackMessage) {
        this.ackMessage = ackMessage;
    }

    /**
     * Set Ack status
     * @param ackStatus Ack status for the property datapoint.
     */
    public void setAckStatus(String ackStatus) {
        this.ackStatus = ackStatus;
    }

    /**
     * Set acked_at timestamp.
     * @param ackTimestamp Timestamp at which cloud receives ack information from the device.
     */
    public void setAckTimestamp(String ackTimestamp) {
        this.ackTimestamp = ackTimestamp;
    }
}
