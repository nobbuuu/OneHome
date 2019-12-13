package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import com.aylanetworks.aylasdk.AylaLog;

import java.util.UUID;

public class AylaDSNCharacteristic extends AylaBaseGattCharacteristic {

    private static final String TAG = "AylaSetupTokenGattCharacteristic";

    public static final UUID CHAR_UUID = UUID.fromString("00000001-FE28-435B-991A-F1B21BB9BCD0");

    public AylaDSNCharacteristic(BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    public String getDSN() {
        return getStringValue();
    }

    @Override
    public String getName() {
        return TAG;
    }
}
