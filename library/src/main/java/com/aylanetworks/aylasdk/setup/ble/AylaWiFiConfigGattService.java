package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGattService;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This service contains the characteristics required to actually configure
 * the Wi-Fi network parameters and request the device to begin to join a
 * particular Access Point (AP).
 */
public final class AylaWiFiConfigGattService extends AylaBaseGattService {

    public static final UUID SERVICE_UUID             = UUID.fromString("1CF0FE66-3ECF-4D6E-A9FC-E287AB124B96");

    private static final UUID CHAR_SCAN_UUID           = UUID.fromString("1F80AF6D-2B71-4E35-94E5-00F854D8F16F");
    private static final UUID CHAR_SCAN_RESULTS_UUID   = UUID.fromString("1F80AF6E-2B71-4E35-94E5-00F854D8F16F");
    private static final UUID CHAR_CONNECT_UUID        = UUID.fromString("1F80AF6A-2B71-4E35-94E5-00F854D8F16F");
    private static final UUID CHAR_CONNECT_STATUS_UUID = UUID.fromString("1F80AF6C-2B71-4E35-94E5-00F854D8F16F");

    public AylaWiFiConfigGattService(@NonNull BluetoothGattService service) {
        super(service);
    }

    @Override
    public List<AylaBaseGattCharacteristic> getManagedCharacteristics() {
        List<AylaBaseGattCharacteristic> managedCharacteristics = new ArrayList<>();
        managedCharacteristics.add(new AylaScanCharacteristic(getOwner().getCharacteristic(CHAR_SCAN_UUID)));
        managedCharacteristics.add(new AylaScanResultCharacteristic(getOwner().getCharacteristic(CHAR_SCAN_RESULTS_UUID)));
        managedCharacteristics.add(new AylaConnectCharacteristic(getOwner().getCharacteristic(CHAR_CONNECT_UUID)));
        managedCharacteristics.add(new AylaConnectStatusCharacteristic(getOwner().getCharacteristic(CHAR_CONNECT_STATUS_UUID)));
        return managedCharacteristics;
    }
}
