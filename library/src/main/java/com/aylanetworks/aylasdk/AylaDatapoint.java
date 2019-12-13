package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.FieldChange;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.lan.CreateDatapointCommand;
import com.aylanetworks.aylasdk.util.DateUtils;
import com.google.gson.annotations.Expose;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An AylaDatapoint represents an update of a value to an {@link AylaProperty}. Each {@link
 * AylaProperty} contains a set of AylaDatapoints which is essentially a history of values set on
 * the property.
 * <p>
 * Creating an AylaDatapoint for a property may be done via a call to {@link
 * AylaProperty#createDatapoint}. The latest datapoint's value for a given property will be set
 * as the property's value, and is obtainable via a call to {@link AylaProperty#getValue()}.
 * <p>
 * A history of datapoints may be fetched from the service by calling {@link
 * AylaProperty#fetchDatapoints}.
 */
public class AylaDatapoint<T> {
    @Expose
    protected String createdAt;
    @Expose
    protected String createdAtFromDevice;
    @Expose
    protected boolean echo;
    @Expose
    protected Map<String, String> metadata;
    @Expose
    protected String updatedAt;
    @Expose
    protected T value;

    // Default empty constructor for subclasses
    protected AylaDatapoint() {

    }

    public int getAckStatus() {
        return ackStatus;
    }

    public int getAckMessage() {
        return ackMessage;
    }

    public String getAckedAt() {
        return ackedAt;
    }

    void setAckedAt(String ack) {
        ackedAt = ack;
    }

    void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    String getId() {
        return id;
    }

    // gson will init these fields, ignore warnings from IDE.
    @Expose
    private String id;
    @Expose
    private int ackStatus;
    @Expose
    private int ackMessage;
    @Expose
    private String ackedAt;

    public Date getCreatedAt() {
        return DateUtils.fromJsonString(createdAt);
    }

    public Date getCreatedAtFromDevice() {
        return DateUtils.fromJsonString(createdAtFromDevice);
    }

    /**
     * Echo is an indication of the source, set by the device for ToDevice properties.
     * If set to 1, it indicates the datapoint value was created on device by the user hitting a button, a schedule firing, or the host app.
     * If set to 0, it indicates the datapoint was created on the cloud or mobile.
     * A typical use case is the user hits a button to increase the temperature on a T-Stat. The datapoint would be sent to the cloud with the echo flag set to 1.
     */
    public boolean isEcho() {
        return echo;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Date getUpdatedAt() {
        return DateUtils.fromJsonString(updatedAt);
    }

    public String getUpdatedAtString() {
        return updatedAt;
    }

    public T getValue() {
        return value;
    }

    public AylaDatapoint(CreateDatapointCommand<T> command) {
        value = command.getValue();
        // We don't get a timestamp- need to make one up
        createdAt = DateUtils.getISO8601DateFormat().format(new Date());
        updatedAt = createdAt;
        createdAtFromDevice = createdAt;
        metadata = command.getMetadata();
    }

    /**
     * Creates an AylaDatapoint with a specific value
     * @param value Value to assign to the datapoint
     */
    public AylaDatapoint(T value) {
        this.value = value;
        createdAt = DateUtils.getISO8601DateFormat().format(new Date());
        updatedAt = createdAt;
        createdAtFromDevice = createdAt;
    }

    public static class Wrapper<T> {
        @Expose
        public AylaDatapoint<T> datapoint;

        public static AylaDatapoint[] unwrap(Wrapper[] container) {
            AylaDatapoint[] datapoints = new AylaDatapoint[container.length];
            for ( int i = 0; i < container.length; i++ ) {
                datapoints[i] = container[i].datapoint;
            }
            return datapoints;
        }

        /**
         * Unwraps the array of datapoints into an array of AylaDatapointBlob objects. For use by
         * file properties only.
         *
         * @param container Container returned from the cloud service
         * @return an array of AylaDatapointBlob objects
         */
        public static AylaDatapoint[] unwrapBlob(Wrapper[] container) {
            AylaDatapointBlob[] datapoints = new AylaDatapointBlob[container.length];
            for ( int i = 0; i < container.length; i++ ) {
                datapoints[i] = new AylaDatapointBlob(container[i].datapoint);
            }
            return datapoints;
        }
    }

    public void setValue(T value) {
        this.value = value;
    }


    /**
     * Called internally by the SDK, this method updates a datapoint with values from another
     * datapoint and notifies the device's listeners of a change.
     *
     * @param other The datapoint used as the source of this update
     * @return an AylaChange object representing the changes to the datapoint, if any occurred
     */
    public Change updateFrom(AylaDatapoint other) {
        Set<String> changedFields = new HashSet<>();
        AylaDatapoint dp = (AylaDatapoint)other;
        if ( dp.value != null && (dp.value.equals(this.value))) {
            this.value = (T) dp.value;
            changedFields.add("value");
        }
        if ( dp.createdAtFromDevice != null && !TextUtils.equals(dp.createdAtFromDevice,
                this.createdAtFromDevice)) {
            this.createdAtFromDevice = dp.createdAtFromDevice;
            changedFields.add("createdAtFromDevice");
        }
        if ( dp.createdAt != null && !TextUtils.equals(dp.createdAt, this.createdAt)) {
            this.createdAt = dp.createdAt;
            changedFields.add("createdAt");
        }
        if ( dp.updatedAt != null && !TextUtils.equals(dp.updatedAt, this.updatedAt)) {
            this.updatedAt = dp.updatedAt;
            changedFields.add("updatedAt");
        }
        if ( dp.echo != this.echo ) {
            this.echo = dp.echo;
            changedFields.add("echo");
        }

        if ( changedFields.size() > 0 ) {
            return new FieldChange(changedFields);
        }

        return null;
    }
}
