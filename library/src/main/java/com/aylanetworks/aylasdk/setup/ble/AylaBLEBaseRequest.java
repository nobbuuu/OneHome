package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGattCharacteristic;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.localdevice.ble.BLEError;

/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */


/**
 * Class used internally to wrap a BLE GATT request in an AylaAPIRequest.
 * This class is used by AylaBLEDevice to map requests to responses and call the appropriate
 * listeners.
 */
@SuppressWarnings("WeakerAccess")
public class AylaBLEBaseRequest extends AylaAPIRequest<AylaBaseGattCharacteristic> {

    public AylaBLEBaseRequest(Response.Listener<AylaBaseGattCharacteristic> successListener,
                              ErrorListener errorListener) {
        super(Method.PUT, "local", null, AylaBaseGattCharacteristic.class,
                null, successListener, errorListener);
    }

    public void reportBLEError(int errorCode, String message) {
        getRequestErrorListener().onErrorResponse(new BLEError(errorCode, message));
    }

    public void reportBLEError(BLEError error) {
        getRequestErrorListener().onErrorResponse(error);
    }
}
