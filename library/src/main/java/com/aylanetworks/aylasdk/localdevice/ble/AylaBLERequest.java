package com.aylanetworks.aylasdk.localdevice.ble;

import android.bluetooth.BluetoothGattCharacteristic;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.ErrorListener;

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
public class AylaBLERequest extends AylaAPIRequest<BluetoothGattCharacteristic> {

    private BluetoothGattCharacteristic _characteristic;

    public AylaBLERequest(BluetoothGattCharacteristic characteristic,
                          AylaSessionManager sessionManager,
                          Response.Listener<BluetoothGattCharacteristic> successListener,
                          ErrorListener errorListener) {
        super(Method.PUT, "local", null, BluetoothGattCharacteristic.class,
                sessionManager, successListener, errorListener);
        _characteristic = characteristic;
    }

    public void reportBLEError(int errorCode, String message) {
        getRequestErrorListener().onErrorResponse(new BLEError(errorCode, message));
    }

    public void reportBLEResponse() {
        getSuccessListener().onResponse(getCharacteristic());
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return _characteristic;
    }
}
