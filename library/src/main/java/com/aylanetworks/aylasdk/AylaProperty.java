package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.aylanetworks.aylasdk.change.PropertyChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.error.PropertyAckTimeoutError;
import com.aylanetworks.aylasdk.error.ServerError;
import com.aylanetworks.aylasdk.error.TimeoutError;
import com.aylanetworks.aylasdk.lan.AylaLanModule;
import com.aylanetworks.aylasdk.lan.AylaLanRequest;
import com.aylanetworks.aylasdk.lan.CreateDatapointCommand;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaDevicePropertyMetric;
import com.aylanetworks.aylasdk.util.DateUtils;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.aylanetworks.aylasdk.util.URLHelper;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.iki.elonen.NanoHTTPD;

/**
 * An AylaProperty represents a "property" of an {@link AylaDevice}. Properties are used to
 * control devices as well as obtain information about their current state. Properties are
 * defined on the AylaService as part of the Template, and may not be created or destroyed.
 * Aylaproperty is represented by a generic class, whose type is determined by the base_type of
 * the property.
 *
 * <p>
 * Properties contain sets of {@link AylaDatapoint}s, the latest of which is considered the
 * property's "value". Therefore, changing the "value" of a property is done by creating an
 * {@link AylaDatapoint} for the property.
 * <p>
 * AylaProperties are obtained from {@link AylaDevice} objects by calling {@link
 * AylaDevice#getProperties()} (to obtain the entire list of known properties), or {@link
 * AylaDevice#getProperty(String)} to obtain a specific property by name.
 * <p>
 * Properties may have {@link AylaPropertyTrigger}s installed on them, which can be used to
 * perform actions when a datapoint is created on the property that matches criteria defined by
 * the {@link AylaPropertyTrigger}.
 * <p>
 * The most common methods for AylaProperty include:
 * <ul>
 * <li>{@link #createDatapoint(T, Map, Response.Listener, ErrorListener)} to change the
 * value of the property </li>
 * <li>{@link #getValue()} to get the value of the latest datapoint for the property</li>
 * <li>{@link #fetchDatapoints(int, Date, Date, Response.Listener, ErrorListener)} to fetch a set of
 * datapoints from the cloud service for this property</li>
 * </ul>
 */
public class AylaProperty<T> {
    private static final String LOG_TAG = "AylaProperty";


    /**
     * Maximum number of datapoints we will fetch in a single API call
     */
    public static final int MAX_DATAPOINT_COUNT = 100;

    public static final int DEFAULT_ACK_WAIT_TIME = 10;     // 10 seconds to wait for an ack

    @Expose
    protected boolean readOnly;
    @Expose
    protected Map<String, String> metadata;
    @Expose
    protected Number key;
    @Expose
    protected String baseType;
    @Expose
    protected T value;
    @Expose
    protected String dataUpdatedAt;           // Timestamp of when the property object was updated
    @Expose
    protected String name;
    @Expose
    protected String displayName;
    @Expose
    protected String direction;
    @Expose
    protected String type;
    @Expose
    protected boolean ackEnabled;
    @Expose
    protected int ackStatus;
    @Expose
    protected int ackMessage;
    @Expose
    protected String ackedAt;

    private AylaDevice.DataSource _lastUpdateSource = AylaDevice.DataSource.CLOUD;

    public static final String BASE_TYPE_FILE = "file";

    public String getBaseType() {
        return baseType;
    }

    public T getValue() {
        return value;
    }

