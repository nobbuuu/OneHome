package com.aylanetworks.aylasdk.localdevice.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.ListChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.localdevice.AylaLocalDevice;
import com.aylanetworks.aylasdk.localdevice.AylaLocalDeviceManager;
import com.aylanetworks.aylasdk.setup.AylaBLEWiFiSetupDevice;
import com.aylanetworks.aylasdk.setup.AylaRegistration;
import com.aylanetworks.aylasdk.setup.AylaRegistrationCandidate;
import com.aylanetworks.aylasdk.setup.ble.AylaWiFiConfigGattService;
import com.aylanetworks.aylasdk.util.EmptyListener;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */


/**
 * The AylaBLEDeviceManager implements the AylaLocalDeviceManager and DeviceListPlugin interfaces
 * to provide local device support for BLE devices.
 */
@SuppressWarnings("WeakerAccess")
public class AylaBLEDeviceManager implements AylaLocalDeviceManager, AylaDeviceManager.DeviceManagerListener {
    private static final String LOG_TAG = "AylaBLEMgr";

    private BluetoothAdapter _bluetoothAdapter;
    private WeakReference<AylaDeviceManager> _deviceManager;

    /**
     * AylaBLEDeviceManager constructor.
     * @param context Context for the application
     */
    public AylaBLEDeviceManager(Context context) {
        BluetoothManager bm = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        _bluetoothAdapter = bm.getAdapter();
    }

    @Override
    public void deviceManagerInitComplete(Map<String, AylaError> deviceFailures) {

    }

    @Override
    public void deviceManagerInitFailure(AylaError error, AylaDeviceManager.DeviceManagerState failureState) {

    }

    @Override
    public void deviceListChanged(ListChange change) {
        updateLocalDevices();
    }

    @Override
    public void deviceManagerError(AylaError error) {

    }

    @Override
    public void deviceManagerStateChanged(AylaDeviceManager.DeviceManagerState oldState, AylaDeviceManager.DeviceManagerState newState) {

    }

    public interface ScanFilter {
        boolean filter(ScanRecordHelper scanRecord);
    }

    /**
     * Filter for service scans that filters based on discvered service UUIDs
     */
    public static class ServiceScanFilter implements ScanFilter {
        private UUID[] _scanServices;
        public ServiceScanFilter(UUID[] scanServices) {
            _scanServices = scanServices;
        }
        public boolean filter(ScanRecordHelper scanRecord) {
            for (UUID uuid : _scanServices) {
                if (scanRecord.containsService(uuid)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Finds local BLE devices which advertise a service ID specified in the array of serviceUUIDs
     * used to initialize the AylaBLEDeviceManager
     *
     * @param timeoutInMs Timeout for discovery in ms
     * @param successListener Listener to receive the results of the scan
     * @param errorListener Listener to receive an error should one occur
     * @return an AylaAPIRequest, which may be used to cancel the request
     */
    public AylaAPIRequest findLocalDevices(int timeoutInMs,
                                           final Response.Listener<AylaLocalDevice[]> successListener,
                                           ErrorListener errorListener) {
        // Make a filter that allows everything through
        ScanFilter filter = new ServiceScanFilter(null) {
            @Override
            public boolean filter(ScanRecordHelper scanRecord) {
                return true;
            }
        };
        return findLocalDevices(filter, timeoutInMs, successListener, errorListener);
    }

    /**
     * The AylaBLEDeviceManager's implementation of this uses a {@link ScanFilter} object as the
     * hint when doing a scan.
     *
     * @param hint Search-specific hint used to filter the set of returned devices. The format of
     *             this object is not defined, but may be used by subclasses to filter based on
     *             Bluetooth address ranges, GATT service IDs, etc.
     *
     * @param timeoutInMs Maximum time the search should take in ms
     * @param successListener Listener to be notified with the results
     * @param errorListener Listener to receive an error should one occur.
     *
     * @return a cancelable AylaAPIRequest
     */
    @Override
    public AylaAPIRequest findLocalDevices(final Object hint,
                                           final int timeoutInMs,
                                           final Response.Listener<AylaLocalDevice[]> successListener,
                                           final ErrorListener errorListener) {
        final ScanFilter scanFilter = (ScanFilter)hint;

        if (_bluetoothAdapter == null) {
            errorListener.onErrorResponse(new PreconditionError("Bluetooth adapter not available"));
            return null;
        }

        if (!_bluetoothAdapter.isEnabled()) {
            errorListener.onErrorResponse(new PreconditionError("Bluetooth is not enabled"));
            return null;
        }

        final Map<String, AylaLocalDevice> discoveredDevices = new HashMap<>();
        final BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
                ScanRecordHelper srh = ScanRecordHelper.parseFromBytes(scanRecord);
                boolean notAdded = !discoveredDevices.containsKey(bluetoothDevice.getAddress());
                boolean shouldAdd = (scanFilter != null) ? (scanFilter.filter(srh) && notAdded) : notAdded;
                if (shouldAdd) {
                    AylaBLEDevice foundDevice = createLocalDevice(bluetoothDevice, rssi, scanRecord);
                    discoveredDevices.put(bluetoothDevice.getAddress(), foundDevice);
                }
            }
        };

        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        //noinspection deprecation
                        _bluetoothAdapter.stopLeScan(callback);
                        Collection<AylaLocalDevice> devices = discoveredDevices.values();
                        successListener.onResponse(devices.toArray(new AylaLocalDevice[devices.size()]));
                    }
                }, timeoutInMs);

