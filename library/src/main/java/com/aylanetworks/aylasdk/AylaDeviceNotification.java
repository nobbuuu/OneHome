package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.google.gson.annotations.Expose;

import java.lang.ref.WeakReference;

/**
 * The AylaDeviceNotification class represents conditions that, when met, result in a set of
 * {@link AylaDeviceNotificationApp} objects being executed. Device notifications may be set on
 * each device and can be designed to fire when the device's online / offline state changes, or
 * IP address changes.
 * <p>
 * Device notifications may be created via calls to {@link AylaDevice#createNotification}.
 */
@Deprecated
public class AylaDeviceNotification {
    @Expose
    private String notificationType;
    @Expose
    private String deviceNickname;
    @Expose
    private Integer threshold;
    @Expose
    private String url;
    @Expose
    private String message;
    @Expose
    private Integer id;
    @Expose
    private Boolean active;

    public NotificationType getNotificationType() {
        return NotificationType.fromStringValue(notificationType);
    }

    public String getDeviceNickname() { return deviceNickname; }

    public Integer getThreshold() { return threshold; }

    public String getUrl() { return url; }

    public String getMessage() { return message; }

    Number getId() { return id; }

    public void setNotificationType(NotificationType notifType) {
        notificationType = notifType._stringValue;
    }

    public void setDeviceNickname(String deviceNickname) { this.deviceNickname = deviceNickname; }

    public void setThreshold(Integer threshold) { this.threshold = threshold; }

    public void setUrl(String url) { this.url = url; }

    public void setMessage(String message) { this.message = message; }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    /**
     * Weak reference to the AylaDevice
     */
    private WeakReference<AylaDevice> _device;

    public enum NotificationType {
        OnConnect("on_connect"),
        IPChange("ip_change"),
        ConnectionLost("on_connection_lost"),
        ConnectionRestore("on_connection_restore");

        NotificationType(String value) {
            _stringValue = value;
        }

        public String stringValue() {
            return _stringValue;
        }

        public static NotificationType fromStringValue(String value) {
            for (NotificationType val : values()) {
                if (val.stringValue().equals(value)) {
                    return val;
                }
            }
            return null;
        }

        private final String _stringValue;
    }

    /**
     * Creates an AylaDeviceNotificationApp on the cloud
     * @param notificationApp AylaDeviceNotificationApp
     * @param successListener Listener to receive on successful creation of NotificationApp
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Create DeviceNotification App
     */
    public AylaAPIRequest createApp(final AylaDeviceNotificationApp notificationApp,
                                    final Response.Listener<AylaDeviceNotificationApp>
                                            successListener,
                                    final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number notificationAppId = getId();
        if (notificationAppId == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Device notification App"
                    + "requires that a device notification was first fetched from the service"));
            return null;
        }

        String postBodyString = notificationApp.toJSON();
        String url = deviceManager.deviceServiceUrl("apiv1/notifications/" + notificationAppId +
                "/notification_apps.json");

        AylaAPIRequest request = new AylaJsonRequest<AylaDeviceNotificationApp.Wrapper>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaDeviceNotificationApp.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaDeviceNotificationApp.Wrapper>() {
                    @Override
                    public void onResponse(AylaDeviceNotificationApp.Wrapper response) {
                        successListener.onResponse(response.getNotificationApp());
                    }
                }, errorListener) {
        };

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Updates an existing AylaDeviceNotificationApp on the cloud
     * @param notificationApp AylaDeviceNotificationApp that needs to be updated
     * @param successListener Listener to receive on successful update of Notification
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to update Notification App
     */
    public AylaAPIRequest updateApp(final AylaDeviceNotificationApp notificationApp,
                                    final Response.Listener<AylaDeviceNotificationApp>
                                            successListener,
                                    final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number notificationId = notificationApp.getNotificationId();
        Number id = notificationApp.getId();
        if (notificationId == null || id == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Update of App requires that a" +
                    " device notification app was first fetched from the service"));
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/notifications/" + notificationId +
                "/notification_apps/" + id + ".json");
        String postBodyString = notificationApp.toJSON();

        AylaAPIRequest request = new AylaJsonRequest<AylaDeviceNotificationApp.Wrapper>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaDeviceNotificationApp.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaDeviceNotificationApp.Wrapper>() {
                    @Override
                    public void onResponse(AylaDeviceNotificationApp.Wrapper response) {
                        successListener.onResponse(response.getNotificationApp());
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches an array of AylaDeviceNotificationApps from the cloud
     * @param successListener Listener to receive on successful fetch of DeviceNotificationApps
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Fetch DeviceNotificationApps
     */
    public AylaAPIRequest fetchApps(final Response.Listener<AylaDeviceNotificationApp[]>
                                            successListener,
                                    final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number notificationAppId = getId();
        if (notificationAppId == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Device notification App"
                    + "requires that a device notification was first fetched from the service"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/notifications/" + notificationAppId +
                "/notification_apps.json");

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaDeviceNotificationApp.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaDeviceNotificationApp.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDeviceNotificationApp.Wrapper[] response) {
                        AylaDeviceNotificationApp[] notificationApps
                                = AylaDeviceNotificationApp.Wrapper.unwrap(response);
                        successListener.onResponse(notificationApps);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes an existing AylaDeviceNotification from the cloud
     * @param notificationApp AylaDeviceNotificationApp that needs to be deleted. This is the
     *                        existing NotificationApp that has been fetched from service
     * @param successListener Listener to receive on successful deletion of DeviceNotification App
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to delete DeviceNotificationApp
     */
    public AylaAPIRequest deleteApp(final AylaDeviceNotificationApp notificationApp,
                                    final Response.Listener<AylaAPIRequest.EmptyResponse>
                                            successListener,
                                    final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number notificationId = notificationApp.getNotificationId();
        Number id = notificationApp.getId();
        if (notificationId == null || id == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Delete requires that a" +
                    " device notification app was first fetched from the service"));
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/notifications/" + notificationId +
                "/notification_apps/" + id + ".json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<>(
                Request.Method.DELETE, url, null, AylaAPIRequest.EmptyResponse.class,
                sessionManager, successListener, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    private AylaDeviceManager getDeviceManager() {
        AylaDevice device = _device.get();
        if (device != null) {
            return device.getDeviceManager();
        }
        return null;
    }

    void setDevice(AylaDevice device) {
        _device = new WeakReference<>(device);
    }

    AylaDevice getDevice() {
        if (_device == null) {
            return null;
        }
        return _device.get();
    }

    public static class Wrapper {
        @Expose
        public AylaDeviceNotification notification;

        public static AylaDeviceNotification[] unwrap(Wrapper[] wrappedNotifications) {
            int size = 0;
            if (wrappedNotifications != null) {
                size = wrappedNotifications.length;
            }

            AylaDeviceNotification[] notifications = new AylaDeviceNotification[size];
            for (int i = 0; i < size; i++) {
                notifications[i] = wrappedNotifications[i].notification;
            }
            return notifications;
        }
    }
}
