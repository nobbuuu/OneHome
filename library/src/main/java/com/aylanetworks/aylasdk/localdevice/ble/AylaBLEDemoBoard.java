package com.aylanetworks.aylasdk.localdevice.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.text.TextUtils;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.change.PropertyChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.localdevice.AylaLocalDevice;
import com.aylanetworks.aylasdk.localdevice.AylaLocalProperty;
import com.aylanetworks.aylasdk.localdevice.AylaLocalRegistrationCandidate;
import com.aylanetworks.aylasdk.localdevice.LocalOTACommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Android_Aura
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */
@SuppressWarnings("WeakerAccess")
public class AylaBLEDemoBoard extends AylaBLEDevice {
    private static final String LOG_TAG = "BLEDevKit";

    public static final String DEFAULT_PRODUCT_NAME = "Ayla BLE Demo";

    public static final UUID SERVICE_AYLA_THERMOSTAT = UUID.fromString
            ("28E7B565-0215-46D7-A924-B8E7C48EAB9B");

    // Characteristic IDs
    public static final UUID CHAR_AC_ON = UUID.fromString
            ("1950C6C9-6566-4608-8210-D712E3DF95B0");
    public static final UUID CHAR_HEAT_ON = UUID.fromString
            ("1950C6C9-6566-4608-8210-D712E3DF95B1");
    public static final UUID CHAR_LOCAL_TEMP = UUID.fromString
            ("1950C6C9-6566-4608-8210-D712E3DF95B2");
    public static final UUID CHAR_TEMP_SETPOINT = UUID.fromString
            ("1950C6C9-6566-4608-8210-D712E3DF95B3");
    public static final UUID CHAR_VACATION = UUID.fromString
            ("1950C6C9-6566-4608-8210-D712E3DF95B4");

    // Property names
    public static final String PROP_OEM_HOST_VERSION = "oem_host_version";
    public static final String PROP_AC_ON = "00:bletstat:ac_on";
    public static final String PROP_HEAT_ON = "00:bletstat:heat_on";
    public static final String PROP_LOCAL_TEMP = "00:bletstat:local_temp";
    public static final String PROP_TEMP_SET = "00:bletstat:temp_setpoint";
    public static final String PROP_VACATION = "00:bletstat:vacation_mode";

    public static final String BLE_DEMO_MODEL = "bledemo";

    private BluetoothGattService _thermostatService;
    private List<BluetoothGattCharacteristic> _notifyCharcteristics;

    // Property value storage
    private boolean _acOn;
    private boolean _heatOn;
    private double _localTemp;
    private int _tempSetpoint;
    private boolean _vacation;

    public AylaBLEDemoBoard(BluetoothDevice discoveredDevice, int rssi, byte[] scanData) {
        super(discoveredDevice, rssi, scanData);
    }

    @SuppressWarnings("unused")
    public AylaBLEDemoBoard() {
        super();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        // Find our thermostat service
        _thermostatService = gatt.getService(SERVICE_AYLA_THERMOSTAT);
        if (_thermostatService == null) {
            AylaLog.e(LOG_TAG, "Could not find thermostat service!");
        }

        // Let the superclass continue
        super.onServicesDiscovered(gatt, status);
    }

    public static String[] getManagedProperties() {
        return new String[]{PROP_AC_ON, PROP_HEAT_ON, PROP_LOCAL_TEMP,
        PROP_TEMP_SET, PROP_VACATION};
    }

