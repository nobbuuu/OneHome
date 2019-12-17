package com.aylanetworks.aylasdk.util;

import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaSystemSettings.ServiceLocation;
import com.aylanetworks.aylasdk.AylaSystemSettings.CloudProvider;
import com.aylanetworks.aylasdk.AylaSystemSettings.ServiceType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/*
 * AylaSDK
 *
 * Copyright 2019 Ayla Networks, all rights reserved
 */

/**
 * This class contains the various URLs the SDK uses to connect to the cloud services. The URLs
 * vary depending on the specific cloud service (e.g. Device, User), the service type
 * (e.g. Field, Development) and service location (e.g. USA, China).
 *
 * Simply call {@link #getBaseServiceURL} to retrieve
 * the base URL for the desired service of the desired type in the desired location.
 */
public class ServiceUrls {

    private static final String LOG_TAG = "ServiceUrls";

    private static final String URL_KEY_TEMPLATE = "%s.%s.%s.%s";

    /**
     * Service types. Different APIs are called against different servers. To get the URL for
     * one of these CloudService types, call {@link #getBaseServiceURL} and pass in one of the
     * CloudService types.
     */
    public enum CloudService {
        Device,
        User,
        Datastream,
        mdssSubscription,
        Log,
        Metrics,
        Rules
    }

    /**
     * Returns the base URL for the specified CloudService. The URL returned may be directly
     * appended to in order to build a complete request URL.
     *
     * @param cloudProvider cloud provider AWS | GCP | VPC
     * @param cloudService CloudService to get the URL for
     * @param serviceType Type of service: Field | Development
     * @param serviceLocation USA | China | Europe
     *
     * @return a String with the base URL of the requested CloudService.
     *
     * <p>
     *  Note: VPC base URLs by default will be mapped to corresponding AWS USA base URLs,
     *  check the {{@link #map(CloudProvider, ServiceLocation, ServiceType, CloudService)}}
     *  method for all the mappings. To override this by either 1) Append your base URLs
     *  to the predefined URLs map {@link #__sCloudServiceURLProvider} with those provided
     *  by Ayla Customer Support for your Ayla Virtual Private IoT Cloud, or
     *  2) Call {@link #setServiceURLOverrides(Map)} with your base URLs before any call to
     *  cloud service.
     * </p>
     */
    public static String getBaseServiceURL(CloudProvider cloudProvider,
                                           CloudService cloudService,
                                           ServiceType serviceType,
                                           ServiceLocation serviceLocation) {
        // First check for an override
        String overrideURL = __serviceURLOverrideMap.get(cloudService);
        if (overrideURL != null) {
            return overrideURL;
        }

        String predefinedURL = __sCloudServiceURLProvider.get(key(cloudProvider, serviceLocation, serviceType, cloudService));
        if (predefinedURL != null) {
            return predefinedURL;
        }

        String mappedURL = __sCloudServiceURLProvider.get(map(cloudProvider, serviceLocation, serviceType, cloudService));
        if (mappedURL != null) {
            return mappedURL;
        }

        throw new IllegalArgumentException("No specified base service URL found!");
    }

    /**
     * Returns the base URL for the specified CloudService. The URL returned may be directly
     * appended to in order to build a complete request URL.
     *
     * @param cloudService CloudService to get the URL for
     * @param serviceType Type of service: Field | Development
     * @param serviceLocation USA | China | Europe
     *
     * @return a String with the base URL of the requested CloudService.
     */
    public static String getBaseServiceURL(CloudService cloudService,
                                           ServiceType serviceType,
                                           ServiceLocation serviceLocation) {
        return getBaseServiceURL(CloudProvider.AWS, cloudService, serviceType, serviceLocation);
    }

    /**
     * Sets override URLs for cloud services. All services contained in the map will be overridden
     * with the provided URL string. Any service not specified to be overridden will use the default
     * URL for the configured service type.
     *
     * Calling this method with a null argument will remove any existing overrides.
     *
     * @param overrides  Map of Cloud services to override
     *
     */
    public static void setServiceURLOverrides(Map<CloudService, String> overrides) {
        __serviceURLOverrideMap.clear();
        if (overrides != null) {
            __serviceURLOverrideMap.putAll(overrides);
        }
    }

    /**
     * Sets an individual service URL.
     * @param service Service to override
     * @param baseURL URL that should be used to connect to the service, or null to remove a previous
     *                override
     */
    public static void setServiceURLOverride(CloudService service, String baseURL) {
        if (baseURL == null) {
            __serviceURLOverrideMap.remove(service);
        } else {
            __serviceURLOverrideMap.put(service, baseURL);
        }
    }


    /**
     * Return dot-separated(".") map key for the provided service info.
     */
    private static String key(CloudProvider cloudProvider,
                              ServiceLocation serviceLocation,
                              ServiceType serviceType,
                              CloudService serviceName) {
        return String.format(Locale.US, URL_KEY_TEMPLATE,
                cloudProvider.name(),
                serviceLocation.name(),
                serviceType.name(),
                serviceName);
    }