        // Start the BLE scan
        //noinspection deprecation
        _bluetoothAdapter.startLeScan(callback);

        // Create an AylaAPIRequest to handle cancellation
        return new AylaAPIRequest<AylaLocalDevice[]>(
                Request.Method.GET,
                "LESCAN",
                null,
                AylaLocalDevice[].class,
                null, successListener, errorListener) {
            @Override
            public void cancel() {
                timeoutHandler.removeCallbacksAndMessages(null);
                //noinspection deprecation
                _bluetoothAdapter.stopLeScan(callback);
            }
        };
    }

    /**
     * Scans for BLE Setup devices.
     * BLE Setup devices are devices that support the Ayla WiFi Configuration service. These
     * devices can be configured over BLE with WiFi SSID and credentials to join the local network.
     *
     * @param scanFilter used to filter the set of returned devices.
     * @param timeoutInMs Maximum time the search should take in ms.
     * @param successListener Listener to be notified with the results.
     * @param errorListener Listener to receive an error should one occur.
     *
     * @return a cancelable AylaAPIRequest
     */
    public AylaAPIRequest findBLESetupDevices(final ScanFilter scanFilter,
                                              final int timeoutInMs,
                                              final Response.Listener<AylaBLEWiFiSetupDevice[]> successListener,
                                              final ErrorListener errorListener) {
        return findLocalDevices(scanFilter, timeoutInMs,
                new Response.Listener<AylaLocalDevice[]>() {
                    @Override
                    public void onResponse(AylaLocalDevice[] response) {
                        List<AylaBLEWiFiSetupDevice> devices = new ArrayList<>();
                        for (AylaLocalDevice device : response) {
                            if (device instanceof AylaBLEWiFiSetupDevice) {
                                devices.add((AylaBLEWiFiSetupDevice) device);
                            }
                        }
                        successListener.onResponse(devices.toArray(
                                new AylaBLEWiFiSetupDevice[devices.size()]));
                    }
                }, errorListener);
    }

    @Override
    public AylaAPIRequest registerLocalDevice(final AylaSessionManager sessionManager,
                                              final AylaLocalDevice localDevice,
                                              final String oem,
                                              final Response.Listener<AylaLocalDevice> successListener,
                                              final ErrorListener errorListener) {
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("Valid session required"));
            return null;
        }

        if (!localDevice.isConnectedLocal()) {
            // We need to connect to the device first
            AylaLog.i(LOG_TAG, "Connecting to registration candidate " + localDevice);
            localDevice.connectLocal(new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                @Override
                public void onResponse(AylaAPIRequest.EmptyResponse response) {
                    // We should be connected now, try calling ourselves again
                    registerLocalDevice(sessionManager, localDevice, oem, successListener,
                            errorListener);
                }
            }, errorListener);
        }

        final AylaDeviceManager dm = sessionManager.getDeviceManager();
        if (dm == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager available"));
            return null;
        }

        String dsn = localDevice.getDsn();
        if (dsn != null && dm.deviceWithDSN(dsn) != null) {
            errorListener.onErrorResponse(new PreconditionError("Device is already registered"));
            return null;
        }

        final AylaAPIRequest<AylaLocalDevice> returnedRequest = new AylaAPIRequest<>(
                Request.Method.GET, null, null, AylaLocalDevice.class, sessionManager,
                successListener, errorListener);

        // Make sure the device stays connected or call the error listener
        final AylaDevice.DeviceChangeListener dcListener = new AylaDevice.DeviceChangeListener() {
            @Override
            public void deviceChanged(AylaDevice device, Change change) {
                if (!localDevice.isConnectedLocal()) {
                    // We can't continue
                    returnedRequest.cancel();
                    errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                            "Device disconnected from Bluetooth during setup, unable to register"));
                }
            }

            @Override
            public void deviceError(AylaDevice device, AylaError error) {
                returnedRequest.cancel();
                errorListener.onErrorResponse(error);
            }

            @Override
            public void deviceLanStateChanged(AylaDevice device, boolean lanModeEnabled, AylaError error) {

            }
        };
        localDevice.addListener(dcListener);

        final EmptyListener<AylaAPIRequest.EmptyResponse> emptyListener = new EmptyListener<>();
        final ErrorListener cleanupErrorListener = new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "Error during local device registration: " + error.getMessage());
                localDevice.removeListener(dcListener);
                localDevice.disconnectLocal(emptyListener, emptyListener);
                errorListener.onErrorResponse(error);
            }
        };

        // Request a registration candidate from the service using the information we have from
        // this device
        String url = dm.deviceServiceUrl("apiv1/devices/discover.json");
        String jsonBody = localDevice.getCandidateJson(oem);
        if (jsonBody == null) {
            errorListener.onErrorResponse(new PreconditionError("Cannot register BLE device " +
                    "without all of: unique_hardware_id, model, oem_model"));
            return null;
        }

        AylaJsonRequest<AylaDevice.Wrapper> request = new AylaJsonRequest<>(
                Request.Method.POST, url, jsonBody, null, AylaBLEDevice.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaDevice.Wrapper>() {
                    @Override
                    public void onResponse(AylaDevice.Wrapper response) {
                        if (returnedRequest.isCanceled()) {
                            localDevice.removeListener(dcListener);
                            localDevice.disconnectLocal(emptyListener, emptyListener);
                            AylaLog.i(LOG_TAG, "Local candidate: request canceled");
                            return;
                        }

                        AylaLog.d(LOG_TAG, "Response to discover.json: " + response);
                        localDevice.updateFrom(response.device, AylaDevice.DataSource.CLOUD);

                        // Register the device
                        AylaRegistration registration = sessionManager.getDeviceManager()
                                .getAylaRegistration();
                        AylaRegistrationCandidate candidate = new AylaRegistrationCandidate(localDevice);
                        candidate.setRegistrationType(AylaDevice.RegistrationType.DSN);
                        candidate.setHardwareAddress(localDevice.getHardwareAddress());
                        registration.registerCandidate(candidate, new Response.Listener<AylaDevice>() {
                            @Override
                            public void onResponse(AylaDevice response) {
                                AylaLog.i(LOG_TAG, "Registration success: " + response);
                                localDevice.removeListener(dcListener);
                                localDevice.updateFrom(response, AylaDevice.DataSource.CLOUD);

                                AylaBLEDevice bleDevice = (AylaBLEDevice) localDevice;

                                // Save the mapping between the unique hardware ID and the BD_ADDR
                                bleDevice.mapBluetoothAddress(bleDevice.getBluetoothAddress());

                                // The call to register should have added our device already.
                                // Let's get that one and update it.
                                AylaBLEDevice registeredDevice = (AylaBLEDevice)dm
                                        .deviceWithDSN(localDevice.getDsn());
                                if (registeredDevice == null) {
                                    dm.addDevice(bleDevice);
                                    registeredDevice = bleDevice;
                                } else {
                                    registeredDevice.updateFrom(bleDevice, AylaDevice.DataSource.CLOUD);
                                }

                                // The device manager's version is now the one that talks to the
                                // device. Disconnect ours so it doesn't remain connected as well.
                                bleDevice.disconnectLocal(emptyListener, emptyListener);
                                successListener.onResponse(registeredDevice);
                            }
                        }, cleanupErrorListener);

                    }
                }, cleanupErrorListener);

        dm.sendDeviceServiceRequest(request);
        returnedRequest.setChainedRequest(request);

        return returnedRequest;
    }

    @Override
    public Class<? extends AylaDevice> getDeviceClass(JSONObject deviceJson) {
        String model = deviceJson.optString("model", null);
        String oemModel = deviceJson.optString("oem_model", null);

        if (TextUtils.equals(model, AylaBLEDevice.DEFAULT_MODEL) &&
                TextUtils.equals(oemModel, AylaBLEDevice.DEFAULT_OEM_MODEL)) {
            return AylaBLEDevice.class;
        }

        // Not a device we are familiar with
        return null;
    }

    /**
     * Method to create an AylaBLEDevice from a BluetoothDevice, rssi and service record. This
     * implementation creates a new AylaBLEDevice and returns it.
     *
     * @param bluetoothDevice BluetoothDevice found in a scan
     * @param rssi RSSI
     * @param scanRecord Scan record of the device
     * @return the AylaBLEDevice for this record
     */
    protected AylaBLEDevice createLocalDevice(BluetoothDevice bluetoothDevice, int rssi, byte[]
            scanRecord) {
        ScanRecordHelper helper = ScanRecordHelper.parseFromBytes(scanRecord);
        if (helper.containsService(AylaWiFiConfigGattService.SERVICE_UUID))  {
            return new AylaBLEWiFiSetupDevice(bluetoothDevice, rssi, scanRecord);
        }
        return new AylaBLEDevice(bluetoothDevice, rssi, scanRecord);
    }

    protected void updateLocalDevices() {
        // Scan for our local devices and connect to them
        AylaDeviceManager dm = _deviceManager.get();

        if (dm == null) {
            AylaLog.d(LOG_TAG, "onResume: No device manager available");
            return;
        }

        // Go through the device list and connect any local devices
        List<AylaDevice> devices = dm.getDevices();
        for (AylaDevice d : devices) {
            if (d instanceof AylaBLEDevice) {
                AylaBLEDevice bleDevice = (AylaBLEDevice) d;
                EmptyListener<AylaAPIRequest.EmptyResponse> emptyListener = new EmptyListener<>();
                AylaLog.d(LOG_TAG, "Attempting connection to BLE device " + bleDevice);
                bleDevice.initializeBluetooth();
                bleDevice.connectLocal(emptyListener, emptyListener);
            }
        }

    }

    // AylaPlugin methods
    @Override
    public String pluginName() {
        return "Ayla BLE Device Manager";
    }

    @Override
    public void initialize(String pluginId, AylaSessionManager sessionManager) {
        AylaLog.d(LOG_TAG, "AylaBLEDeviceManager: Initialize in role of: " + pluginId);
        if (pluginId.equals(AylaLocalDeviceManager.PLUGIN_ID_LOCAL_DEVICE)) {
            // Save the session's device manager
            _deviceManager = new WeakReference<>(sessionManager.getDeviceManager());
            _deviceManager.get().addListener(this);
        }
    }

    @Override
    public void onPause(String pluginId, AylaSessionManager sessionManager) {
        AylaLog.d(LOG_TAG, "AylaBLEDeviceManager: onPause");
        // When operating in the context of the local device manager, disconnect our devices
        if (TextUtils.equals(pluginId, AylaLocalDeviceManager.PLUGIN_ID_LOCAL_DEVICE)) {
            // Disconnect local connections
            AylaDeviceManager dm = _deviceManager.get();

            if (dm == null) {
                AylaLog.d(LOG_TAG, "onPause: No device manager available");
                return;
            }

            dm.removeListener(this);
            _deviceManager = null;

            // Go through the device list and disconnect any local devices
            List<AylaDevice> devices = dm.getDevices();
            for (AylaDevice d : devices) {
                if (d instanceof AylaBLEDevice) {
                    AylaBLEDevice bleDevice = (AylaBLEDevice) d;
                    EmptyListener<AylaAPIRequest.EmptyResponse> emptyListener = new EmptyListener<>();
                    AylaLog.d(LOG_TAG, "Disconnecting BLE device " + bleDevice);
                    bleDevice.disconnectLocal(emptyListener, emptyListener);
                }
            }
        }
    }

    @Override
    public void onResume(String pluginId, AylaSessionManager sessionManager) {
        AylaLog.d(LOG_TAG, "AylaBLEDeviceManager: onResume");
        // When operating in the context of the local device manager, re-connect our devices
        if (TextUtils.equals(pluginId, AylaLocalDeviceManager.PLUGIN_ID_LOCAL_DEVICE)) {
            _deviceManager = new WeakReference<>(sessionManager.getDeviceManager ());
            _deviceManager.get().addListener(this);
            updateLocalDevices();
        }
    }

    @Override
    public void shutDown(String pluginId, AylaSessionManager sessionManager) {
        AylaLog.d(LOG_TAG, "AylaBLEDeviceManager: shutDown");
        onPause(pluginId, sessionManager);
    }


    public BluetoothDevice getRemoteDevice(String hardware_address) {
        try {
            return _bluetoothAdapter.getRemoteDevice(hardware_address);
        } catch (IllegalArgumentException e) {
            AylaLog.e(LOG_TAG, "IllegalArgumentException for getRemoteDevice" +
                    e.getLocalizedMessage());
        }
        return null;
    }
}
