package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import com.aylanetworks.aylasdk.AylaLog;

import java.util.UUID;

public class AylaSetupTokenGattCharacteristic extends AylaBaseGattCharacteristic {

    private static final String TAG = "AylaSetupTokenGattCharacteristic";

    public static final UUID CHAR_UUID = UUID.fromString("7E9869ED-4DB3-4520-88EA-1C21EF1BA834");

    public AylaSetupTokenGattCharacteristic(BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    @Override
    public String getName() {
        return TAG;
    }

    public void setSetupToken(String token) {
        setStringValue(token);
    }

}
