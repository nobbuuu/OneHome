package com.aylanetworks.aylasdk.localdevice.ble;

/*
 * Aura_Android
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */


import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.change.FieldChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.util.DateUtils;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.aylanetworks.aylasdk.localdevice.AylaLocalDevice;
import com.aylanetworks.aylasdk.localdevice.AylaLocalDeviceManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An AylaBLEDevice represents an AylaLocalDevice that is communicated with via Bluetooth LE.
 * AylaBLEDevices expect the BLE device to support the Ayla GATT Service, which is structured to
 * provide information relevant to the Ayla service.
 *
 * This class may be used as-is with devices that support the Ayla GATT service.
 */
@SuppressWarnings("WeakerAccess, unused")
public class AylaBLEDevice extends AylaLocalDevice {
    private final static String LOG_TAG = "BLEDevice";

    // Used to track how many times we fail so we don't get stuck forever re-trying
    private int _errorCount;
    private static int MAX_ERROR_RETRIES = 3;

    private String _deviceName;

    // UUIDs for the Ayla GATT Service and characteristics
    public static final UUID SERVICE_AYLA_BLE = UUID.fromString
            ("0000FE28-0000-1000-8000-00805F9B34FB");

    public static final UUID GATT_CHAR_DUID = UUID.fromString
            ("00000001-FE28-435B-991A-F1B21BB9BCD0");
    public static final UUID GATT_CHAR_OEM_ID = UUID.fromString
            ("00000002-FE28-435B-991A-F1B21BB9BCD0");
    public static final UUID GATT_CHAR_OEM_MODEL = UUID.fromString
            ("00000003-FE28-435B-991A-F1B21BB9BCD0");
    public static final UUID GATT_CHAR_TEMPLATE_VERSION = UUID.fromString
            ("00000004-FE28-435B-991A-F1B21BB9BCD0");
    public static final UUID GATT_CHAR_IDENTIFY = UUID.fromString
            ("00000005-FE28-435B-991A-F1B21BB9BCD0");
    public static final UUID GATT_CHAR_DISPLAY_NAME = UUID.fromString
            ("00000006-FE28-435B-991A-F1B21BB9BCD0");

    public static final String DEFAULT_MODEL = "AY001BT01";
    public static final String DEFAULT_OEM_MODEL = "OEM-AYLABT";

    private static final String PREFS_ADDRESS_MAPPING = "BLEAddrMap";
    private static final String PREFS_ADDR_PREFIX = "HWAddr-";

    protected BluetoothDevice _bluetoothDevice;
    protected BluetoothGatt _bluetoothGatt;
    protected BluetoothGattService _aylaGattService;

    protected String _oemHostVersion;

    protected List<BluetoothGattCharacteristic> _characteristicsToFetch;

    // List of listeners waiting for a connect event
    protected final List<EmptyListener> _pendingConnectListeners = new ArrayList<>();

    // Maps of characteristics to read / write requests
    protected final Map<BluetoothGattCharacteristic, AylaBLERequest> _pendingWriteRequests =
            new HashMap<>();

    protected final Map<BluetoothGattCharacteristic, AylaBLERequest> _pendingReadRequests =
            new HashMap<>();

    protected boolean _isConnected = false;

    // Runnable to be run after bonding state changes
    protected Runnable _postBondingRunnable;

