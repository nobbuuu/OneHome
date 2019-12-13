package com.aylanetworks.aylasdk.localdevice;

/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */


import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.plugin.DeviceClassPlugin;

/**
 * The AylaLocalDeviceManager interface is an {@link com.aylanetworks.aylasdk.plugin.AylaPlugin}
 * interface used to help manage devices that reside outside the IP network. Bluetooth LE devices
 * are an example of local devices.
 *
 * AylaLocalDeviceManager extends the {@link DeviceClassPlugin} interface, which allows this
 * class to assign a specific AylaDevice subclass to devices managed by AylaDeviceManager.
 *
 * Implementers of this interface will need to provide means for discovering, registering and
 * unregistering local devices. An example class that implements these interfaces for Bluetooth LE
 * devices can be found in {@link com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDeviceManager}.
 */
public interface AylaLocalDeviceManager extends DeviceClassPlugin {
    /**
     * Local Device Plugin for communications with devices outside of LAN / Internet, such as
     * Bluetooth LE devices. These plugins implement the AylaLocalDeviceManager interface.
     *
     * These plugins work with the Ayla Local Device SDK.
     */
    String PLUGIN_ID_LOCAL_DEVICE = "com.aylanetworks.aylasdk.localdevice";


    /**
     * Searches for AylaLocalDevices nearby. The returned list should contain AylaLocalDevices
     * that have been locally discovered. The returned devices may or may not be registered to
     * the user.
     *
     * @param hint Search-specific hint used to filter the set of returned devices. The format of
     *             this object is not defined, but may be used by subclasses to filter based on
     *             Bluetooth address ranges, GATT service IDs, etc.
     *
     * @param timeoutInMs Maximum time the search should take in ms
     * @param successListener Listener to be notified with the results
     * @param errorListener Listener to receive an error should one occur.
     *
     * @return an AylaAPIRequest object. This object may be used to cancel the operation before
     * the scan has completed.
     */
    AylaAPIRequest findLocalDevices(Object hint,
                                    int timeoutInMs,
                                    Response.Listener<AylaLocalDevice[]> successListener,
                                    ErrorListener errorListener);

    /**
     * Registers a local device with the Ayla service. Devices are unregistered via a call to the
     * device object's {@link AylaLocalDevice#unregister(Response.Listener, ErrorListener)} method.
     *
     * @param sessionManager Session manager for the account this device should be registered to
     * @param device Device to register. This should have been returned from findLocalDevices.
     * @param oem OEM ID for the account. This may be null if only one OEM exists for the
     *            signed-in account.
     * @param successListener Listener to receive the registered device upon success
     * @param errorListener Listener to receive an error should one occur
     *
     * @return an AylaAPIRequest, which may be used to cancel the operation
     */
    AylaAPIRequest registerLocalDevice(AylaSessionManager sessionManager,
                                       AylaLocalDevice device,
                                       String oem,
                                       Response.Listener<AylaLocalDevice> successListener,
                                       ErrorListener errorListener);

}
