package com.aylanetworks.aylasdk.setup.ble.listeners;

import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.setup.AylaWifiScanResults;

/**
 * Listener to receive AP scan results reported by the device.
 */
public interface OnScanResultChangedListener {

    /**
     * Received a new AP scan result from the device.
     * @param result the received AP scan result.
     */
    void onScanResultAvailable(AylaWifiScanResults.Result result);

    /**
     * Received all AP scan results from the device.
     * @param results the received AP scan results.
     */
    void onScanResultsAvailable(AylaWifiScanResults results);

    /**
     * Found error while receiving the scan results.
     * @param error the specific error that was found.
     */
    void onScanResultError(AylaError error);
}