    // We need to assume that T is the type we expect it to be
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValueForProperty(AylaLocalProperty<T> property) {
        if (!isConnectedLocal()) {
            // We are not connected. Return the cloud's value for this property
            return property.getOriginalProperty().getValue();
        }

        switch (property.getName()) {
            case PROP_AC_ON:
                return (T)Integer.valueOf(_acOn ? 1 : 0);

            case PROP_HEAT_ON:
                return (T) Integer.valueOf(_heatOn ? 1 : 0);

            case PROP_LOCAL_TEMP:
                return (T) Double.valueOf(_localTemp);

            case PROP_OEM_HOST_VERSION:
                return (T) _oemHostVersion;

            case PROP_TEMP_SET:
                return (T) Integer.valueOf(_tempSetpoint);

            case PROP_VACATION:
                return (T) Integer.valueOf(_vacation ? 1 : 0);

            default:
                return super.getValueForProperty(property);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> AylaAPIRequest<T> setValueForProperty(AylaLocalProperty<T> property,
                                                     final T value,
                                                     final Response.Listener<T> successListener,
                                                     final ErrorListener errorListener) {
        if (!isConnectedLocal()) {
            errorListener.onErrorResponse(new PreconditionError("Properties are read-only unless "
                    + "the device is connected locally."));
            return null;
        }

        AylaAPIRequest<T> request = null;
        BluetoothGattCharacteristic c;
        switch(property.getName()) {
            case PROP_TEMP_SET:
                c = _thermostatService.getCharacteristic
                    (CHAR_TEMP_SETPOINT);
                c.setValue((Integer)value, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                request = writeCharacteristic(c, new Response.Listener<BluetoothGattCharacteristic>() {
                    @Override
                    public void onResponse(BluetoothGattCharacteristic response) {
                        _tempSetpoint = (Integer)value;
                        successListener.onResponse(value);
                    }
                }, errorListener);
                break;

            case PROP_VACATION:
                c = _thermostatService.getCharacteristic(CHAR_VACATION);
                c.setValue((Integer)value, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                request = writeCharacteristic(c, new Response.Listener<BluetoothGattCharacteristic>() {
                    @Override
                    public void onResponse(BluetoothGattCharacteristic response) {
                        _vacation = ((Integer)value != 0);
                        successListener.onResponse(value);
                    }
                }, errorListener);
                break;
        }

        return request;
    }

    @Override
    public boolean isPropertyReadOnly(AylaLocalProperty property) {
        if (property == null) {
            return true;
        }

        switch (property.getName()) {
            case PROP_TEMP_SET:
            case PROP_VACATION:
                return false;
        }
        return true;
    }

    @Override
    public String getModel() {
        if (TextUtils.isEmpty(model)) {
            model = BLE_DEMO_MODEL;
        }
        return model;
    }

    @Override
    public String getProductName() {
        if (productName == null) {
            productName = DEFAULT_PRODUCT_NAME;
        }

        return super.getProductName();
    }

    @Override
    protected void onOTAReceived(LocalOTACommand otaCommand, String filename) {
        // The OTA file was received
        AylaLog.d(LOG_TAG, "File downloaded for OTA command " + filename);

        // We're not really updating the board. Just ack the command.
        setOTAStatus(0, otaCommand.command_id,
                new Response.Listener<AylaAPIRequest.EmptyResponse>() {
            @Override
            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                AylaLog.i(LOG_TAG, "OTA status sent successfully");
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "Error trying to return status for OTA command: " +  error
                        .getMessage());
            }
        });
    }

    @Override
    public String getCandidateJson(String oem) {
        if (getHardwareAddress() == null) {
            AylaLog.e(LOG_TAG, "Cannot register without a hardware address!");
            return null;
        }
        if (getOemModel() == null) {
            AylaLog.e(LOG_TAG, "Cannot register without a OEM model!");
            return null;
        }
        if (getModel() == null) {
            AylaLog.e(LOG_TAG, "Cannot register without a model!");
            return null;
        }

        AylaLocalRegistrationCandidate rc = new AylaLocalRegistrationCandidate();
        rc.device.unique_hardware_id = getHardwareAddress();
        rc.device.oem_model = getOemModel();
        rc.device.model = getModel();
        rc.device.oem = oem;
        rc.device.sw_version = "0.1";

        // This device is composed of a single subdevice
        rc.device.subdevices = new AylaLocalRegistrationCandidate.Subdevice[1];
        rc.device.subdevices[0] = new AylaLocalRegistrationCandidate.Subdevice();
        rc.device.subdevices[0].subdevice_key = String.format(Locale.US, "%02d", 0);
        rc.device.subdevices[0].templates = new AylaLocalRegistrationCandidate.Template[1];
        rc.device.subdevices[0].templates[0] = new AylaLocalRegistrationCandidate.Template();
        rc.device.subdevices[0].templates[0].template_key = "bletstat";
        rc.device.subdevices[0].templates[0].version = "1.2";

        String json = AylaNetworks.sharedInstance().getGson().toJson(rc);
        AylaLog.d(LOG_TAG, "Reg candidate JSON:\n" + json);
        return json;
    }

    @Override
    protected List<BluetoothGattCharacteristic> getCharacteristicsToFetch() {
        List<BluetoothGattCharacteristic> characteristics = super.getCharacteristicsToFetch();
        if (_thermostatService != null) {
            characteristics.add(_thermostatService.getCharacteristic(CHAR_AC_ON));
            characteristics.add(_thermostatService.getCharacteristic(CHAR_HEAT_ON));
            characteristics.add(_thermostatService.getCharacteristic(CHAR_LOCAL_TEMP));
            characteristics.add(_thermostatService.getCharacteristic(CHAR_TEMP_SETPOINT));
            characteristics.add(_thermostatService.getCharacteristic(CHAR_VACATION));
        } else {
            AylaLog.e(LOG_TAG, "Cannot get characteristics without the thermostat service!");
        }
        return characteristics;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        AylaLocalProperty changedProperty = null;

        if (uuid.equals(CHAR_AC_ON)) {
            _acOn = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) != 0;
            changedProperty = (AylaLocalProperty)getProperty(PROP_AC_ON);
        } else if (uuid.equals(CHAR_HEAT_ON)) {
            _heatOn = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) != 0;
            changedProperty = (AylaLocalProperty)getProperty(PROP_HEAT_ON);
        } else if (uuid.equals(CHAR_LOCAL_TEMP)) {
            changedProperty = (AylaLocalProperty)getProperty(PROP_LOCAL_TEMP);
            _localTemp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        } else if (uuid.equals(CHAR_TEMP_SETPOINT)) {
            changedProperty = (AylaLocalProperty)getProperty(PROP_TEMP_SET);
            _tempSetpoint = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        } else if (uuid.equals(CHAR_VACATION)) {
            changedProperty = (AylaLocalProperty)getProperty(PROP_VACATION);
            _vacation = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    != 0;
        }

        if (changedProperty != null) {
            PropertyChange change = new PropertyChange(changedProperty.getName());
            changedProperty.getOwner().notifyDeviceChanged(change, DataSource.LOCAL);
            changedProperty.pushUpdateToCloud();
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            onCharacteristicChanged(gatt, characteristic);
        }
        super.onCharacteristicRead(gatt, characteristic, status);
    }

