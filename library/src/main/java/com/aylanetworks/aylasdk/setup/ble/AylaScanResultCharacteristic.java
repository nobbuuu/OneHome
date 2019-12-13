package com.aylanetworks.aylasdk.setup.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;

import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.setup.AylaSetup;
import com.aylanetworks.aylasdk.setup.AylaWifiScanResults;
import com.aylanetworks.aylasdk.setup.ble.listeners.OnScanResultChangedListener;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Scan result characteristic holds a single scan result. A scan list may be
 * obtained in two ways.
 * <ol>
 *     <li>Reading this characteristic value iteratively will provide the next scan
 *     result in the list</li>
 *     <li>Enabling notifications on this characteristic will provide the entire
 *     list of scan results one at a time in the form of notifications.</li>
 * </ol>
 *
 * This characteristic can be read or written. If written to with a specific scan index,
 * the next scan result read will be for that specific scan index. Subsequent reads will
 * provide the next scan result in the list.
 * Alternatively, this characteristic may never be written to if notifications are enabled.
 * Just requesting a scan will produce a list of scan results in the form of notifications.
 * This is the preferred method of operation.
 *
 * <pre>
 *
 * The structure definition of the characteristic that represents a scan result.
 *
 * Field    Bytes    Format    Description
 * ------------------------------------------------------------------
 * Index    1        uint8     Index of the scan result in the list
 * ------------------------------------------------------------------
 * SSID     32       uint8     array Discovered SSID
 * ------------------------------------------------------------------
 * SSID_len 1        uint8     Length of the SSID
 * ------------------------------------------------------------------
 * BSSID    6        uint8     array Discovered BSSID
 * ------------------------------------------------------------------
 * RSSI     2        sint16    Signal strength
 * ------------------------------------------------------------------
 * Security 1        uint8     0x00: Open
 *                             0x01: WEP
 *                             0x02: WPA
 *                             0x03: WPA2-Personal
 * ------------------------------------------------------------------
 * </pre>
 */
public final class AylaScanResultCharacteristic extends AylaBaseGattCharacteristic {

    private static final String TAG = "AylaScanResultCharacteristic";

    public static final UUID CHAR_UUID = UUID.fromString("1F80AF6E-2B71-4E35-94E5-00F854D8F16F");

    private final static char[] HEX_CHARACTERS_ARRAY = "0123456789ABCDEF".toCharArray();

    private int _index;
    private byte[] _ssid;
    private int _ssidLen;
    private byte[] _bssid;
    private int _rssi;
    private AylaSetup.WifiSecurityType _securityType;

    private OnScanResultChangedListener _onScanResultChangedListener;
    private List<AylaWifiScanResults.Result> _scanResultsList = new ArrayList<>();

    public void setOnScanResultChangedListener(OnScanResultChangedListener listener) {
        _onScanResultChangedListener = listener;
    }

    public OnScanResultChangedListener getOnScanResultChangedListener() {
        return _onScanResultChangedListener;
    }

    public AylaScanResultCharacteristic(BluetoothGattCharacteristic characteristic) {
        super(characteristic);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        // Call super method to ensure shouldHandleRead() method gets called.
        super.onCharacteristicChanged(gatt, characteristic);
        notifyScanResult(getScanResult());
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (super.onCharacteristicRead(gatt, characteristic, status)) {
            notifyScanResult(getScanResult());
        }
        return true;
    }

    private void notifyScanResult(AylaWifiScanResults.Result result) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (getOnScanResultChangedListener() == null) {
                    return;
                }

                if (result.ssid != null && !result.ssid.trim().isEmpty()) {
                    _scanResultsList.add(result);
                    getOnScanResultChangedListener().onScanResultAvailable(result);
                } else if (_scanResultsList.size() > 0) {
                    int length = _scanResultsList.size();
                    AylaWifiScanResults scanResults = new AylaWifiScanResults();
                    scanResults.results = _scanResultsList.toArray(new AylaWifiScanResults.Result[length]);
                    _scanResultsList.clear();
                    getOnScanResultChangedListener().onScanResultsAvailable(scanResults);
                } else {
                    AylaError error = new InternalError("unexpected end of scan results");
                    getOnScanResultChangedListener().onScanResultError(error);
                    return;
                }
            }
        });
    }

    @Override
    protected boolean shouldHandleRead() {
        byte[] values = getValue();
        int offset = 0;
        int len = 1;
        _index = values[offset];

        offset += len;
        len = 32;
        _ssid = Arrays.copyOfRange(values, offset, offset + len);

        offset += len;
        len = 1;
        _ssidLen = values[offset];

        offset += len;
        len = 6;
        _bssid = Arrays.copyOfRange(values, offset, offset + len);

        offset += len;
        len = 2;
        byte[] rssi = Arrays.copyOfRange(values, offset, offset + len);
        _rssi = (short) (rssi[0] << 8 | rssi[1] & 0xFF);

        offset += len;
        int securityType = values[offset];
        switch (securityType) {
            case 0:
                _securityType = AylaSetup.WifiSecurityType.NONE;
                break;
            case 1:
                _securityType = AylaSetup.WifiSecurityType.WEP;
                break;
            case 2:
                _securityType = AylaSetup.WifiSecurityType.WPA;
                break;
            case 3:
                _securityType = AylaSetup.WifiSecurityType.WPA2;
                break;
        }

        AylaLog.i(TAG, "scanIndex = " + getIndex() +
                ", ssid=" + getSSID() +
                ", bssid=" + getBSSID() +
                ", rssi=" + getRSSI() +
                ", security=" + getSecurityType());

        return true;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_CHARACTERS_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHARACTERS_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public int getIndex() {
        return _index;
    }

    public String getSSID() {
        return new String(getSSIDBytes(), Charset.forName("UTF-8"));
    }

    public byte[] getSSIDBytes() {
        return Arrays.copyOfRange(_ssid, 0, getSSIDLength());
    }

    public byte[] getBSSIDBytes() {
        return _bssid;
    }

    public int getSSIDLength() {
        return _ssidLen;
    }

    public String getBSSID() {
        return bytesToHex(_bssid);
    }

    public int getRSSI() {
        return _rssi;
    }

    public AylaSetup.WifiSecurityType getSecurityType() {
        return _securityType;
    }

    public AylaWifiScanResults.Result getScanResult() {
        AylaWifiScanResults.Result scanResult = new AylaWifiScanResults.Result();
        scanResult.ssid = getSSID();
        scanResult.bssid = getBSSID();
        scanResult.signal = getRSSI();
        scanResult.security = getSecurityType() == null ? null : getSecurityType().stringValue();
        return scanResult;
    }

}
