package com.aylanetworks.aylasdk.plugin;

/*
 * Aura_Android
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */


import com.aylanetworks.aylasdk.AylaDevice;

import org.json.JSONObject;

/**
 * The DeviceClassPlugin interface allows implementers to specify the specific AylaDevice-derived
 * class that should be created within the AylaDeviceManager. When device objects are to be
 * created, the AylaDeviceManager will first check with the registered DeviceClassPlugin, if
 * present, to determine what subtype to create.
 *
 * This allows application developers to create AylaDevice-derived classes to support
 * device-specific functionality, while having these objects created in place of AylaDevice
 * objects within AylaDeviceManager.
 */
public interface DeviceClassPlugin extends AylaPlugin {
    /**
     * Returns the AylaDevice-derived class for a given JSON object.
     * The method should return the appropriate class to create given the supplied object.
     *
     * If no matching class can be found, this method should return null to indicate it does not
     * support devices with the given model, oemModel or unique ID.
     *
     * The AylaDeviceManager depends on this method returning the appropriate class type so that
     * local devices may be constructed properly.
     *
     * A plugin implementing this interface should be installed with the plugin ID of
     * {@link com.aylanetworks.aylasdk.AylaNetworks#PLUGIN_ID_DEVICE_CLASS}.
     *
     * @param deviceJSON JSON object representing the device as received from the cloud service
     *
     * @return an AylaDevice-derived class object which will be used to construct this device
     * object.
     */
    Class<? extends AylaDevice> getDeviceClass(JSONObject deviceJSON);
}
