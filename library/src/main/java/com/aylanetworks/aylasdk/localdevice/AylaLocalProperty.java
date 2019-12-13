package com.aylanetworks.aylasdk.localdevice;

/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */


import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaDatapointBlob;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.PropertyChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;

import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * The AylaLocalProperty class is responsible for the creation of datapoints on the LocalDevice
 * as well as obtaining the current values from the device.
 *
 * @param <T> the base type, inherited from AylaProperty.
 */
@SuppressWarnings("WeakerAccess")
public class AylaLocalProperty<T> extends AylaProperty <T> {
    private static final String LOG_TAG = "LocalProperty";
    private AylaProperty<T> _originalProperty;

    public AylaLocalProperty (AylaProperty<T> other, AylaDevice owningDevice) {
        _originalProperty = other;

        // Copy some values from the original property
        this.name = _originalProperty.getName();
        this.key = _originalProperty.getKey();
        this.readOnly = _originalProperty.isReadOnly();
        this.baseType = _originalProperty.getBaseType();
        this.direction = _originalProperty.getDirection();
        this.displayName = _originalProperty.getDisplayName();
        this.type = _originalProperty.getType();

        this._owningDevice = new WeakReference<>(owningDevice);
        this.updateFrom(other, AylaDevice.DataSource.CLOUD);
    }

    public AylaProperty<T> getOriginalProperty() {
        return _originalProperty;
    }

    /**
     * Creates a datapoint on the cloud for this property using the current value. This is used
     * by the LocalDevice SDK to reflect changes on the device obtained via a local connection on
     * the cloud service.
     */
    @SuppressWarnings("unchecked")
    public void pushUpdateToCloud() {
        AylaProperty prop = getOriginalProperty();

        prop.createDatapointCloud(getValue(), null, new Response.Listener<AylaDatapoint>() {
            @Override
            public void onResponse(AylaDatapoint response) {
                AylaLog.d(LOG_TAG, "Local datapoint created on cloud");
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "Error pushing update to cloud");
            }
        });
    }

    protected AylaLocalDevice getLocalDevice() {
        if (_owningDevice == null) {
            return null;
        }

        try {
            return (AylaLocalDevice)_owningDevice.get();
        } catch (ClassCastException e) {
            AylaLog.e(LOG_TAG, "Owning device is not a local device");
            return null;
        }
    }

    @Override
    public T getValue() {
        AylaLocalDevice owner = (AylaLocalDevice)_owningDevice.get();
        return owner.getValueForProperty(this);
    }

    @Override
    public boolean isReadOnly() {
        AylaLocalDevice owner = (AylaLocalDevice)_owningDevice.get();
        return owner.isPropertyReadOnly(this);
    }

    @Override
    public AylaAPIRequest createDatapoint(final T value,
                                          final Map<String, String> metadata,
                                          final Response.Listener<AylaDatapoint<T>> successListener,
                                          final ErrorListener errorListener) {
        AylaLocalDevice owner = getLocalDevice();
        if (owner == null) {
            errorListener.onErrorResponse(new PreconditionError("Property must be owned by a " +
                    "local device"));
            return null;
        }

        @SuppressWarnings("unchecked") final
        AylaAPIRequest returnedRequest = new AylaAPIRequest(Request.Method.GET, null, null,
                AylaDatapoint.class, getSessionManager(), successListener, errorListener);

        AylaAPIRequest request = owner.setValueForProperty(this, value, new Response.Listener<T>() {
            @Override
            public void onResponse(T response) {
                AylaDatapoint<T> dp = new AylaDatapoint<>(value);
                updateFrom(dp, AylaDevice.DataSource.LOCAL);
                successListener.onResponse(dp);
                pushUpdateToCloud();
            }
        }, errorListener);

        returnedRequest.setChainedRequest(request);
        return returnedRequest;
    }

    @Override
    public PropertyChange updateFrom(AylaProperty otherProperty, AylaDevice.DataSource dataSource) {
        return _originalProperty.updateFrom(otherProperty, dataSource);
    }

    @Override
    public PropertyChange updateFrom(AylaDatapoint dp, AylaDevice.DataSource dataSource) {
        return _originalProperty.updateFrom(dp, dataSource);
    }

    @Override
    public AylaAPIRequest createDatapoint(T value, Map<String, String> metadata, int ackEnabledTimeout, Response.Listener<AylaDatapoint<T>> successListener, ErrorListener errorListener) {
        return createDatapoint(value, metadata, successListener, errorListener);
    }

    @Override
    public AylaAPIRequest createDatapointCloud(T value, Map<String, String> metadata, Response.Listener<AylaDatapoint<T>> successListener, ErrorListener errorListener) {
        return createDatapoint(value, metadata, successListener, errorListener);
    }

    @Override
    public AylaAPIRequest createDatapointCloud(T value, Map<String, String> metadata, int ackEnabledTimeout, Response.Listener<AylaDatapoint<T>> successListener, ErrorListener errorListener) {
        return createDatapoint(value, metadata, successListener, errorListener);
    }

    @Override
    public AylaAPIRequest createDatapointLAN(T value, Map<String, String> metadata, Response.Listener<AylaDatapoint<T>> successListener, ErrorListener errorListener) {
        return createDatapoint(value, metadata, successListener, errorListener);
    }

    @Override
    public AylaAPIRequest createDatapointLAN(T value, Map<String, String> metadata, int ackEnabledTimeout, Response.Listener<AylaDatapoint<T>> successListener, ErrorListener errorListener) {
        return createDatapoint(value, metadata, successListener, errorListener);
    }

    @Override
    public AylaAPIRequest createDataPointBlob(Response.Listener<AylaDatapointBlob> successListener, ErrorListener errorListener) {
        errorListener.onErrorResponse(new PreconditionError("Blob datapoints are currently not " +
                "suported for local devices"));
        return null;
    }


}
