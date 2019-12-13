package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.NonNull;

import com.aylanetworks.aylasdk.setup.AylaSetup;

import java.util.UUID;

/**
 * Connect characteristic and the corresponding parameters needed to connect
 * to a specified network.
 *
 * Structure definition of the the characteristic:
 *
 * <pre>
 *
 * Field     Bytes    Format            Description
 * ------------------------------------------------------------
 * SSID      32       uint8 array       Selected SSID
 * ------------------------------------------------------------
 * SSID_len  1        uint8             Length of SSID
 * ------------------------------------------------------------
 * BSSID     6        uint8 array       Selected BSSID
 * ------------------------------------------------------------
 * Key       64       uint8 array       Passphrase
 * ------------------------------------------------------------
 * Key_len   1        uint8             Length of passphrase
 * ------------------------------------------------------------
 * Security  1        uint8             0x00: open
 *                                      0x01: WEP
 *                                      0x02: WPA
 *                                      0x03: WPA2-Personal
 * ------------------------------------------------------------
 * </pre>
 */
public final class AylaConnectCharacteristic extends AylaBaseGattCharacteristic {

    private static final String TAG = "AylaConnectCharacteristic";

    public static final UUID CHAR_UUID = UUID.fromString("1F80AF6A-2B71-4E35-94E5-00F854D8F16F");

    private String _ssid;
    private String _password;
    private AylaSetup.WifiSecurityType _securityType;

    public AylaConnectCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    @Override
    public String getName() {
        return TAG;
    }

    public AylaConnectCharacteristic(@NonNull BluetoothGattCharacteristic characteristic,
                                     @NonNull String ssid,
                                     @NonNull String password,
                                     @NonNull AylaSetup.WifiSecurityType securityType) {
        super(characteristic);
        this._securityType = securityType;
        this._ssid = ssid;
        this._password = password;
    }

    public AylaConnectCharacteristic setSSID(String ssid) {
        this._ssid = ssid;
        return this;
    }

    public AylaConnectCharacteristic setPassword(String password) {
        this._password = password;
        return this;
    }

    public AylaConnectCharacteristic setSecurityType(AylaSetup.WifiSecurityType type) {
        this._securityType = type;
        return this;
    }

    @Override
    protected boolean shouldHandleWrite() {
        byte[] ssidBytes = _ssid.getBytes();
        byte[] passwordBytes = (_password == null) ? new byte[]{} : _password.getBytes();
        int securityTypeInt = 3;
        switch (_securityType) {
            case NONE:
                securityTypeInt = 0;
                break;
            case WEP:
                securityTypeInt = 1;
                break;
            case WPA:
                securityTypeInt = 2;
                break;
            case WPA2:
                securityTypeInt = 3;
                break;
        }

        byte[] connectBytes = new byte[105];
        connectBytes[32] = (byte) _ssid.length();
        connectBytes[103] = (byte) _password.length();
        connectBytes[104] = (byte) securityTypeInt;
        System.arraycopy(ssidBytes, 0, connectBytes, 0, ssidBytes.length);
        System.arraycopy(passwordBytes, 0, connectBytes, 39, passwordBytes.length);

        return getOwner().setValue(connectBytes);
    }
}
