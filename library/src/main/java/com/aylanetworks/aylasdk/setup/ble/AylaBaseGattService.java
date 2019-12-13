package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AylaBaseGattService {

    private WeakReference<BluetoothGattService> _ownerService;

    AylaBaseGattService(@NonNull BluetoothGattService service) {
        _ownerService = new WeakReference<>(service);
    }

    protected BluetoothGattService getOwner() {
        return _ownerService.get();
    }

    /**
     * Return the managed characteristics defined in this service. This doesn't
     * mean all the services defined in the service.
     */
    public List<AylaBaseGattCharacteristic> getManagedCharacteristics() {
        List<AylaBaseGattCharacteristic> characteristics = new ArrayList<>();
        for (BluetoothGattCharacteristic characteristic : getOwner().getCharacteristics()) {
            characteristics.add(new AylaBaseGattCharacteristic(characteristic));
        }
        return characteristics;
    }

    /**
     * Return the Ayla specific characteristic that corresponds with the specific
     * bluetooth characteristic.
     */
    public AylaBaseGattCharacteristic getManagedCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
        return getManagedCharacteristic(characteristic.getUuid());
    }

    /**
     * Return the characteristic with a specific UUID from the managed characteristics.
     */
    public AylaBaseGattCharacteristic getManagedCharacteristic(UUID characteristicUUID) {
        List<AylaBaseGattCharacteristic> characteristics = getManagedCharacteristics();
        if (characteristics != null && characteristics.size() > 0) {
            for (AylaBaseGattCharacteristic characteristic : characteristics) {
                if (characteristic.getOwner().getUuid().equals(characteristicUUID)) {
                    return characteristic;
                }
            }
        }

        return null;
    }
}
