package com.aylanetworks.aylasdk.setup.ble.listeners;

import com.aylanetworks.aylasdk.setup.AylaWifiStatus;
import com.aylanetworks.aylasdk.setup.ble.AylaConnectStatusCharacteristic;

/**
 * Listener to receive connection status changes while connecting
 * the device to the specific access point.
 */
public interface OnConnectStatusChangedListener {

    /**
     * Device connected to the specific SSID.
     * @param ssid the target SSID the device was connected to.
     */
    void onConnected(String ssid);

    /**
     * Received connection state change reported by the device.
     * @param state the current connection state the device was in. can be one
     *              of the states defined in {@link AylaConnectStatusCharacteristic.State}.
     */
    void onConnectionStateChanged(AylaConnectStatusCharacteristic.State state);

    /**
     * Device failed to connect to the SSID because of an error.
     * @param ssid the SSID the device failed to connect to.
     * @param error the error which stopped the device from connecting to the SSID.
     */
    void onConnectionError(String ssid, AylaWifiStatus.HistoryItem.Error error);
}
