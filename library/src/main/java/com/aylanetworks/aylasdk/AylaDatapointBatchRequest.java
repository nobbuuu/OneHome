package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class used to represent a single update to a device's property as part of a batch request.
 *
 * See {@link AylaDeviceManager#createDatapointBatch} for details.
 */
public class AylaDatapointBatchRequest<T> {
    private static final String LOG_TAG = "AylaDatapointBatchRequest";

    private T _datapointValue;
    private AylaProperty<T> _property;

    /**
     * Constructor for AylaDatapointBatchRequest. We validate the datapoint value, property name,
     * property base type and DSN values
     *
     * @param datapointValue Value of DataPoint
     * @param property  AylaProperty object
     */
    public AylaDatapointBatchRequest(final T datapointValue, final AylaProperty<T> property) {
        if (datapointValue== null) {
            throw new AssertionError("Datapoint value cannot be null");
        }
        if (property == null || property.getName() == null) {
            throw new AssertionError("Property name cannot be null");
        }
        if (property.getBaseType() == null) {
            throw new AssertionError("Property base type cannot be null");
        }
        if (property.getOwner() == null || property.getOwner().getDsn() == null) {
            throw new AssertionError("Owner Device DSN cannot be null");
        }

        _datapointValue = datapointValue;
        _property = property;
    }

    public JSONObject toJSONObject() throws JSONException{
        JSONObject bodyObj = new JSONObject();
        try {
            JSONObject datapointBody = new JSONObject();
            datapointBody.put("value", _datapointValue);

            bodyObj.put("datapoint", datapointBody);
            bodyObj.put("dsn", _property.getOwner().getDsn());
            bodyObj.put("name", _property.getName());
            return bodyObj;
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "JSON Exception for AylaDatapointBatchRequest : " + e.toString());
            throw e;
        }
    }
}
