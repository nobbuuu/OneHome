package com.aylanetworks.aylasdk;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */


import android.content.Context;

import static com.aylanetworks.aylasdk.AylaDSManager.AylaDSSubscriptionType.AylaDSSubscriptionTypeDatapoint;
import static com.aylanetworks.aylasdk.AylaDSManager.AylaDSSubscriptionType.AylaDSSubscriptionTypeDatapointAck;

/**
 * Settings and configuration class used to initialize the Ayla library. Application developers
 * must provide the SDK an instance of this object filled out with application-specific
 * information by calling {@link AylaNetworks#initialize}.
 */
public class AylaSystemSettings {
    /** Context used for notifications, component initialization, etc. This should be a
     * permanent context such as the Application context. If the context is an Activity,
     * be sure to call updateContext() if the context changes. */
    public Context context;

    /**
     * Application ID provided by Ayla Networks for your app. This must be set correctly in
     * order to connect to the cloud service.
     */
    public String appId;

    /**
     * Application secret provided by AylaNetworks for your app. This must be set correctly in
     * order to connect to the cloud service.
     */
    public String appSecret;

    /**
     * Cloud service provider. Currently support AWS, GCP and VPC.
     */
    public CloudProvider cloudProvider = CloudProvider.AWS;

    /**
     * Service to connect to. Generally during development, the Development service is used, while
     * in production, Field service is used.
     */
    public ServiceType serviceType = ServiceType.Development;

    /**
     * Location of the service to connect to. Set this to the region your application will be
     * accessed from.
     */
    public ServiceLocation serviceLocation = ServiceLocation.USA;

    /**
     * Interface that provides details about devices in the system.
     */
    public DeviceDetailProvider deviceDetailProvider;

    /**
     * To specify the regex of the preferred device SSID to be setup.
     */
    public String ssidRegex;

    /**
     * To enable or disable datastreaming feature in this app
     */
    public boolean allowDSS;

    /**
     * To enable or disable offline use of this app. Devices in LAN mode can be controlled in
     * offline mode.
     */
    public boolean allowOfflineUse;

    /**
     * To enable or disable metrics of this app, enabled by default.
     */
    public boolean disableMetricsService;

    /**
     * To enable or disable UncaughtExceptionHandler of this app, enabled by default.
     */
    public boolean disableUncaughtExceptionHandler;

    /**
     * The default timeout for network operations
     */
    public int defaultNetworkTimeoutMs = 5000;

    /**
     * Sender ID for push notifications. Android-specific.
     */
    public String pushNotificationSenderId;

    /**
     * If true, allows devices that are shared with the local account to be accessible in LAN mode.
     *
     * WARNING: Access control to the device via Roles is not supported in LAN mode. Shared
     * devices in LAN mode will respond to the LAN commands as if the account holder were the
     * owner of the device. Applications may read and write to devices in LAN mode even if the
     * device was shared with "read-only" access.
     */
    public boolean allowLANConnectionToSharedDevices = false;

    public String[] dssSubscriptionTypes = new String[]{
            AylaDSSubscriptionTypeDatapoint.stringValue(),
            AylaDSSubscriptionTypeDatapointAck.stringValue()};

    /** Default constructor **/
    public AylaSystemSettings() {
    }

    /**
     * The DeviceDetailProvider interface is used by the SDK to obtain information about devices
     * it is managing. Application developers may provide an object that implements this
     * interface to the SDK during initialization that will be called by the SDK to provide
     * device-specific information.
     */
    public interface DeviceDetailProvider {
        /**
         * @return an array of property names (Strings) that the DeviceManager should keep
         * up-to-date. If this method returns null for a given device, all properties will be
         * fetched for that device.
         *
         * @param device the {@link AylaDevice} that is requesting the names of managed properties
         */
        String[] getManagedPropertyNames(AylaDevice device);
    }

    /**
     * Copy constructor
     * @param other AylaSystemSettings object to copy values from
     */
    public AylaSystemSettings(AylaSystemSettings other) {
        this.context = other.context;
        this.appId = other.appId;
        this.appSecret = other.appSecret;
        this.cloudProvider = other.cloudProvider;
        this.serviceType = other.serviceType;
        this.serviceLocation = other.serviceLocation;
        this.deviceDetailProvider = other.deviceDetailProvider;
        this.ssidRegex = other.ssidRegex;
        this.allowDSS = other.allowDSS;
        this.allowOfflineUse = other.allowOfflineUse;
        this.defaultNetworkTimeoutMs = other.defaultNetworkTimeoutMs;
        this.pushNotificationSenderId = other.pushNotificationSenderId;
        this.dssSubscriptionTypes = other.dssSubscriptionTypes;
        this.allowLANConnectionToSharedDevices = other.allowLANConnectionToSharedDevices;
        this.disableMetricsService = other.disableMetricsService;
        this.disableUncaughtExceptionHandler = other.disableUncaughtExceptionHandler;
    }

    /** ServiceType enumeration */
    public enum ServiceType {
        Field,
        Development
    }

    /** Service locations */
    public enum ServiceLocation {
        USA,
        China,
        Europe
    }

    /** Cloud Providers */
    public enum CloudProvider {
        AWS,
        GCP,
        VPC
    }
}