    @Override
    protected void onCharacteristicsFetched() {
        super.onCharacteristicsFetched();
        if (_thermostatService == null) {
            AylaLog.e(LOG_TAG, "Thermostat service is null in onCharacteristicsFetched");
            return;
        }

        // Turn on notifications
        UUID[] notifyCharacteristics = new UUID[]{ CHAR_AC_ON, CHAR_HEAT_ON, CHAR_LOCAL_TEMP,
                CHAR_TEMP_SETPOINT, CHAR_VACATION};
        _notifyCharcteristics = new ArrayList<>(notifyCharacteristics.length);
        for (UUID uuid : notifyCharacteristics) {
            BluetoothGattCharacteristic c = _thermostatService.getCharacteristic(uuid);
            _notifyCharcteristics.add(c);
        }

        // Put off notifying listeners of connectivity until we enable notifiactions
        enableNextNotify();
    }

    private void enableNextNotify() {
        if (_notifyCharcteristics.size() == 0) {
            AylaLog.d(LOG_TAG, "Finished enabling notifications");
            // Let any pending callers know we're done connecting
            super.onCharacteristicsFetched();
            return;
        }

        BluetoothGattCharacteristic c = _notifyCharcteristics.remove(0);
        _bluetoothGatt.setCharacteristicNotification(c, true);
        BluetoothGattDescriptor enableDescriptor =
                c.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR);
        enableDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!_bluetoothGatt.writeDescriptor(enableDescriptor)) {
            AylaLog.e(LOG_TAG, "Failed to enable notification for " + c.getUuid());
            enableNextNotify();
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        if (status != BluetoothGatt.GATT_SUCCESS) {
            AylaLog.e(LOG_TAG, "onDescriptorWrite status " + status + " for " + descriptor
                    .getCharacteristic().getUuid());
        }

        if (descriptor.getUuid().equals(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR)) {
            // We are working on setting up notifications. Continue.
            enableNextNotify();
        }
    }
}
