package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.aylanetworks.aylasdk.setup.AylaWifiStatus;
import com.aylanetworks.aylasdk.setup.ble.listeners.OnConnectStatusChangedListener;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

/**
 * Status characteristic holds the status and result of the Wi-Fi join
 * request, initialized from a connect request as defined in {@link AylaConnectCharacteristic}
 * <pre>
 *
 * The structure definition of the characteristic that represents a scan result.
 *
 * Field      Bytes    Format          Description
 * ------------------------------------------------------
 * SSID       32       uint8 array     SSID
 * ------------------------------------------------------
 * SSID_len   1        uint8           Length of SSID
 * ------------------------------------------------------
 * Error      1        uint8           Error Code
 * ------------------------------------------------------
 * State      1        uint8           Current Wi-Fi state
 *                                     0x0: N/A
 *                                     0x1: Disabled
 *                                     0x2: Connecting to Wi-Fi
 *                                     0x3: Connecting to Network (Obtaining IP address)
 *                                     0x4: Connecting to the Cloud
 *                                     0x5: Up/Connected
 * -------------------------------------------------------
 *
 * Note: Error code corresponds to Wi-Fi error events from Ayla
 * Module and MCU Interface Specification.
 * </pre>
 */
public final class AylaConnectStatusCharacteristic extends AylaBaseGattCharacteristic {

    private static final String TAG = "AylaConnectStatusCharacteristic";

    public static final UUID CHAR_UUID = UUID.fromString("1F80AF6C-2B71-4E35-94E5-00F854D8F16F");

    private byte[] _ssid;
    private int _ssidLen;
    private AylaWifiStatus.HistoryItem.Error _error;
    private State _state;

    private OnConnectStatusChangedListener _onConnectStatusChangedListener;

    public void setOnConnectStatusChangedListener(OnConnectStatusChangedListener listener) {
        _onConnectStatusChangedListener = listener;
    }

    public OnConnectStatusChangedListener getOnConnectStatusChangedListener() {
        return _onConnectStatusChangedListener;
    }

    public AylaConnectStatusCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        // Call super method to ensure shouldHandleRead() method gets called.
        super.onCharacteristicChanged(gatt, characteristic);
        notifyConnectionStatus();
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (super.onCharacteristicRead(gatt, characteristic, status)) {
            notifyConnectionStatus();
        }
        return true;
    }

    private void notifyConnectionStatus() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (getOnConnectStatusChangedListener() == null) {
                    return;
                }

                AylaWifiStatus.HistoryItem.Error error = getError();
                State state = getState();
                switch (error) {
                    case NoError:
                    case InProgress:
                        getOnConnectStatusChangedListener().onConnectionStateChanged(state);
                        if (state == State.CONNECTED) {
                            getOnConnectStatusChangedListener().onConnected(getSSID());
                        }
                        break;
                    default:
                        getOnConnectStatusChangedListener().onConnectionError(getSSID(), error);
                }
            }
        });
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    protected boolean shouldHandleRead() {
        byte[] values = getValue();
        int offset = 0;
        int len = 32;
        _ssid = Arrays.copyOfRange(values, offset, offset + len);

        offset += len;
        len = 1;
        _ssidLen = values[offset];

        offset += len;
        len = 1;
        int errorCode = values[offset];
        for (AylaWifiStatus.HistoryItem.Error error : AylaWifiStatus.HistoryItem.Error.values()) {
            if (error.getCode() == errorCode) {
                _error = error;
                break;
            }
        }

        offset += len;
        int stateCode = values[offset];
        for (State state : State.values()) {
            if (state.getCode() == stateCode) {
                _state = state;
                break;
            }
        }

        return true;
    }

    public String getSSID() {
        return new String(getSSIDBytes(), Charset.forName("UTF-8"));
    }

    public byte[] getSSIDBytes() {
        return Arrays.copyOfRange(_ssid, 0, getSSIDLength());
    }

    public int getSSIDLength() {
        return _ssidLen;
    }

    public State getState() {
        return _state;
    }

    public AylaWifiStatus.HistoryItem.Error getError() {
        return _error;
    }

    public enum State {
        NA(0x00, "N/A"),
        Disabled(0x01, "Disabled"),
        CONNECTING_TO_WIFI(0x02, "Connecting to Wi-Fi"),
        CONNECTING_TO_NETWORK(0x03, "Connecting to Network (Obtaining IP address)"),
        CONNECTING_TO_CLOUD(0x04, "Connecting to the Cloud"),
        CONNECTED(0x05, "Up/Connected");

        State(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        private int code;
        private String description;
    }

}
