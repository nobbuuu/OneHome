package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */
import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.google.gson.annotations.Expose;

import java.lang.ref.WeakReference;
/**
 * Property triggers are used to launch an action when a specified condition is met on the device.
 * This object is used by createTrigger,updateTrigger,fetchTriggers and deleteTrigger methods in
 * AylaProperty object
 */
@Deprecated
public class AylaPropertyTrigger {
    @Expose
    private String triggerType;
    @Expose
    private String compareType;
    @Expose
    private String value;
    @Expose
    private String propertyNickname;
    @Expose
    private String deviceNickname;
    @Expose
    private String retrievedAt;
    @Expose
    private Boolean active;
    @Expose
    private Number key;
    @Expose
    private String period;
    @Expose
    private String baseType;
    @Expose
    private String triggeredAt;

    public String getTriggerType() { return triggerType; }

    public String getCompareType() { return compareType; }

    public String getValue() { return value; }

    public String getPropertyNickname() { return propertyNickname; }

    public String getDeviceNickname() { return deviceNickname; }

    public String getRetrievedAt() { return retrievedAt; }

    public Boolean getActive() { return active; }

    Number getKey() { return key; }

    public String getPeriod() { return period; }

    public String getBaseType() { return baseType; }

    public String getTriggeredAt() { return triggeredAt; }

    public void setActive(Boolean active) { this.active = active; }

    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public void setCompareType(String compareType) { this.compareType = compareType; }

    public void setValue(String value) { this.value = value; }

    public void setPropertyNickname(String propertyNickname) {
        this.propertyNickname = propertyNickname;
    }

    public void setDeviceNickname(String nickname) {
        this.deviceNickname=nickname;
    }

    public void setBaseType(String baseType) {
        this.baseType = baseType;
    }

    public enum TriggerType {
        Change("on_change"),
        Absolute("compare_absolute"),
        Always("always");

        TriggerType(String value) {
            _stringValue = value;
        }

        public String stringValue() {
            return _stringValue;
        }

        private final String _stringValue;
    }

    /**
     * Weak reference to the AylaProperty that owns this Property Trigger.
     */
    private WeakReference<AylaProperty> _owningProperty;

    /**
     * Creates a TriggerApp for the Property Trigger
     *
     * @param triggerApp      AylaPropertyTriggerApp
     * @param successListener Listener to receive on successful creation of Trigger App
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Create Trigger App
     */
    public AylaAPIRequest createApp(final AylaPropertyTriggerApp triggerApp,
                                    final Response.Listener<AylaPropertyTriggerApp>
                                            successListener,
                                    final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager(errorListener);
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

        Number propertyTriggerKey = getKey();
        if (propertyTriggerKey == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("This is an Invalid " +
                    "Property Trigger"));
            return null;
        }

        String postBodyString = triggerApp.toJSON();
        String url = deviceManager.deviceServiceUrl("apiv1/triggers/" + propertyTriggerKey +
                "/trigger_apps.json");

        AylaAPIRequest request = new AylaJsonRequest<AylaPropertyTriggerApp.Wrapper>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaPropertyTriggerApp.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaPropertyTriggerApp.Wrapper>() {
                    @Override
                    public void onResponse(AylaPropertyTriggerApp.Wrapper response) {
                        AylaPropertyTriggerApp propertyTriggerApp = response.trigger_app;
                        if (propertyTriggerApp != null) {
                            propertyTriggerApp.populateFields();
                            successListener.onResponse(propertyTriggerApp);
                        }
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Updates a TriggerApp
     *
     * @param triggerApp      AylaPropertyTriggerApp that needs to be updated
     * @param successListener Listener to receive on successful update of Trigger App
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to update Trigger App
     */
    public AylaAPIRequest updateApp(final AylaPropertyTriggerApp triggerApp,
                                    final Response.Listener<AylaPropertyTriggerApp>
                                            successListener,
                                    final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager(errorListener);
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

        Number triggerAppKey = triggerApp.getKey();
        if (triggerAppKey == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("This is an Invalid " +
                    "Application Trigger"));
            return null;
        }

        String postBodyString = triggerApp.toJSON();
        String url = deviceManager.deviceServiceUrl("apiv1/trigger_apps/" + triggerAppKey + ".json");

        AylaAPIRequest request = new AylaJsonRequest<AylaPropertyTriggerApp.Wrapper>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaPropertyTriggerApp.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaPropertyTriggerApp.Wrapper>() {
                    @Override
                    public void onResponse(AylaPropertyTriggerApp.Wrapper response) {
                        AylaPropertyTriggerApp propertyTriggerApp = response.trigger_app;
                        if (propertyTriggerApp != null) {
                            propertyTriggerApp.populateFields();
                            successListener.onResponse(propertyTriggerApp);
                        }
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches an Array of TriggerApps for this Property Trigger
     *
     * @param successListener Listener to receive on successful fetch of TriggerApps
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Fetch TriggerApps
     */
    public AylaAPIRequest fetchApps(final Response.Listener<AylaPropertyTriggerApp[]>
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

        Number propertyTriggerKey = getKey();
        if (propertyTriggerKey == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("This is an Invalid " +
                    "Property Trigger"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/triggers/" + propertyTriggerKey +
                "/trigger_apps.json");

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaPropertyTriggerApp.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaPropertyTriggerApp.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaPropertyTriggerApp.Wrapper[] response) {
                        AylaPropertyTriggerApp[] applicationTriggers
                                = AylaPropertyTriggerApp.Wrapper.unwrap(response);
                        successListener.onResponse(applicationTriggers);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes a Trigger App
     *
     * @param triggerApp      AylaPropertyTriggerApp that needs to be deleted. This is the existing
     *                        TriggerApp that has been fetched from service
     * @param successListener Listener to receive on successful deletion of Trigger App
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to delete TriggerApp
     */
    public AylaAPIRequest deleteApp(final AylaPropertyTriggerApp triggerApp,
                                    final Response.Listener<EmptyResponse>
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

        Number triggerAppKey = triggerApp.getKey();
        if (triggerAppKey == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("This is an Invalid " +
                    "Application Trigger"));
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/trigger_apps/" + triggerAppKey + ".json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, EmptyResponse.class,
                sessionManager, successListener, errorListener) {
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
     * Convenience method to return the Device Manager responsible for this object
     *
     * @return the AylaDeviceManager responsible for this object
     */
    private AylaDeviceManager getDeviceManager(ErrorListener errorListener) {
        AylaProperty property = getOwner();
        if (property == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No owning property"));
            }
            return null;
        }
        return property.getDeviceManager(errorListener);
    }

    void setOwner(AylaProperty owner) {
        _owningProperty = new WeakReference<>(owner);
    }

    AylaProperty getOwner() {
        if (_owningProperty == null) {
            return null;
        }
        return _owningProperty.get();
    }

    public static class Wrapper {
        @Expose
        public AylaPropertyTrigger trigger;

        public static AylaPropertyTrigger[] unwrap(Wrapper[] wrappedTriggers) {
            int size = 0;
            if (wrappedTriggers != null) {
                size = wrappedTriggers.length;
            }

            AylaPropertyTrigger[] triggers = new AylaPropertyTrigger[size];
            for (int i = 0; i < size; i++) {
                triggers[i] = wrappedTriggers[i].trigger;
            }
            return triggers;
        }
    }
}