    /**
     *
     * Map unavailable service locations to known ones, specifically
     * <li>
     *     <item>US GCP Development ==> US AWS Development</item>
     *     <item>EU GCP Development ==> US AWS Development</item>
     *     <item>CN GCP Development ==> CN AWS Development</item>
     *     <item>VPC Development ==> US AWS Development</item>
     *     <item>VPC Field ==> US AWS Field</item>
     * </li>
     *
     * @param cloudProvider service provider
     * @param serviceLocation service location
     * @param serviceType service type
     * @param serviceName service name
     *
     * @return dot-separated(".") map key of the provided service info, or null if
     * no service URL is not available.
     */
    private static String map(CloudProvider cloudProvider,
                              ServiceLocation serviceLocation,
                              ServiceType serviceType,
                              CloudService serviceName) {
        switch (serviceType) {
            case Development: {
                switch (cloudProvider) {
                    case GCP: {
                        switch (serviceLocation) {
                            case USA:
                                cloudProvider = CloudProvider.AWS;
                                break;

                            case Europe:
                                cloudProvider = CloudProvider.AWS;
                                serviceLocation = ServiceLocation.USA;
                                break;

                            case China:
                                cloudProvider = CloudProvider.AWS;
                                serviceLocation = ServiceLocation.China;
                                break;
                        }
                        break;
                    }
                }
                break;
            }

            case Field: {
                switch (cloudProvider) {
                    case GCP: {
                        switch (serviceLocation) {
                            case USA:
                                break;

                            case Europe:
                                AylaLog.e(LOG_TAG, "EU GCP Field is not supported");
                                return null;

                            case China:
                                AylaLog.e(LOG_TAG, "CN GCP Field is not supported");
                                return null;
                        }
                        break;
                    }

                }
                break;
            }
        }

        return String.format(Locale.US, URL_KEY_TEMPLATE,
                cloudProvider.name(),
                serviceLocation.name(),
                serviceType.name(),
                serviceName);
    }

    private static final Map<String, String> __sCloudServiceURLProvider = new HashMap<>();
    private static final Map<CloudService, String> __serviceURLOverrideMap = new HashMap<>();

    static {
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.User), "https://user-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.User), "https://user-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.Log), "https://log-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.Metrics), "https://metric-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.Device), "https://ads-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.Datastream),  "https://mstream-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.mdssSubscription),"https://mdss-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Development, CloudService.Rules),"https://rulesservice-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.User), "https://user-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.Log), "https://log-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.Metrics), "https://metric-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.Device), "https://ads-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.Datastream), "https://mstream-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.mdssSubscription), "https://mdss-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.USA, ServiceType.Field, CloudService.Rules), "https://rulesservice-field.aylanetworks.com/");

        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.User), "https://user-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.Log), "https://log-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.Metrics), "https://metric-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.Device), "https://ads-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.Datastream),  "https://mstream-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.mdssSubscription),"https://mdss-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Development, CloudService.Rules),"https://rulesservice-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.User), "https://user-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.Log), "https://log-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.Metrics), "https://metric-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.Device), "https://ads-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.Datastream), "https://mstream-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.mdssSubscription), "https://mdss-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.China, ServiceType.Field, CloudService.Rules), "https://rulesservice-field.ayla.com.cn/");

        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.User), "https://user-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.Log), "https://log-dev-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.Metrics), "https://metric-dev-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.Device), "https://ads-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.Datastream),  "https://mstream-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.mdssSubscription),"https://mdss-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Development, CloudService.Rules),"https://rulesservice-dev-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.User), "https://user-field-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.Log), "https://log-field-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.Metrics), "https://metric-field-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.Device), "https://ads-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.Datastream), "https://mstream-field-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.mdssSubscription), "https://mdss-field-eu.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.AWS, ServiceLocation.Europe, ServiceType.Field, CloudService.Rules), "https://rulesservice-field-eu.aylanetworks.com/");

        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.User), "https://user-field-rvnd.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.Log), "https://log-field-rvnd.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.Metrics), "https://log-field-rvnd.aylanetworks.com/"); // To Be deployed
        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.Device), "https://ads-field-rvnd.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.Datastream), "https://stream-field-rvnd.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.mdssSubscription), "https://mstream-field-rvnd.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.GCP, ServiceLocation.USA, ServiceType.Field, CloudService.Rules), "https://rulesservice-field-rvnd.aylanetworks.com/"); // To be deployed

        /*
         *    VPC Base URLs
         *
         *   Contact Ayla Customer Support if your company is running Ayla IoT Cloud Services on your own Virtual Private Cloud
         *   There are two option on how to use those custom VPC base URLs:
         *     1) Use a 'serviceOverrides' option similar to Aura and over ride these URLs from the application (preferred)
         *     2) Replace the base URLs below. While good for testing / validation, the base URLs will need to be maintained with every Ayla SDK update
         */
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.User), "https://user-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.User), "https://user-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.Log), "https://log-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.Metrics), "https://metric-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.Device), "https://ads-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.Datastream),  "https://mstream-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.mdssSubscription),"https://mdss-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Development, CloudService.Rules),"https://rulesservice-dev.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.User), "https://user-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.Log), "https://log-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.Metrics), "https://metric-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.Device), "https://ads-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.Datastream), "https://mstream-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.mdssSubscription), "https://mdss-field.aylanetworks.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.VPC, ServiceLocation.USA, ServiceType.Field, CloudService.Rules), "https://rulesservice-field.aylanetworks.com/");


        /**
         * SUNSEA Base URLs
         * Metrics/Rules 使用的是CloudProvider.China 提供的地址。
         */
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.User), "https://user-dev.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.Log), "https://log.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.Metrics), "https://metric-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.Device), "https://ads-dev.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.Datastream),  "https://mstream-dev.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.mdssSubscription),"https://mdss-dev.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Development, CloudService.Rules),"https://rulesservice-dev.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.User), "https://user-field.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.Log), "https://log.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.Metrics), "https://metric-field.ayla.com.cn/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.Device), "https://ads-field.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.Datastream), "https://mstream-field.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.mdssSubscription), "https://mdss-field.sunseaiot.com/");
        __sCloudServiceURLProvider.put(key(CloudProvider.SUNSEA, ServiceLocation.China, ServiceType.Field, CloudService.Rules), "https://rulesservice-field.ayla.com.cn/");
    }

}