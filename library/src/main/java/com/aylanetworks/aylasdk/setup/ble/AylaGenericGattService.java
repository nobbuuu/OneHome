package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGattService;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This is a mandatory service advertised by the BLE GATT server. It contains the
 * basic device information required to identify it as a device which conforms to
 * Ayla defined specifications, service formats, and UUIDs.
 */
public final class AylaGenericGattService extends AylaBaseGattService {

    public static final UUID SERVICE_UUID   = UUID.fromString("0000FE28-0000-1000-8000-00805F9B34FB");
    private static final UUID CHAR_DSN_UUID = UUID.fromString("00000001-FE28-435B-991A-F1B21BB9BCD0");

    public AylaGenericGattService(@NonNull BluetoothGattService service) {
        super(service);
    }

    @Override
    public List<AylaBaseGattCharacteristic> getManagedCharacteristics() {
        List<AylaBaseGattCharacteristic> characteristics = new ArrayList<>();
        characteristics.add(new AylaDSNCharacteristic(getOwner().getCharacteristic(CHAR_DSN_UUID)));
        return characteristics;
    }

}
