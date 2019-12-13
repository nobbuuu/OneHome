package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGattService;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This is an optional service whose primary function is to assist in device
 * registration over BLE. Additionally it also provides profile management capabilities.
 */
public final class AylaConnectivityGattService extends AylaBaseGattService {

    public static final UUID SERVICE_UUID           = UUID.fromString("FCE3EC41-59B6-4873-AE36-FAB25BD59ADC");
    private static final UUID CHAR_SETUP_TOKEN_UUID = UUID.fromString("7E9869ED-4DB3-4520-88EA-1C21EF1BA834");

    public AylaConnectivityGattService(@NonNull BluetoothGattService service) {
        super(service);
    }

    @Override
    public List<AylaBaseGattCharacteristic> getManagedCharacteristics() {
        List<AylaBaseGattCharacteristic> characteristics = new ArrayList<>();
        characteristics.add(new AylaSetupTokenGattCharacteristic(
                getOwner().getCharacteristic(CHAR_SETUP_TOKEN_UUID)));
        return characteristics;
    }
}
