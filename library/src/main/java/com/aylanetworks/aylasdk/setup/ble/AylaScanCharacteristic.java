package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import com.aylanetworks.aylasdk.AylaLog;

import java.util.UUID;

/**
 * Scan characteristic allows request to start scanning for available Wi-Fi Access
 * Points (APs), by writing 1 to start scan.
 */
public class AylaScanCharacteristic extends AylaBaseGattCharacteristic {

    private static final String TAG = "AylaScanCharacteristic";

    public static final UUID CHAR_UUID = UUID.fromString("1F80AF6D-2B71-4E35-94E5-00F854D8F16F");

    public AylaScanCharacteristic(BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public boolean shouldHandleWrite() {
        return setStringValue("1");
    }
}