    public Date getDataUpdatedAt() {
        return DateUtils.fromJsonString(dataUpdatedAt);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public Map<String, String> getMetadata() {
        if (metadata == null) {
            return null;
        }

        return new HashMap<>(metadata);
    }

    public String getType() {
        return type;
    }

    public boolean isAckEnabled() {
        return ackEnabled;
    }

    public int getAckStatus() {
        return ackStatus;
    }

    public int getAckMessage() {
        return ackMessage;
    }

    /**
     * Returns the time the latest acknowledgement was received from the device. Please note that
     * this value may differ slightly based on whether the value was obtained directly from the
     * device (LAN) or from either the Cloud Service (CLOUD) or datastream service (DSS). The source
     * of the update may be found by calling {@link #getLastUpdateSource()}.
     *
     * @return The timestamp of the acknowledgement of the datapoint from the device
     */
    public Date getAckedAt() {
        return DateUtils.fromJsonString(ackedAt);
    }

    /**
     * @hide
     */
    public Number getKey() {
        return key;
    }

    /**
     * Returns the DataSource representing the service used to last update this property's status.
     *
     * @return the DataSource last used to update this device's status. May be one of:
     * <ul>
     *     <li>{@link AylaDevice.DataSource#LAN} last update came from device via LAN</li>
     *     <li>{@link AylaDevice.DataSource#DSS} last update came from Device Stream Service</li>
     *     <li>{@link AylaDevice.DataSource#CLOUD} last update came from Ayla Cloud Service</li>
     * </ul>
     */
    public AylaDevice.DataSource getLastUpdateSource() {
        return _lastUpdateSource;
    }

    /**
     * Sets the Datasource type used to update this property's status.
     * @param lastUpdateSource the DataSource type used to update the property
     */
    public void setLastUpdateSource(AylaDevice.DataSource lastUpdateSource) {
        this._lastUpdateSource = lastUpdateSource;
    }

    /**
     * Weak reference to the AylaDevice that owns this property. This is set by AylaDevice when
     * the property is created for the first time.
     */
    protected WeakReference<AylaDevice> _owningDevice;

    /**
     * Creates a datapoint for this property with the supplied data using either the cloud
     * service or the local network. If the device has an active LAN session, the request will be
     * sent via the LAN. Otherwise it will be sent via the cloud.
     *
     * @param value           Value for the new datapoint
     * @param metadata        Metadata for the new datapoint
     * @param successListener Listener to receive the newly created datapoint on success
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatapoint(
            T value,
            Map<String, String> metadata,
            final Response.Listener<AylaDatapoint<T>> successListener,
            ErrorListener errorListener) {
        return createDatapoint(value, metadata, DEFAULT_ACK_WAIT_TIME, successListener, errorListener);
    }

    /**
     * Creates a datapoint for this property with the supplied data using either the cloud
     * service or the local network. If the device has an active LAN session, the request will be
     * sent via the LAN. Otherwise it will be sent via the cloud.
     *
     * @param value             Value for the new datapoint
     * @param metadata          Metadata for the new datapoint
     * @param ackEnabledTimeout Timeout in seconds to wait for an ack (ack-enabled properties only)
     * @param successListener   Listener to receive the newly created datapoint on success
     * @param errorListener     Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatapoint(
            T value,
            Map<String, String> metadata,
            final int ackEnabledTimeout,
            final Response.Listener<AylaDatapoint<T>> successListener,
            ErrorListener errorListener) {
        AylaDevice device = getOwner();
        if (device == null) {
            errorListener.onErrorResponse(new InternalError("No owner for this property"));
            return null;
        }

        // If we can do this over the LAN, let's.
        if (device.isLanModeActive() &&!(BASE_TYPE_FILE.equals(baseType))) {
            return createDatapointLAN(value, metadata, ackEnabledTimeout, successListener,
                    errorListener);
        } else {
            return createDatapointCloud(value, metadata, ackEnabledTimeout, successListener,
                    errorListener);
        }
    }

    /**
     * Creates a datapoint for this property with the supplied data using the cloud service.
     *
     * @param value             Value for the new datapoint
     * @param metadata          Metadata for the new datapoint
     * @param successListener   Listener to receive the newly created datapoint on success
     * @param errorListener     Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatapointCloud(
            final T value,
            Map<String, String> metadata,
            final Response.Listener<AylaDatapoint<T>> successListener,
            final ErrorListener errorListener) {
        return createDatapointCloud(value, metadata, DEFAULT_ACK_WAIT_TIME,
                successListener, errorListener);
    }

    /**
     * Creates a datapoint for this property with the supplied data using the cloud service.
     *
     * @param value             Value for the new datapoint
     * @param metadata          Metadata for the new datapoint
     * @param ackEnabledTimeout Timeout in seconds to wait for an ack (ack-enabled properties only)
     * @param successListener   Listener to receive the newly created datapoint on success
     * @param errorListener     Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatapointCloud(
            final T value,
            Map<String, String> metadata,
            final int ackEnabledTimeout,
            final Response.Listener<AylaDatapoint<T>> successListener,
            final ErrorListener errorListener) {

        // File-based properties create blob datapoints
        if (BASE_TYPE_FILE.equals(baseType)) {
            return createDataPointBlob(new Response.Listener<AylaDatapointBlob>() {
                @Override
                public void onResponse(AylaDatapointBlob response) {
                    successListener.onResponse((AylaDatapoint<T>)response);
                }
            }, errorListener);
        }

        AylaDevice device = getOwner();
        if (device == null) {
            errorListener.onErrorResponse(new InternalError("No owner for this property"));
            return null;
        }

        AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/properties/" + getKey() + "/datapoints.json");

        // Set up the body JSON
        final JSONObject bodyObject = new JSONObject();
        JSONObject valueObject = new JSONObject();
        try {
            valueObject.put("value", value);
            if (metadata != null) {
                // Create a JSON string out of the metadata and submit that
                valueObject.put("metadata", new JSONObject(metadata));
            }
            bodyObject.put("datapoint", valueObject);
        } catch (JSONException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "message", e));
            return null;
        }

        AylaLog.d(LOG_TAG, "CreateDatapoint POST body:\n" + bodyObject.toString());

        Map<String, String> headers = new HashMap<>();
        headers.put("x-ayla-source", "Mobile");

        final AylaAPIRequest<AylaDatapoint.Wrapper> request = new
                AylaJsonRequest<AylaDatapoint.Wrapper>(
                        Request.Method.POST,
                        url,
                        bodyObject.toString(),
                        headers,
                        AylaDatapoint.Wrapper.class,
                        getSessionManager(),
                        new EmptyListener<AylaDatapoint.Wrapper>(),
                        errorListener) {

                    @Override
                    protected Response<AylaDatapoint.Wrapper> parseNetworkResponse(NetworkResponse response) {
                        _networkResponseTimestamp = System.currentTimeMillis();
                        _networkResponse = response;


                        // Save our response headers
                        _responseHeaders = response.headers;

                        // Deserialize the JSON data into an object
                        String json;
                        try {
                            json = new String(
                                    response.data,
                                    HttpHeaderParser.parseCharset(response.headers));
                            if ( _logResponse ) {
                                String responseString = new String(response.data);
                                AylaLog.d(getLogTag(), "Request: " + this.toString() +
                                        " response code: " + response.statusCode +
                                        " response body: " + responseString);
                            }
                        } catch (UnsupportedEncodingException e) {
                            return Response.error(new ParseError(e));
                        }


                        Type type;
                        switch (baseType){
                            case "boolean":
                            case "integer":
                                type = new TypeToken<AylaDatapoint.Wrapper<Integer>>(){}.getType();
                                break;
                            case "decimal":
                                type = new TypeToken<AylaDatapoint.Wrapper<Float>>(){}.getType();
                                break;
                            default:
                                type = new TypeToken<AylaDatapoint.Wrapper<String>>(){}.getType();
                        }

                        AylaDatapoint.Wrapper wrapper = getGson().fromJson(json, type);

                        try {
                            return Response.success(
                                    wrapper,
                                    HttpHeaderParser.parseCacheHeaders(response));
                        } catch (JsonSyntaxException e) {
                            return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                                    json, e)));
                        } catch (JsonParseException e) {
                            return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                                    json, e)));
                        }
                    }

                    @Override
                    protected void deliverResponse(AylaDatapoint.Wrapper response) {
                        AylaDatapoint datapoint = response.datapoint;
                        if (isAckEnabled()) {
                            new AylaDatapointAckPoller(
                                    _owningDevice.get(),
                                    AylaProperty.this,
                                    datapoint.getId(),
                                    this,
                                    ackEnabledTimeout,
                                    successListener,
                                    errorListener).startPolling();
                        } else {
                            // Update ourselves using the datapoint
                            updateFrom(datapoint, AylaDevice.DataSource.CLOUD);
                            if (BASE_TYPE_FILE.equals(baseType)) {
                                // This is a blob datapoint
                                datapoint = new AylaDatapointBlob(datapoint);
                            }
                            successListener.onResponse(datapoint);
                        }
                    }
                };
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Creates a datapoint for this property with the supplied data via a LAN connection with the
     * device.
     *
     * @param value           Value for the new datapoint
     * @param metadata        Metadata for the new datapoint
     * @param successListener Listener to receive the newly created datapoint on success
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatapointLAN(
            T value,
            Map<String, String> metadata,
            final Response.Listener<AylaDatapoint<T>> successListener,
            final ErrorListener errorListener) {
        return createDatapointLAN(value, metadata, DEFAULT_ACK_WAIT_TIME,
                successListener, errorListener);
    }

    /**
     * Creates a datapoint for this property with the supplied data via a LAN connection with the
     * device. This version of this method allows for a timeout for ack-enabled properties. The
     * method will not return until an ack has been received for ack-enabled properties.
     *
     * @param value           Value for the new datapoint
     * @param metadata        Metadata for the new datapoint
     * @param ackEnabledTimeout Timeout in seconds to wait for an ack (ack-enabled properties only)
     * @param successListener Listener to receive the newly created datapoint on success
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatapointLAN(
            T value,
            Map<String, String> metadata,
            final int ackEnabledTimeout,
            final Response.Listener<AylaDatapoint<T>> successListener,
            final ErrorListener errorListener) {
        final AylaDevice device = _owningDevice.get();
        if (device == null) {
            errorListener.onErrorResponse(new InternalError("No owning device for this property!"));
            return null;
        }

        final AylaLanModule lanModule = device.getLanModule();
        if (lanModule == null || !lanModule.isActive()) {
            errorListener.onErrorResponse(new PreconditionError("Device is not in LAN mode"));
            return null;
        }

        // Make a LAN command packet
        final CreateDatapointCommand<T> cmd = device.getCreateDatapointCommand(this, value,
                metadata, ackEnabledTimeout);

        if (isAckEnabled()) {
            cmd.prepareForAck(ackEnabledTimeout, successListener, errorListener);
        }

        final AylaLanRequest request = new AylaLanRequest(device, cmd, getSessionManager(),
                new Response.Listener<AylaLanRequest.LanResponse>() {
                    @Override
                    public void onResponse(AylaLanRequest.LanResponse response) {
                        // If the response was successful, return a datapoint to the caller
                        AylaError error = cmd.getResponseError();
                        if (error != null) {
                            errorListener.onErrorResponse(error);
                            return;
                        }

                        // Create the datapoint, update ourselves and return to the caller
                        if (!ackEnabled || ackEnabledTimeout == 0) {
                            AylaDatapoint<T> dp = new AylaDatapoint<>(cmd);
                            updateFrom(dp, AylaDevice.DataSource.LAN);
                            successListener.onResponse(dp);
                            AylaDevicePropertyMetric devicePropertyMetric =
                                    new AylaDevicePropertyMetric(AylaMetric.LogLevel.INFO,
                                            AylaDevicePropertyMetric.MetricType.LAN_CREATE_DATAPOINT,
                                            "createDatapointLAN", device.getDsn(),
                                            getName(), AylaMetric.Result.SUCCESS, null);
                            sendToMetricsManager(devicePropertyMetric);

                        } else {
                            // For ack-enabled datapoints, we need to wait for the ack, or the
                            // specified ack timeout. Otherwise we return success here.
                            if (lanModule == null) {
                                errorListener.onErrorResponse(new PreconditionError("Device is " +
                                        "not in LAN mode"));
                            }
                        }
                    }
                }, errorListener);

        lanModule.sendRequest(request);
        return request;
    }

    /**
     * Creates a datapoint for the file properties. This method just creates a placeholder
     * AylaBlob object. The blob data will need to be uploaded in a separate method
     * uploadBlob on the AylaBlob object.
     *
     * @param successListener Listener to receive the newly created file datapoint on success
     * @param errorListener   Listener to receive error information
     * @return he AylaAPIRequest object queued to send for this request
     */
    protected AylaAPIRequest createDataPointBlob(final Response.Listener<AylaDatapointBlob>
                                                      successListener,
                                              final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/properties/" + getKey()
                + "/datapoints.json");
        JSONObject bodyObject = new JSONObject();
        JSONObject valueObject = new JSONObject();
        try {
            bodyObject.put("datapoint", valueObject);
        } catch (JSONException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create "
                        + "message", e));
            }
            return null;
        }

        AylaAPIRequest<AylaDatapointBlob.Wrapper> request = new
                AylaJsonRequest<>(
                Request.Method.POST,
                url,
                bodyObject.toString(),
                null,
                AylaDatapointBlob.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaDatapointBlob.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatapointBlob.Wrapper response) {
                        AylaDatapointBlob datapointBlob = response.datapoint;
                        datapointBlob.setProperty(AylaProperty.this);
                        successListener.onResponse(datapointBlob);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Class controlling the polling logic for creating Datapoint on ack_enabled properties in
     * Cloud Mode.
     */
    private static class AylaDatapointAckPoller<T> {

        private int _pollingMaxCount = 5;
        private static final int __pollingInterval = 1000; // ms

        private int _currentIteration;
        private AylaDevice _device;
        private AylaProperty<T> _property;
        private String _datapointID;
        private AylaAPIRequest _originalRequest;
        private Response.Listener _successListener;
        private ErrorListener _errorListener;

        AylaDatapointAckPoller(
                AylaDevice d
                , AylaProperty ap
                , final String adpID
                , final AylaAPIRequest oriRequest
                , int maxPolling
                , final Response.Listener successListener
                , final ErrorListener errorListener){
            _currentIteration = 0;
            _device = d;
            _property = ap;
            _datapointID = adpID;
            _originalRequest = oriRequest;
            _pollingMaxCount = maxPolling;

            _successListener = successListener;
            _errorListener = errorListener;
        }

        public AylaAPIRequest startPolling() {
            return fetchDatapointByID(_successListener, _errorListener);
        }

        /**
         * Fetches one datapoint specified by _datapointID, which is returned from cloud.
         *
         * @param successListener Listener to receive the specified datapoint on success
         * @param errorListener   Listener to receive error information
         * @return The AylaAPIRequest used to fetch the datapoints
         */
        private AylaAPIRequest fetchDatapointByID(
                final Response.Listener<AylaDatapoint> successListener
                , final ErrorListener errorListener) {
            if (_originalRequest == null ||
                    (_originalRequest!=null && _originalRequest.isCanceled())) {
                AylaLog.w(LOG_TAG, "AylaDatapointAckPoller.fetchDAtapointByID, _originalRequest " +
                        "null or is canceled.");
                return null;
            }

            // Form the request
            if (_device == null) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(new PreconditionError("No owning device"));
                }
                return null;
            }
            if (_property == null || TextUtils.isEmpty( _property.getName() )) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(
                            new InternalError("Property is null or property name is empty"));
                }
                return null;
            }
            if (TextUtils.isEmpty(_datapointID)) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(
                            new InternalError("Datapoint ID is empty or null"));
                }
                return null;
            }

            AylaDeviceManager adm = _property.getDeviceManager(errorListener);
            if (adm == null) {
                return null;
            }

            String url = adm.deviceServiceUrl(
                    "apiv1/devices/" + _device.getKey()
                            + "/properties/" + _property.getName()
                            + "/datapoints/" + _datapointID + ".json");
            AylaLog.d(LOG_TAG, "getDatapointByID url:" + url);

            AylaAPIRequest<AylaDatapoint.Wrapper> request =
                    new AylaAPIRequest<AylaDatapoint.Wrapper>(
                            Request.Method.GET
                            , url
                            , null
                            , AylaDatapoint.Wrapper.class
                            , _property.getSessionManager()
                            , new Response.Listener<AylaDatapoint.Wrapper>() {
                        @Override
                        public void onResponse(AylaDatapoint.Wrapper response) {
                            AylaDatapoint datapoint = response.datapoint;
                            if (!TextUtils.isEmpty(datapoint.getAckedAt())) {
                                if (response.datapoint.getAckStatus() ==
                                        NanoHTTPD.Response.Status.OK.getRequestStatus()) {
                                    _property.updateFrom(datapoint, AylaDevice.DataSource.CLOUD);
                                    successListener.onResponse(datapoint);
                                    AylaDevicePropertyMetric devicePropertyMetric =
                                            new AylaDevicePropertyMetric(AylaMetric.LogLevel.INFO,
                                                    AylaDevicePropertyMetric.MetricType.PROPERTY_ACK,
                                                    "fetchDatapointByID", _device.getDsn(), _property.getName(),
                                                    AylaMetric.Result.SUCCESS, null);
                                    devicePropertyMetric.setAckMessage(String.valueOf(
                                            datapoint.getAckMessage()));
                                    devicePropertyMetric.setAckStatus(String.valueOf(
                                            datapoint.getAckStatus()));
                                    devicePropertyMetric.setAckTimestamp(datapoint.getAckedAt());
                                    sendToMetricsManager(devicePropertyMetric);
                                } else {
                                    // We got the ack, but failed.
                                    errorListener.onErrorResponse(new ServerError(response.datapoint
                                            .getAckStatus(), null, "Datapoint NAK", null));
                                }

                                return;
                            }

                            // We're still waiting for the ACK.
                            _currentIteration++;
                            if (_currentIteration > _pollingMaxCount) {
                                AylaLog.w(LOG_TAG, "Ack enable property time out.");
                                if (errorListener != null) {
                                    errorListener.onErrorResponse(new PropertyAckTimeoutError(_property.getName(),
                                            "Ack enabled property " + _property.getName() + " polling timed out"));
                                }
                                AylaDevicePropertyMetric devicePropertyMetric =
                                        new AylaDevicePropertyMetric(AylaMetric.LogLevel.INFO,
                                        AylaDevicePropertyMetric.MetricType.PROPERTY_ACK,
                                        "fetchDatapointByID", _device.getDsn(), _property.getName(),
                                        AylaMetric.Result.FAILURE, "Ack enabled property " + _property.getName() + " timed out");
                                sendToMetricsManager(devicePropertyMetric);
                                return;
                            }

                            new Handler(Looper.getMainLooper())
                                    .postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            fetchDatapointByID(successListener, errorListener);
                                        }
                                    }, __pollingInterval);
                        }// end of onResponse
                    }// end of Response.Listener
                            , errorListener
                    ){
                        @Override
                        protected Response<AylaDatapoint.Wrapper> parseNetworkResponse(NetworkResponse response) {
                            _networkResponseTimestamp = System.currentTimeMillis();
                            _networkResponse = response;

                            // Save our response headers
                            _responseHeaders = response.headers;

                            // Deserialize the JSON data into an object
                            String json;
                            try {
                                json = new String(
                                        response.data,
                                        HttpHeaderParser.parseCharset(response.headers));
                                if ( _logResponse ) {
                                    String responseString = new String(response.data);
                                    AylaLog.d(getLogTag(), "Request: " + this.toString() +
                                            " response code: " + response.statusCode +
                                            " response body: " + responseString);
                                }
                            } catch (UnsupportedEncodingException e) {
                                return Response.error(new ParseError(e));
                            }


                            Type type;
                            switch (_property.baseType){
                                case "boolean":
                                case "integer":
                                    type = new TypeToken<AylaDatapoint.Wrapper<Integer>>(){}.getType();
                                    break;
                                case "decimal":
                                    type = new TypeToken<AylaDatapoint.Wrapper<Float>>(){}.getType();
                                    break;
                                default:
                                    type = new TypeToken<AylaDatapoint.Wrapper<String>>(){}.getType();
                            }

                            AylaDatapoint.Wrapper wrapper = getGson().fromJson(json, type);

                            try {
                                return Response.success(
                                        wrapper,
                                        HttpHeaderParser.parseCacheHeaders(response));
                            } catch (JsonSyntaxException e) {
                                return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                                        json, e)));
                            } catch (JsonParseException e) {
                                return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                                        json, e)));
                            }
                        }
                    };
            if (_originalRequest != null) {
                _originalRequest.setChainedRequest(request);
            }
            adm.sendDeviceServiceRequest(request);
            return request;
        }// end of fetchDatapointByID

    }// end of AylaDatapointAckPoller class



    /**
     * Fetches a collection of datapoints for this property from the server.
     *
     * @param count           Number of datapoints to fetch. If zero, will fetch the maximum number
     *                        of datapoints allowed in a single API call (MAX_DATAPOINT_COUNT).
     * @param from            Date of earliest datapoint to fetch. May be null.
     * @param to              Date of the latest datapoint to fetch. May be null.
     * @param successListener Listener to receive the array of datapoints
     * @param errorListener   Listener to receive errors that may occur
     * @return the AylaAPIRequest used to fetch the datapoints
     */
    public AylaAPIRequest fetchDatapoints(int count, Date from, Date to,
                                          final Response.Listener<AylaDatapoint[]> successListener,
                                          ErrorListener errorListener) {
        // Form the request
        AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/properties/" + getKey() + "/datapoints.json");

        if (count <= 0 || count > MAX_DATAPOINT_COUNT) {
            count = MAX_DATAPOINT_COUNT;
        }

        // Add the URL parameters
        Map<String, String> params = new HashMap<>();
        params.put("limit", Integer.toString(count));

        String fromString = null;
        if (from != null) {
            fromString = DateUtils.getISO8601DateFormat().format(from);
        }
        String toString = null;
        if (to != null) {
            toString = DateUtils.getISO8601DateFormat().format(to);
        }

        if (fromString != null) {
            params.put("filter[created_at_since_date]", fromString);
        }

        if (toString != null) {
            params.put("filter[created_at_end_date]", toString);
        }

        url = URLHelper.appendParameters(url, params);

        AylaAPIRequest request = new AylaAPIRequest<AylaDatapoint.Wrapper[]>(
                Request.Method.GET,
                url,
                null,
                AylaDatapoint.Wrapper[].class,
                getSessionManager(),
                new Response.Listener<AylaDatapoint.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDatapoint.Wrapper[] response) {
                        // Unwrap the container
                        AylaDatapoint[] unwrappedDatapoints;
                        if (BASE_TYPE_FILE.equals(baseType)) {
                            // File datapoints are all AylaDatapointBlob objects. Return those here.
                            unwrappedDatapoints = AylaDatapoint.Wrapper.unwrapBlob(response);
                        } else {
                            unwrappedDatapoints = AylaDatapoint.Wrapper.unwrap(response);
                        }
                        successListener.onResponse(unwrappedDatapoints);
                    }
                },
                errorListener){

            @Override
            protected Response parseNetworkResponse(NetworkResponse response) {
                _networkResponseTimestamp = System.currentTimeMillis();
                _networkResponse = response;

                // Save our response headers
                _responseHeaders = response.headers;

                // Deserialize the JSON data into an object
                String json;
                try {
                    json = new String(
                            response.data,
                            HttpHeaderParser.parseCharset(response.headers));
                    if ( _logResponse ) {
                        String responseString = new String(response.data);
                        AylaLog.d(getLogTag(), "Request: " + this.toString() +
                                " response code: " + response.statusCode +
                                " response body: " + responseString);
                    }
                } catch (UnsupportedEncodingException e) {
                    return Response.error(new ParseError(e));
                }


                Type type;
                switch (baseType){
                    case "boolean":
                    case "integer":
                        type = new TypeToken<AylaDatapoint.Wrapper<Integer>[]>(){}.getType();
                        break;
                    case "decimal":
                        type = new TypeToken<AylaDatapoint.Wrapper<Float>[]>(){}.getType();
                        break;
                    default:
                        type = new TypeToken<AylaDatapoint.Wrapper<String>[]>(){}.getType();
                }

                AylaDatapoint.Wrapper[] datapoints = getGson().fromJson(json, type);

                try {
                    return Response.success(
                            datapoints,
                            HttpHeaderParser.parseCacheHeaders(response));
                } catch (JsonSyntaxException e) {
                    return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                            json, e)));
                } catch (JsonParseException e) {
                    return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                            json, e)));
                }
            }
        };

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Updates the property's value and notifies any listeners of our owning device of the change.
     * <p>
     * This method should only be called internally.
     *
     * @param value New value to set
     * @param metadata New metadata
     * @return the PropertyChange that resulted from the operation, or null if no changes were made
     */
    public PropertyChange updateFrom(T value, Map<String, String> metadata,
                                     AylaDevice.DataSource updateSource) {
        _lastUpdateSource = updateSource;
        Set<String> changedFields = new HashSet<>();

        if (updateSource == AylaDevice.DataSource.LAN) {
            // The devices do not set timestamps- we need to set these ourselves
            String nowTimeString = DateUtils.getISO8601DateFormat().format(new Date());

            if (isAckEnabled()) {
                ackedAt = nowTimeString;
                changedFields.add("ackedAt");
            }
            dataUpdatedAt = nowTimeString;
            changedFields.add("dataUpdatedAt");
        }

        if (!value.equals(this.value)) {
            this.value = value;
            changedFields.add("value");
        }

        if (!ObjectUtils.equals(this.getMetadata(), metadata)) {
            if (metadata == null) {
                this.metadata = null;
            } else {
                this.metadata = new HashMap<>(metadata);
            }
            changedFields.add("metadata");
        }

        // Notify
        AylaDevice device = _owningDevice.get();
        PropertyChange change = null;
        if (changedFields.size() > 0) {
            change = new PropertyChange(getName(), changedFields, value);
            if (device != null) {
                device.notifyDeviceChanged(change, updateSource);
            }
        }

        return change;
    }

    /**
     * Updates the property's value from the given datapoint and notifies any listeners of our
     * owning device of the change.
     *
     * @param dp Datapoint used to update this property's value
     * @return A PropertyChange describing the change, or null if no changes occurred
     */
    public PropertyChange updateFrom(AylaDatapoint<T> dp, AylaDevice.DataSource dataSource) {
        _lastUpdateSource = dataSource;

        Set<String> changedFields = new HashSet<>();
        if ((dp.getValue() != null && !dp.getValue().equals(value))) {
            changedFields.add("value");
            value = (T) dp.getValue();
        }
        
        if (!ObjectUtils.equals(dp.getMetadata(), metadata)) {
            changedFields.add("metadata");
            if ( dp.getMetadata() == null ) {
                metadata = null;
            } else {
                metadata = new HashMap<>(dp.getMetadata());
            }
        }

        if (dataSource == AylaDevice.DataSource.LAN) {
            // The devices do not set timestamps- we need to set these ourselves
            String nowTimeString = DateUtils.getISO8601DateFormat().format(new Date());

            if (isAckEnabled() && dp.getAckedAt() == null) {
                dp.setAckedAt(nowTimeString);
            }

            if (dp.getUpdatedAtString() == null) {
                dp.setUpdatedAt(nowTimeString);
            }
        }

        if (!ObjectUtils.equals(dp.getUpdatedAtString(), dataUpdatedAt)) {
            changedFields.add("dataUpdatedAt");
            dataUpdatedAt = dp.getUpdatedAtString();
        }

        // Assuming the incoming datapoint is always latest.
        if ( !TextUtils.equals(dp.getAckedAt(), this.ackedAt) && !TextUtils.isEmpty(dp.getAckedAt())) {
            changedFields.add("ackedAt");
            changedFields.add("ackMessage");
            changedFields.add("ackStatus");

            this.ackedAt = dp.getAckedAt();
            this.ackMessage = dp.getAckMessage();
            this.ackStatus = dp.getAckStatus();
        }

        PropertyChange<T> change = null;
        if ( changedFields.size() > 0 ) {
            change = new PropertyChange<>(getName(), changedFields, dp);
            AylaDevice owner = getOwner();
            if (owner != null ) {
                owner.notifyDeviceChanged(change, dataSource);
            }
        }

        //update cached properties
        if(AylaNetworks.sharedInstance().getSystemSettings().allowOfflineUse){
            AylaCache cache = getSessionManager().getCache();
            List<AylaProperty> propertyList = getOwner().getProperties();
            AylaProperty[] properties = propertyList.toArray(new AylaProperty[propertyList.size()]);
            cache.saveArray(AylaCache.CacheType.PROPERTY, getOwner().dsn, properties);
        }
        return change;
    }

    /**
     * Updates the property from another property and notifies any listeners of our owning device
     * of any changes that resulted.
     * <p>
     * This method should only be called internally.
     *
     * @param otherProperty Property to take our new values from
     * @return the PropertyChange that resulted from the operation, or null if no changes were made
     */

    public PropertyChange updateFrom(AylaProperty otherProperty, AylaDevice.DataSource dataSource) {
        _lastUpdateSource = dataSource;
        Set<String> changedFields = new HashSet<>();

        if (!ObjectUtils.equals(otherProperty.getKey(), this.getKey())) {
            key = otherProperty.getKey();
            changedFields.add("key");
        }
        if (!ObjectUtils.equals(otherProperty.getAckedAt(), this.getAckedAt())) {
            ackedAt = otherProperty.ackedAt;
            changedFields.add("ackedAt");
        }
        if (otherProperty.getAckMessage() != this.getAckMessage()) {
            ackMessage = otherProperty.getAckMessage();
            changedFields.add("ackMessage");
        }
        if (otherProperty.getAckStatus() != this.getAckStatus()) {
            ackStatus = otherProperty.getAckStatus();
            changedFields.add("ackStatus");
        }
        if (!ObjectUtils.equals(otherProperty.getDataUpdatedAt(), this.getDataUpdatedAt())) {
            dataUpdatedAt = otherProperty.dataUpdatedAt;
            changedFields.add("dataUpdatedAt");
        }
        if (!ObjectUtils.equals(otherProperty.getDisplayName(), this.getDisplayName())) {
            displayName = otherProperty.getDisplayName();
            changedFields.add("displayName");
        }
        if (!ObjectUtils.equals(otherProperty.getValue(), this.getValue())) {
            this.value = (T) otherProperty.getValue();
            changedFields.add("value");
        }
        if (!ObjectUtils.equals(otherProperty.getMetadata(), this.getMetadata())) {
            if (otherProperty.getMetadata() == null) {
                this.metadata = null;
            } else {
                this.metadata = new HashMap<>(otherProperty.getMetadata());
            }
            changedFields.add("metadata");
        }

        // Did we change anything?
        if (changedFields.size() > 0) {
            PropertyChange change = new PropertyChange(this.getName(), changedFields, value);
            getOwner().notifyDeviceChanged(change, dataSource);
        }

        // No changes.
        return null;
    }

    /**
     * Creates a Trigger for the Property
     * @param propertyTrigger A name for the trigger getting created
     * @param successListener Listener to receive on successful creation of Trigger
     * @param errorListener Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to create new Trigger
     */
    public AylaAPIRequest createTrigger(final AylaPropertyTrigger propertyTrigger,
                                        final Response.Listener<AylaPropertyTrigger>
                                                successListener,
                                        final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No session is active"));
            }
            return null;
        }

        String triggerType= propertyTrigger.getTriggerType();
        if (triggerType == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "Invalid Trigger Type"));
            }
            return null;
        }

        Number propertyKey = getKey().intValue();
        String url = deviceManager.deviceServiceUrl("apiv1/properties/" + propertyKey + "/triggers.json");

        AylaPropertyTrigger.Wrapper triggerWrapper = new AylaPropertyTrigger.Wrapper();
        triggerWrapper.trigger = propertyTrigger;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (triggerWrapper, AylaPropertyTrigger.Wrapper.class);

        AylaAPIRequest<AylaPropertyTrigger.Wrapper> request = new
                AylaJsonRequest<>(
                        Request.Method.POST,
                        url,
                        postBodyString,
                        null,
                        AylaPropertyTrigger.Wrapper.class,
                        sessionManager,
                        new Response.Listener<AylaPropertyTrigger.Wrapper>() {
                            @Override
                            public void onResponse(AylaPropertyTrigger.Wrapper response) {
                                response.trigger.setOwner(AylaProperty.this);
                                successListener.onResponse(response.trigger);
                            }
                        }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Updates a Trigger
     * @param propertyTrigger AylaPropertyTrigger that needs to be updated. This is the trigger
     *                        that is fetched from service or can be newly created trigger
     *                        create using createTrigger method
     * @param successListener Listener to receive on successful update of Trigger
     * @param errorListener Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to update Trigger
     */
    public AylaAPIRequest updateTrigger(final AylaPropertyTrigger propertyTrigger,
                                        final Response.Listener<AylaPropertyTrigger>
                                                successListener,
                                        final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No session is active"));
            }
            return null;
        }

        if (propertyTrigger.getKey() == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("This is an Invalid Property " +
                    "Trigger"));
            return null;
        }
        Number propertyTriggerKey = propertyTrigger.getKey().intValue();

        String url = deviceManager.deviceServiceUrl("apiv1/triggers/" + propertyTriggerKey + ".json");

        AylaPropertyTrigger.Wrapper triggerWrapper = new AylaPropertyTrigger.Wrapper();
        triggerWrapper.trigger = propertyTrigger;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (triggerWrapper, AylaPropertyTrigger.Wrapper.class);

        AylaAPIRequest<AylaPropertyTrigger.Wrapper> request = new
                AylaJsonRequest<>(
                        Request.Method.PUT,
                        url,
                        postBodyString,
                        null,
                        AylaPropertyTrigger.Wrapper.class,
                        sessionManager,
                        new Response.Listener<AylaPropertyTrigger.Wrapper>() {
                            @Override
                            public void onResponse(AylaPropertyTrigger.Wrapper response) {
                                response.trigger.setOwner(AylaProperty.this);
                                successListener.onResponse(response.trigger);
                            }
                        }, errorListener);


        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches an array of Triggers for this AylaProperty
     * @param successListener Listener to receive on successful fetch of Triggers
     * @param errorListener Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Fetch Triggers
     */
    public AylaAPIRequest fetchTriggers(final Response.Listener<AylaPropertyTrigger[]>
                                                successListener,
                                        final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No session is active"));
            }
            return null;
        }

        Number propertyKey = getKey().intValue();
        String url = deviceManager.deviceServiceUrl("apiv1/properties/" + propertyKey + "/triggers.json");

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaPropertyTrigger.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaPropertyTrigger.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaPropertyTrigger.Wrapper[] response) {
                        AylaPropertyTrigger[] propertyTriggers = AylaPropertyTrigger.Wrapper
                                .unwrap(response);

                        for(AylaPropertyTrigger trigger:propertyTriggers) {
                            trigger.setOwner(AylaProperty.this);
                        }
                        successListener.onResponse(propertyTriggers);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes a Trigger
     * @param propertyTrigger AylaPropertyTrigger that needs to be updated. This is the existing
     *                        Trigger that has been fetched from service
     * @param successListener Listener to receive on successful deletion of Triggers
     * @param errorListener Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to delete Trigger
     */
    public AylaAPIRequest deleteTrigger(final AylaPropertyTrigger propertyTrigger,
                                        final Response.Listener<EmptyResponse> successListener,
                                        final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager(errorListener);
        if (deviceManager == null) {
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No session is active"));
            }
            return null;
        }
        Number propertyTriggerKey = propertyTrigger.getKey();
        if(propertyTriggerKey == null){
            errorListener.onErrorResponse(new InvalidArgumentError("This is an Invalid Property " +
                    "Trigger"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/triggers/" + propertyTriggerKey + ".json");

        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, AylaAPIRequest.EmptyResponse.class,
                sessionManager, successListener, errorListener){
            @Override
            protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                return Response.success(new EmptyResponse(),
                        HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Convenience method to return the device manager responsible for this object
     *
     * @param errorListener Optional error listener to be called in case of failure
     * @return the AylaDeviceManager responsible for this object
     */
    public AylaDeviceManager getDeviceManager(ErrorListener errorListener) {
        AylaDevice d = _owningDevice.get();
        if (d == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No owning device"));
            }
            return null;
        }

        AylaDeviceManager dm = d.getDeviceManager();
        if (dm == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager"));
            }
        }

        return dm;
    }

    /**
     * Convenience method to return the session manager responsible for this object
     *
     * @return the AylaSessionManager responsible for this object
     */
    public AylaSessionManager getSessionManager() {
        AylaDevice d = _owningDevice.get();
        if (d == null) {
            return null;
        }
        return d.getSessionManager();
    }

    void setOwner(AylaDevice owner) {
        _owningDevice = new WeakReference<>(owner);
    }

    public AylaDevice getOwner() {
        if (_owningDevice == null) {
            return null;
        }
        return _owningDevice.get();
    }

    public static class Wrapper<T> {
        @Expose
        public AylaProperty<T> property;

        public static AylaProperty[] unwrap(Wrapper[] wrappedProperties) {
            int size = 0;
            if (wrappedProperties != null) {
                size = wrappedProperties.length;
            }

            AylaProperty[] properties = new AylaProperty[size];
            for (int i = 0; i < size; i++) {
                properties[i] = wrappedProperties[i].property;
            }
            return properties;
        }
    }

    private static void sendToMetricsManager(AylaMetric metric){
        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance()
                .getMetricsManager();
        if(metricsManager != null){
            metricsManager.addMessageToUploadsQueue(metric);
        }
    }

    @Override
    public String toString() {
        return "Property[" + getName() + "|" + getValue() + "|" + getDataUpdatedAt() + "|" + getLastUpdateSource() + "]";
    }
}