    private final BroadcastReceiver _pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR);
                 final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                         BluetoothDevice.ERROR);

                 if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                     onBondChanged(true);
                 } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                     onBondChanged(false);
                 }
            }
        }
    };

    // Bluetooth standard descriptor for enabling notifications for characteristics
    public static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR = UUID.fromString
            ("00002902-0000-1000-8000-00805f9b34fb");

    public AylaBLEDevice(BluetoothDevice discoveredDevice, int rssi, byte[] scanData) {
        super();
        AylaLog.d(LOG_TAG, "Constructor: discoveredDevice: " + discoveredDevice.getName() + " rssi:" +
                rssi +
        " scanData: " + (scanData == null ? "absent" : "present"));
        _bluetoothDevice = discoveredDevice;
        ParcelUuid uuids[] = discoveredDevice.getUuids();
        if (uuids == null) {
            AylaLog.d(LOG_TAG, "No UUIDs");
        } else {
            for (ParcelUuid uuid : uuids) {
                AylaLog.d(LOG_TAG, "uuid: " + uuid);
            }
        }
    }

    /**
     * Default constructor. It is required for derived classes to override this and call super(),
     * otherwise devices created will not be properly initialized and will fail to function
     * properly.
     */
    public AylaBLEDevice() {
        super();
    }

    public void initializeBluetooth() {
        if (_bluetoothGatt == null) {
            if (ContextCompat.checkSelfPermission(AylaNetworks.sharedInstance().getContext(),
                    Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                AylaLog.e(LOG_TAG, "Bluetooth permission not yet granted- cannot connect to local" +
                        " device");
                return;
            }

            AylaBLEDeviceManager blem = null;
            try {
                blem = (AylaBLEDeviceManager) AylaNetworks.sharedInstance()
                        .getPlugin(AylaLocalDeviceManager.PLUGIN_ID_LOCAL_DEVICE);
            } catch (ClassCastException ex) {
                AylaLog.e(LOG_TAG, ex.getMessage());
            }

            if (blem != null && _bluetoothDevice == null && getBluetoothAddress() != null) {
                _bluetoothDevice = blem.getRemoteDevice(getBluetoothAddress());
                EmptyListener<AylaAPIRequest.EmptyResponse> el = new EmptyListener<>();
                connectLocal(el, el);
            }
        }
    }

    public boolean requiresLocalConfiguration() {
        // We require local configuration if we do not have an entry for the local device's
        // hardware address in our preferences. This mapping is used to map the unique_hardware_id
        // (which can be useless to us if the device was registered on iOS where BD_ADDRs are not
        // available) to the BD_ADDR that we need to connect to the device.
        SharedPreferences prefs = AylaNetworks.sharedInstance().getContext()
                .getSharedPreferences(PREFS_ADDRESS_MAPPING, Context.MODE_PRIVATE);
        return !prefs.contains(PREFS_ADDR_PREFIX + getHardwareAddress());
    }

    /**
     * When the actual Bluetooth address for this device has been discovered, the application
     * should call this method to store the mapping. This will ensure that in future runs of the
     * application the bluetooth mappings will be available without requiring a scan.
     *
     * Passing null for the bluetoothAddress will remove any previous mapping for this device.
     *
     * @param bluetoothAddress The BD_ADDR for this device
     */
    public void mapBluetoothAddress(String bluetoothAddress) {
        SharedPreferences.Editor ed = AylaNetworks.sharedInstance().getContext()
                .getSharedPreferences(PREFS_ADDRESS_MAPPING, Context.MODE_PRIVATE).edit();
        if (bluetoothAddress == null) {
            ed.remove(PREFS_ADDR_PREFIX + getHardwareAddress());
        } else {
            ed.putString(PREFS_ADDR_PREFIX + getHardwareAddress(), bluetoothAddress);
        }
        ed.apply();

        initializeBluetooth();
    }

    /**
     * Returns the Bluetooth address (BD_ADDR) of this device, or null if not known
     * @return the Bluetooth address of this device, or null
     */
    public String getBluetoothAddress() {
        if (_bluetoothDevice != null && _bluetoothDevice.getAddress() != null) {
            return _bluetoothDevice.getAddress();
        }

        if (unique_hardware_id == null) {
            return null;
        }

        // Look up the hardware address in our shared preferences
        SharedPreferences prefs = AylaNetworks.sharedInstance().getContext()
                .getSharedPreferences(PREFS_ADDRESS_MAPPING, Context.MODE_PRIVATE);
        String savedAddress = prefs.getString(PREFS_ADDR_PREFIX + unique_hardware_id, null);
        if (savedAddress != null) {
            return savedAddress;
        }

        // No mapping made yet.
        return null;
    }

    protected void fetchCharacteristics() {
       _characteristicsToFetch = getCharacteristicsToFetch();
        fetchNextCharacteristic();
    }

    protected void fetchNextCharacteristic() {
        if (_characteristicsToFetch == null || _characteristicsToFetch.size() == 0) {
            AylaLog.d(LOG_TAG, "Finished fetching characteristics");
            onCharacteristicsFetched();
            return;
        }

        BluetoothGattCharacteristic characteristic = _characteristicsToFetch.remove(0);
        if (!_bluetoothGatt.readCharacteristic(characteristic)) {
            AylaLog.e(LOG_TAG, "Failed to read characteristic: " + characteristic.getUuid());
            fetchNextCharacteristic();
        }
    }

    protected AylaAPIRequest writeCharacteristic(BluetoothGattCharacteristic characteristic,
                                                 Response.Listener<BluetoothGattCharacteristic> successListener,
                                                 final ErrorListener errorListener) {
        if (_bluetoothGatt == null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    errorListener.onErrorResponse(new PreconditionError("Bluetooth is not connected"));
                }
            });
            return null;
        }

        // Create a request and save it
        AylaBLERequest request = new AylaBLERequest(characteristic,
                 getSessionManager(), successListener, errorListener);
        synchronized (_pendingWriteRequests) {
            _pendingWriteRequests.put(characteristic, request);
        }

        _bluetoothGatt.writeCharacteristic(characteristic);

        return request;
    }

    @SuppressWarnings("unused")
    protected AylaAPIRequest readCharacteristic(BluetoothGattCharacteristic characteristic,
                                                Response.Listener<BluetoothGattCharacteristic>
                                                        successListener,
                                                final ErrorListener errorListener) {
        if (_bluetoothGatt == null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    errorListener.onErrorResponse(new PreconditionError("Bluetooth is not connected"));
                }
            });
            return null;
        }

        // Create a request and save it
        AylaBLERequest request = new AylaBLERequest(characteristic,
                getSessionManager(), successListener, errorListener);
        synchronized (_pendingReadRequests) {
            _pendingReadRequests.put(characteristic, request);
        }

        _bluetoothGatt.readCharacteristic(characteristic);

        return request;
    }

    /**
     * Called when the set of characteristics has been fetched
     */
    protected void onCharacteristicsFetched() {
        notifyConnectionSuccess();
        checkQueuedCommands();
    }

    /**
     * Notify anybody waiting for connectLocal() that we're done connecting
     * without error.
     */
    private void notifyConnectionSuccess() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (EmptyListener listener : _pendingConnectListeners) {
                    listener.onResponse(new AylaAPIRequest.EmptyResponse());
                }
                _pendingConnectListeners.clear();
            }
        });
    }

    /**
     * Notify anybody waiting for connectLocal() that the connection was not established.
     * @param error an error related with the failure, if any.
     */
    private void notifyConnectionFailure(AylaError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (EmptyListener listener : _pendingConnectListeners) {
                    listener.onErrorResponse(error);
                }
                _pendingConnectListeners.clear();
            }
        });
    }

    @Override
    public boolean isConnectedLocal() {
        return _isConnected;
    }

    @Override
    public AylaAPIRequest connectLocal(final Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                       ErrorListener errorListener) {
        return connectLocal(successListener, errorListener, true);
    }

    public AylaAPIRequest connectLocal(final Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                       ErrorListener errorListener,
                                       boolean autoConnect) {
        if (_isConnected) {
            AylaLog.d(LOG_TAG, getFriendlyName() + " already connected");
            if (successListener != null) {
                successListener.onResponse(new AylaAPIRequest.EmptyResponse());
            }
            return null;
        }

        // Do we have a bluetooth device yet?
        if (_bluetoothDevice != null) {
            if (_bluetoothGatt != null) {
                AylaLog.d(LOG_TAG, "already trying to connect");
                return null;
            }

            final EmptyListener<AylaAPIRequest.EmptyResponse> curConnectListener = new EmptyListener
                    <AylaAPIRequest.EmptyResponse>() {
                @Override
                public void onResponse(AylaAPIRequest.EmptyResponse response) {
                    successListener.onResponse(response);
                }

                @Override
                public void onErrorResponse(AylaError error) {
                    errorListener.onErrorResponse(error);
                }
            };
            _pendingConnectListeners.add(curConnectListener);

            Context context = AylaNetworks.sharedInstance().getContext().getApplicationContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Add the fourth parameter "transport" as BluetoothDevice.TRANSPORT_LE to
                // solve status 133 error. This API is available on platforms higher than M.
                // Check below link for more discussion about BLE status = 133 problem
                // https://github.com/NordicSemiconductor/Android-DFU-Library/issues/1
                _bluetoothGatt = _bluetoothDevice.connectGatt(context, autoConnect,
                        new AylaGattCallback(this), BluetoothDevice.TRANSPORT_LE);
            } else {
                _bluetoothGatt = _bluetoothDevice.connectGatt(context, autoConnect,
                        new AylaGattCallback(this));
            }

            // Return an AylaAPIRequest that the caller can use to cancel notification of connection
            return new AylaAPIRequest<AylaAPIRequest.EmptyResponse>(Request.Method.GET, "local", null,
                    AylaAPIRequest.EmptyResponse.class, null, successListener, errorListener) {
                @Override
                public void cancel() {
                    _pendingConnectListeners.remove(curConnectListener);
                }
            };
        }

        // Discovery needs to happen first
        errorListener.onErrorResponse(new PreconditionError("No BLE device discovered to " +
                "connect to. Did you call findLocalDevices first?"));
        return null;
    }

    @Override
    public AylaAPIRequest disconnectLocal(Response.Listener<AylaAPIRequest.EmptyResponse>
                                                      successListener,
                                          ErrorListener errorListener) {
        if (!_isConnected) {
            successListener.onResponse(new AylaAPIRequest.EmptyResponse());
            return null;
        }

        if (_bluetoothGatt == null) {
            successListener.onResponse(new AylaAPIRequest.EmptyResponse());
            return null;
        }

        _bluetoothGatt.disconnect();
        _bluetoothGatt = null;
        _isConnected = false;
        successListener.onResponse(new AylaAPIRequest.EmptyResponse());
        return null;
    }

    //////////////////////////////////////////////////////////////////////////////////
    // Required overrides of AylaLocalDevice

    @Override
    public ConnectionStatus getConnectionStatus() {
        return isConnectedLocal() ? ConnectionStatus.Online : ConnectionStatus.Offline;
    }

    @Override
    public boolean isLanEnabled() {
        return false;
    }

    @Override
    public boolean isLanModeActive() {
        return isConnectedLocal();
    }

    @Override
    public String toString() {
        return "Ayla Bluetooth Device [" + unique_hardware_id + "]";
    }

    /**
     * Returns a list of BluetoothGattCharacteristics that should be fetched. Derived classes
     * should override this method to return the list of characteristics required for normal
     * operation of the device.
     *
     * @return a list of BluetoothGattCharacteristics that should be fetched
     */
    protected List<BluetoothGattCharacteristic> getCharacteristicsToFetch() {
        List<BluetoothGattCharacteristic> characteristicsToFetch = new ArrayList<>();
        BluetoothGattCharacteristic characteristic;
        if (unique_hardware_id ==  null) {
            characteristic = _aylaGattService.getCharacteristic(GATT_CHAR_DUID);
            characteristicsToFetch.add(characteristic);
        }

        if (oem == null) {
            characteristic = _aylaGattService.getCharacteristic(GATT_CHAR_OEM_ID);
            characteristicsToFetch.add(characteristic);
        }

        if (oemModel == null) {
            characteristic = _aylaGattService.getCharacteristic
                    (GATT_CHAR_OEM_MODEL);
            characteristicsToFetch.add(characteristic);
        }

        if (_oemHostVersion == null) {
            characteristic = _aylaGattService.getCharacteristic(GATT_CHAR_TEMPLATE_VERSION);
            characteristicsToFetch.add(characteristic);
        }

        if (_deviceName == null) {
            characteristic = _aylaGattService.getCharacteristic(GATT_CHAR_DISPLAY_NAME);
            characteristicsToFetch.add(characteristic);
        }

        return characteristicsToFetch;
    }


    ////////////////////////////////////////////////////////////////////////////////////
    // BluetoothGattCallback methods. We cannot inherit from both BluetoothGattCallback
    // and AylaDevice, so we have a helper class that calls us back with the same methods
    // as BluetoothGattCallback

    public static final String BTCB_TAG = "GattCallback";

    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        AylaLog.d(LOG_TAG, "Connection state change [" + getFriendlyName() + "], newState: " + newState);
        boolean wasConnected = _isConnected;
        _isConnected = (newState == BluetoothGatt.STATE_CONNECTED);
        Set<String> changedFields = new HashSet<>();
        if (_isConnected != wasConnected) {
            changedFields.add("connectionStatus");
        }

        AylaLog.d(BTCB_TAG, "onConnectionStateChange: " + status + " connected: " + _isConnected);
        if (_isConnected && !wasConnected) {
            List<BluetoothGattService> services = _bluetoothGatt.getServices();

            AylaLog.d(BTCB_TAG, "bluetoothGatt has " + services.size() + " services:");
            for (BluetoothGattService svc : services) {
                AylaLog.d(BTCB_TAG, svc.getUuid().toString());
            }
            AylaLog.d(BTCB_TAG, "Discovering more services...");
            _bluetoothGatt.discoverServices();

            // Set the connected at field
            connectedAt = DateUtils.getISO8601DateFormat().format(new Date());
            changedFields.add("connectedAt");
        } else if (!_isConnected){
            notifyConnectionFailure(new PreconditionError("failed to connect with status " + status));
            gatt.close();
            _bluetoothGatt = null;
        }

        if (changedFields.size() > 0) {
            FieldChange fc = new FieldChange(changedFields);
            notifyDeviceChanged(fc, DataSource.LOCAL);
        }
    }

    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        AylaLog.d(BTCB_TAG, "onServicesDiscovered: " + status);
        List<BluetoothGattService> services = _bluetoothGatt.getServices();
        AylaLog.d(BTCB_TAG, "bluetoothGatt has " + services.size() + " services:");
        for (BluetoothGattService svc : services) {
            AylaLog.d(BTCB_TAG, svc.getUuid().toString());
        }

        _aylaGattService = _bluetoothGatt.getService(SERVICE_AYLA_BLE);
        if (_aylaGattService == null) {
            AylaLog.e(BTCB_TAG, "Could not get the Ayla GATT service!");
        } else {
            AylaLog.d(BTCB_TAG, "Reading device characteristics...");
            fetchCharacteristics();
        }
    }

    public void onCharacteristicRead(BluetoothGatt gatt,
                                     final BluetoothGattCharacteristic characteristic,
                                     final int status) {
        AylaLog.d(BTCB_TAG, "onCharacteristicRead: " + characteristic.getUuid() + " status: " + status);
        if (status == 137 || status == 135 || status == 133) {
            // This is undocumented. Seems that we should re-try the read.
            if (++_errorCount > MAX_ERROR_RETRIES) {
                AylaLog.e(LOG_TAG, "Not retrying status " + status + " - too many retries");
            } else {
                _bluetoothGatt.readCharacteristic(characteristic);
                return;
            }
        }

        _errorCount = 0;

        // Check to see if we failed because we are not paired
        if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
            // We need to pair with this device
            AylaLog.d(LOG_TAG, "Pairing required to read " + characteristic.getUuid() + "... " +
                    "pairing...");

            // Register a receiver to get updated when bonding is complete
            AylaNetworks.sharedInstance().getContext().registerReceiver(_pairingReceiver,
                    new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

            // Add an action to perform when the bonding changes
            _postBondingRunnable = new Runnable() {
                @Override
                public void run() {
                    _bluetoothGatt.readCharacteristic(characteristic);
                }
            };
            _bluetoothDevice.createBond();
            return;
        }

        // See if we have pending read requests and notify listeners
        AylaBLERequest request;
        synchronized (_pendingReadRequests) {
            // Find the original read request
            request = _pendingReadRequests.remove(characteristic);
        }

        if (request != null) {
            final AylaBLERequest finalRequest = request;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        finalRequest.reportBLEResponse();
                    } else {
                        finalRequest.reportBLEError(status, "Write characteristic failed: " + status);
                    }
                }
            });
        }

        UUID uuid = characteristic.getUuid();
        if (status != BluetoothGatt.GATT_SUCCESS) {
            AylaLog.e(LOG_TAG, "Characteristic read returned " + status + " for " + uuid);
            fetchNextCharacteristic();
            return;
        }

        if (GATT_CHAR_DUID.equals(uuid)) {
            unique_hardware_id = new String(characteristic.getValue());
        } else if (GATT_CHAR_OEM_ID.equals(uuid)) {
            oem = new String(characteristic.getValue());
        } else if (GATT_CHAR_DISPLAY_NAME.equals(uuid)) {
            _deviceName = new String(characteristic.getValue());
        } else if (GATT_CHAR_OEM_MODEL.equals(uuid)) {
            oemModel = new String(characteristic.getValue());
        } else if (GATT_CHAR_TEMPLATE_VERSION.equals(uuid)) {
            _oemHostVersion = new String(characteristic.getValue());
        }
        fetchNextCharacteristic();
    }

    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      final BluetoothGattCharacteristic characteristic,
                                      final int status) {
        AylaBLERequest request;
        synchronized (_pendingWriteRequests) {
            // Find the original write request
            request = _pendingWriteRequests.remove(characteristic);
        }

        if (request == null) {
            AylaLog.e(LOG_TAG, "Write response without a request: " + characteristic);
            return;
        }

        final AylaBLERequest finalRequest = request;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    finalRequest.getSuccessListener().onResponse(characteristic);
                } else {
                    finalRequest.reportBLEError(status, "Write characteristic failed: " + status);
                }
            }
        });
    }

    @Override
    public AylaAPIRequest updateProductName(final String displayName,
                                            final Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                            final ErrorListener errorListener) {
        // If we don't have an Ayla GATT service, just update on the cloud
        if (_aylaGattService == null) {
            return super.updateProductName(displayName, successListener, errorListener);
        }

        // Create a dummy request to return to the caller so they can cancel
        @SuppressWarnings("unchecked")
        final AylaAPIRequest request = new AylaAPIRequest(Request.Method.PUT, "local", null,
                AylaAPIRequest.EmptyResponse.class, null,
                successListener, errorListener);

        // First update the Bluetooth name, if we're connected
        if (isConnectedLocal()) {
            BluetoothGattCharacteristic nameChar = _aylaGattService.getCharacteristic
                    (GATT_CHAR_DISPLAY_NAME);
            nameChar.setValue(displayName);
            nameChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            writeCharacteristic(nameChar, new Response.Listener<BluetoothGattCharacteristic>() {
                @Override
                public void onResponse(BluetoothGattCharacteristic response) {
                    AylaAPIRequest innerRequest = AylaBLEDevice.super.updateProductName
                            (displayName, successListener, errorListener);
                    if (innerRequest != null && !request.isCanceled()) {
                        request.setChainedRequest(innerRequest);
                    }
                }
            }, new ErrorListener() {
                @Override
                public void onErrorResponse(AylaError error) {
                    AylaLog.e(LOG_TAG, "Could not update local device name");
                    // We still want to update on the cloud though
                    AylaAPIRequest innerRequest = AylaBLEDevice.super.updateProductName
                            (displayName, successListener, errorListener);
                    if (innerRequest != null && !request.isCanceled()) {
                        request.setChainedRequest(innerRequest);
                    }
                }
            });
        }

        return request;
    }

    public void onBondChanged(boolean bonded) {
        AylaLog.i(LOG_TAG, "onBondChanged: " + bonded);
        AylaNetworks.sharedInstance().getContext().unregisterReceiver(_pairingReceiver);
        if (_postBondingRunnable != null) {
            _postBondingRunnable.run();
            _postBondingRunnable = null;
        }
    }

    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        AylaLog.d(BTCB_TAG, "onCharacteristicChanged: " + characteristic);
    }


    @SuppressWarnings("unused")
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        AylaLog.d(BTCB_TAG, "onDescriptorRead: " + descriptor + " status: " + status);
    }

    @SuppressWarnings("unused")
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        AylaLog.d(BTCB_TAG, "onDescriptorWrite: " + descriptor + " status: " + status);
    }


    @SuppressWarnings("unused")
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        AylaLog.d(BTCB_TAG, "onReliableWriteCompleted: " + status);
    }


    @SuppressWarnings("unused")
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        AylaLog.d(BTCB_TAG, "onReadRemoteRssi: " + rssi + " status: " + status);
    }


    @SuppressWarnings("unused")
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        AylaLog.d(BTCB_TAG, "onMtuChanged: " + mtu + " status: " + status);
    }

    @Override
    public String getFriendlyName() {
        String response = getProductName();
        if (!TextUtils.isEmpty(response)) {
            return response;
        }

        response = getDeviceName();
        if (!TextUtils.isEmpty(response)) {
            return response;
        }

        return getDsn();
    }

    @Override
    public String getDeviceName() {
        if (!TextUtils.isEmpty(_deviceName)) {
            return _deviceName;
        } else {
            return _bluetoothDevice != null ? _bluetoothDevice.getName() : null;
        }
    }

}
