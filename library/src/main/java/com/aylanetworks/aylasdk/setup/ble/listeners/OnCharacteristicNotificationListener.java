package com.aylanetworks.aylasdk.setup.ble.listeners;

import android.bluetooth.BluetoothGattCharacteristic;

public interface OnCharacteristicNotificationListener {

    void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);

}
