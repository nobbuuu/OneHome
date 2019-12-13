package com.aylanetworks.aylasdk.setup;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDevice;
import com.aylanetworks.aylasdk.setup.ble.AylaBaseGattCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaBaseGattService;
import com.aylanetworks.aylasdk.setup.ble.AylaConnectivityGattService;
import com.aylanetworks.aylasdk.setup.ble.AylaDSNCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaGenericGattService;
import com.aylanetworks.aylasdk.setup.ble.AylaWiFiConfigGattService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Android_Aura
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

public class AylaBLEWiFiSetupDevice extends AylaBLEDevice {

    public static final String LOG_TAG = "AylaBLEWiFiSetupDevice";

    private Map<UUID, AylaBaseGattCharacteristic> _cachedCharacteristics = new HashMap<>();

    public BluetoothDevice getBluetoothDevice() {
        return _bluetoothDevice;
    }

    public BluetoothGatt getBluetoothGatt() {
        return _bluetoothGatt;
    }

    @Override
    public void setLanIp(String ip) {
        lanIp = ip;
    }

    @Override
    public String getLanIp() {
        return lanIp;
    }

    public void setRegistrationType(String regType) {
        registrationType = regType;
    }

    @Override
    public RegistrationType getRegistrationType() {
        for (AylaDevice.RegistrationType type : AylaDevice.RegistrationType.values()) {
            if (registrationType.equals(type.stringValue())) {
                return type;
            }
        }
        return RegistrationType.None;
    }

    public AylaBLEWiFiSetupDevice(BluetoothDevice discoveredDevice, int rssi, byte[] scanData) {
        super(discoveredDevice, rssi, scanData);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {

        if (status != BluetoothGatt.GATT_SUCCESS) {
            AylaLog.d(BTCB_TAG, "service discovery ended up with status:" + status);
            return;
        }

        List<BluetoothGattService> services = getBluetoothGatt().getServices();
        AylaLog.d(BTCB_TAG, "bluetoothGatt has " + services.size() + " services:");
        for (BluetoothGattService svc : services) {
            AylaLog.d(BTCB_TAG, svc.getUuid().toString());
        }

        List<AylaBaseGattService> managedServices = new ArrayList<>();
        BluetoothGattService service = gatt.getService(AylaGenericGattService.SERVICE_UUID);
        if (service == null) {
            AylaLog.e(LOG_TAG, "Could not find Ayla GATT service!");
        } else {
            managedServices.add(new AylaGenericGattService(service));
        }

        service = gatt.getService(AylaWiFiConfigGattService.SERVICE_UUID);
        if (service == null) {
            AylaLog.e(LOG_TAG, "Could not find Ayla Wifi Config service!");
        } else {
            managedServices.add(new AylaWiFiConfigGattService(service));
        }

        service = gatt.getService(AylaConnectivityGattService.SERVICE_UUID);
        if (service == null) {
            AylaLog.e(LOG_TAG, "Could not find Ayla Wifi Connectivity service!");
        } else {
            managedServices.add(new AylaConnectivityGattService(service));
        }

        for (AylaBaseGattService svc : managedServices) {
            for (AylaBaseGattCharacteristic characteristic : svc.getManagedCharacteristics()) {
                if (characteristic != null && characteristic.getOwner() != null) {
                    _cachedCharacteristics.put(characteristic.getUUID(), characteristic);
                } else {
                    AylaLog.e(LOG_TAG, "Could not find managed characteristic " + characteristic.getName());
                }
            }
        }

        // Have to explicitly call fetchCharacteristics even though
        // no characteristic are needed to fetch here, so that the
        // device connection result can be returned.
        fetchCharacteristics();
    }

    private AylaBaseGattCharacteristic getManagedCharacteristic(BluetoothGattCharacteristic characteristic) {
        return _cachedCharacteristics.get(characteristic.getUuid());
    }

    public AylaBaseGattCharacteristic getManagedCharacteristic(UUID uuid) {
        return _cachedCharacteristics.get(uuid);
    }

    @Override
    protected List<BluetoothGattCharacteristic> getCharacteristicsToFetch() {
        List<BluetoothGattCharacteristic> characteristicsToFetch = new ArrayList<>();
        // Add the minimum set of characteristics
        // that need to be read from here.
        characteristicsToFetch.add(_cachedCharacteristics.get(
                AylaDSNCharacteristic.CHAR_UUID).getOwner());
        return characteristicsToFetch;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        final BluetoothGattCharacteristic characteristic) {
        AylaLog.d(LOG_TAG, "onCharacteristicChanged " + characteristic.getUuid());

        AylaBaseGattCharacteristic mapping = getManagedCharacteristic(characteristic);
        if (mapping != null) {
            mapping.onCharacteristicChanged(gatt, characteristic);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        AylaLog.d(LOG_TAG, "onCharacteristicRead " + characteristic.getUuid() + ", with status " + status);

        AylaBaseGattCharacteristic mapping = getManagedCharacteristic(characteristic);
        if (mapping != null) {
            mapping.onCharacteristicRead(gatt, characteristic, status);
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        //super.onCharacteristicWrite(gatt, characteristic, status);
        AylaLog.d(LOG_TAG, "onCharacteristicWrite " + characteristic.getUuid() + ", with status " + status);

        AylaBaseGattCharacteristic mapping = getManagedCharacteristic(characteristic);
        if (mapping != null) {
            mapping.onCharacteristicWrite(gatt, characteristic, status);
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        //super.onDescriptorRead(gatt, descriptor, status);
        AylaLog.d(LOG_TAG, "onDescriptorRead " +  descriptor.getUuid() + ", with status " + status);

        AylaBaseGattCharacteristic mapping = getManagedCharacteristic(descriptor.getCharacteristic());
        if (mapping != null) {
            mapping.onDescriptorRead(gatt, descriptor, status);
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        //super.onDescriptorWrite(gatt, descriptor, status);
        AylaLog.d(LOG_TAG, "onDescriptorWrite " +  descriptor.getUuid() + ", with status " + status);

        AylaBaseGattCharacteristic mapping = getManagedCharacteristic(descriptor.getCharacteristic());
        if (mapping != null) {
            mapping.onDescriptorWrite(gatt, descriptor, status);
        }
    }

    @Override
    public AylaAPIRequest connectLocal(Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                       ErrorListener errorListener) {
        return connectLocal(successListener, errorListener, false);
    }

    /**
     * Returns the DSN of the device for confirming if the device has connected to cloud.
     */
    public String getDsn() {
        return ((AylaDSNCharacteristic) _cachedCharacteristics.get(
                AylaDSNCharacteristic.CHAR_UUID)).getDSN();
    }
}
