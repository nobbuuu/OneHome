package com.aylanetworks.aylasdk.setup;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AppPermissionError;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.error.TimeoutError;
import com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDeviceManager;
import com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDeviceManager.ScanFilter;
import com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDeviceManager.ServiceScanFilter;
import com.aylanetworks.aylasdk.setup.ble.AylaBaseGattCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaConnectCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaConnectStatusCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaConnectStatusCharacteristic.State;
import com.aylanetworks.aylasdk.setup.ble.AylaScanCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaScanResultCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaSetupTokenGattCharacteristic;
import com.aylanetworks.aylasdk.setup.ble.AylaWiFiConfigGattService;
import com.aylanetworks.aylasdk.setup.ble.listeners.OnConnectStatusChangedListener;
import com.aylanetworks.aylasdk.setup.ble.listeners.OnScanResultChangedListener;
import com.aylanetworks.aylasdk.util.PermissionUtils;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.aylanetworks.aylasdk.util.URLHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Android_Aura
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

/**
 * AylaBLEWiFiSetup class is used to scan for devices that enables Wi-Fi setup via BLE, such as
 * combo modules and the Raspberry Pi. This class has public methods to scan for BLE devices
 * that support Wi-Fi Config service, connect to a selected device, and read and write BLE
 * characteristics to connect the device to Ayla cloud.
 *
 * Once a BLE device has been discovered via a call to {@link #scanDevices}, the selected device
 * can be passed to {@link #connectToBLEDevice} to connect the mobile device to the
 * BLE device. Once the mobile and device are connected through BLE, the mobile device
 * asks the setup device to do its own scan for access points via
 * {@link #scanForAccessPoints(int, Response.Listener, ErrorListener)}, the scan results will be
 * received in the callback if notification works, or alternatively be fetched by calling
 * {@link #fetchScanResults(int, long, Response.Listener, ErrorListener)}.
 *
 * If the device supports Setup Token characteristic, the app calls {@link #sendSetupToken} to
 * send a setup token to the device. This setup token will be used later in the setup process to
 * confirm that the device connected to Ayla cloud.
 * <p>
 * At this point the user may choose an access point for the device to connect to and
 * supply the app with the WiFi password. Once this is done, a call to
 * {@link #connectDeviceToAP} will start connecting the device to the selected AP.
 * <p>
 * <p>
 * In order to confirm that the device has successfully connected to the WiFi network, and
 * subsequently the Ayla service, the {@link #confirmDeviceConnected} method can be called to
 * verify that the device has connected.
 * <p>
 * Once the device has been connected to the network, it may be registered to the user's account
 * if it was not already registered.
 * <p>
 */
public class AylaBLEWiFiSetup {

    private static final String LOG_TAG = "BLE_WIFI_SETUP";

    private AylaBLEWiFiSetupDevice _setupDevice;
    private final AylaSessionManager _sessionManager;
    private final AylaBLEDeviceManager _bleDeviceManager;

    private static final int DEFAULT_SCAN_RESULTS_POLL_INTERVAL = 100;
    private static final int DEFAULT_CONNECTION_STATUS_POLL_INTERVAL = 1000;

    private static final int DEFAULT_CONFIRM_POLL_INTERVAL = 1000;
    private int _confirmPollInterval = DEFAULT_CONFIRM_POLL_INTERVAL;

    /**
     * Array of permissions required by setup. Methods will check these to make
     * sure they have been permitted before proceeding.
     */
    public static final String[] SETUP_REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
    };

    private OnScanResultChangedListener _onScanResultChangedListener;
    private OnConnectStatusChangedListener _onConnectStatusChangedListener;

    /**
     * Set OnScanResultChangedListener to receive AP scan results changed events.
     * @param listener the listener to receive AP scan results.
     */
    public void setOnScanResultChangedListener(OnScanResultChangedListener listener) {
        _onScanResultChangedListener = listener;
    }

    /**
     * Get the registered AP OnScanResultChangedListener.
     * @return the registered AP scan results changed listener, or null if
     * none was registered.
     */
    public OnScanResultChangedListener getOnScanResultChangedListener() {
        return _onScanResultChangedListener;
    }

    /**
     * Set the connection status changed listener to receive connection
     * state changed events.
     * @param listener the listener to receive connection state changed events.
     */
    public void setOnConnectStatusChangedListener(OnConnectStatusChangedListener listener) {
        _onConnectStatusChangedListener = listener;
    }

    /**
     * get the registered connection state changed listener.
     * @return the registered connection state changed listener, or null if
     * none was registered.
     */
    public OnConnectStatusChangedListener getOnConnectStatusChangedListener() {
        return _onConnectStatusChangedListener;
    }

    /**
     * Default constructor that is needed to receive the BLE WiFi setup flow.
     *
     * @param context context that is needed for permission check, etc.
     * @param sessionManager SessionManager for the current session to be passed from the app.
     * @throws PreconditionError if null parameter exists, or the required permissions for
     *                           setup are not granted.
     */
    public AylaBLEWiFiSetup(@NonNull Context context,
                            @NonNull AylaSessionManager sessionManager) throws PreconditionError {

        if (context == null || sessionManager == null) {
            throw new PreconditionError("has null parameter(s)");
        }

        AppPermissionError permissionError = PermissionUtils.checkPermissions(
                context, SETUP_REQUIRED_PERMISSIONS);
        if (permissionError != null) {
            throw new PreconditionError("needs permission "
                    + permissionError.getFailedPermission(), permissionError);
        }

        _sessionManager = sessionManager;
        _bleDeviceManager = new AylaBLEDeviceManager(context);
    }

    /**
     * Returns the instance of AylaBLEWiFiSetupDevice being set up.
     * @return Returns the BLE setup device, or null if {@link #connectToBLEDevice(
     * AylaBLEWiFiSetupDevice, Response.Listener, ErrorListener) connectToBLEDevice} was
     * never been called before with a valid device instance.
     */
    public AylaBLEWiFiSetupDevice getSetupDevice() {
        return _setupDevice;
    }

    /**
     * Get the session manager associated with this setup.
     */
    public AylaSessionManager getSessionManager() {
        return _sessionManager;
    }

    /**
     * Returns the poll interval used when polling the service to determine if a device has
     * connected to the service
     *
     * @return the poll interval in ms
     */
    public int getConfirmPollInterval() {
        return _confirmPollInterval;
    }

    /**
     * Sets the interval used to poll the device service to determine if the device has
     * successfully connected to the service after joining the WiFi network.
     *
     * @param timeInMs Time in milliseconds between requests to look for the device
     */
    public void setConfirmPollInterval(int timeInMs) {
        _confirmPollInterval = timeInMs;
    }

    /**
     * Scan for available BLE devices.
     * @param timeout Maximum time the search should take in ms.
     * @param scanFilter used to filter the set of returned devices.
     * @param successListener Listener to be notified with the results.
     * @param errorListener Listener to receive an error should one occur.
     * @return a cancelable AylaAPIRequest
     */
    public AylaAPIRequest scanDevices(int timeout,
                                      ScanFilter scanFilter,
                                      Response.Listener<AylaBLEWiFiSetupDevice[]> successListener,
                                      ErrorListener errorListener) {
        return _bleDeviceManager.findBLESetupDevices(scanFilter, timeout, successListener, errorListener);
    }

    /**
     * Scan for available BLE devices.
     * @param timeout Maximum time the search should take in ms.
     * @param successListener Listener to be notified with the results.
     * @param errorListener Listener to receive an error should one occur.
     * @return a cancelable AylaAPIRequest
     */
    public AylaAPIRequest scanDevices(int timeout,
                                      Response.Listener<AylaBLEWiFiSetupDevice[]> successListener,
                                      ErrorListener errorListener){
        UUID[] services = new UUID[] {AylaWiFiConfigGattService.SERVICE_UUID};
        ServiceScanFilter scanFilter = new ServiceScanFilter(services);
        return scanDevices(timeout, scanFilter, successListener, errorListener);
    }

    /**
     * Connect to a BLE device that is to be connected to a Wifi access point.
     *
     * @param setupDevice the AylaBLEWiFiSetupDevice that is to be connected to Wifi access point.
     * @param successListener Listener to be notified with results.
     * @param errorListener Listener to receive an error should one occur.
     * @return a cancelable AylaAPIRequest
     */
    public AylaAPIRequest connectToBLEDevice(AylaBLEWiFiSetupDevice setupDevice,
                                             Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                             ErrorListener errorListener){
        _setupDevice = setupDevice;
        return getSetupDevice().connectLocal(successListener, errorListener, false);
    }

    /**
     * Start wifi scan on the device for Wifi access points, and return the scan results
     * in the success listener.
     *
     * There are two ways to get the scan results:
     * <ol>
     *     <li>Enabling notification on the Scan Results characteristic, and receive scan
     *     result in the characteristic changed callback
     *     {@link AylaScanResultCharacteristic#onCharacteristicChanged(
     *     BluetoothGatt, BluetoothGattCharacteristic)}</li>, one scan result per callback.
     *
     *     <li>Reading, at fixed interval, the Scan Results characteristic for the scan results.</li>
     * </ol>
     *
     * The notification based scan results retrieval way is preferred, however, if the
     * notification way didn't work(for example, failed to write notification descriptor)), then
     * automatically fallback to the polling way, by calling {@link #fetchScanResults(int, long,
     * Response.Listener, ErrorListener) fetchScanResults}.
     *
     * @param timeoutInSeconds  the specified period of seconds the operation will last at most.
     * @param successListener Listener to be notified if the scan results are available.
     * @param errorListener Listener to receive the error should one occur.
     * @return a cancelable AylaAPIRequest
     */
    public AylaAPIRequest scanForAccessPoints(int timeoutInSeconds,
                                              Response.Listener<AylaWifiScanResults> successListener,
                                              ErrorListener errorListener) {
        AylaScanResultCharacteristic scanResultCharacteristic = (AylaScanResultCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaScanResultCharacteristic.CHAR_UUID);
        if (scanResultCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("scan result characteristic not available"));
            return null;
        }

        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        long targetTime = System.currentTimeMillis() + timeoutInSeconds * 1000;

        AylaAPIRequest originalRequest = AylaAPIRequest.dummyRequest(
                AylaWifiScanResults.class, successListener, errorListener);
        AylaAPIRequest enableNotificationRequest = scanResultCharacteristic.enableCharacteristicNotification(
                getSetupDevice().getBluetoothGatt(),
                true,
                new Response.Listener<AylaBaseGattCharacteristic>() {
                    @Override
                    public void onResponse(AylaBaseGattCharacteristic response) {
                        AylaLog.d(LOG_TAG, "scan results notification enabled");
                        scanResultCharacteristic.setOnScanResultChangedListener(
                                new OnScanResultChangedListener() {
                                    @Override
                                    public void onScanResultAvailable(AylaWifiScanResults.Result result) {
                                        AylaLog.i(LOG_TAG, "new scan result:" + result);
                                        if (getOnScanResultChangedListener() != null) {
                                            getOnScanResultChangedListener().onScanResultAvailable(result);
                                        }
                                    }

                                    @Override
                                    public void onScanResultsAvailable(AylaWifiScanResults results) {
                                        AylaLog.i(LOG_TAG, "got all scan result. len = " + results.results.length);
                                        timeoutHandler.removeCallbacksAndMessages(null);
                                        successListener.onResponse(results);
                                        if (getOnScanResultChangedListener() != null) {
                                            getOnScanResultChangedListener().onScanResultsAvailable(results);
                                        }
                                    }

                                    @Override
                                    public void onScanResultError(AylaError error) {
                                        timeoutHandler.removeCallbacksAndMessages(null);
                                        if (getOnScanResultChangedListener() != null) {
                                            errorListener.onErrorResponse(error);
                                        }
                                    }
                                });

                        originalRequest.setChainedRequest(startScan(new Response.Listener<AylaScanCharacteristic>() {
                            @Override
                            public void onResponse(AylaScanCharacteristic response) {
                                AylaLog.d(LOG_TAG, "AP scan started, waiting for scan results notification.");
                            }
                        }, errorListener));
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        AylaLog.e(LOG_TAG, "failed to enable scan results notification cause "
                                + error + ", fallback to polling scan results");
                        originalRequest.setChainedRequest(startScan(new Response.Listener<AylaScanCharacteristic>() {
                            @Override
                            public void onResponse(AylaScanCharacteristic response) {
                                AylaLog.d(LOG_TAG, "AP scan started, start polling for scan results");
                                originalRequest.setChainedRequest(
                                        fetchScanResults(timeoutInSeconds, DEFAULT_SCAN_RESULTS_POLL_INTERVAL,
                                                new Response.Listener<AylaWifiScanResults>() {
                                                    @Override
                                                    public void onResponse(AylaWifiScanResults response) {
                                                        timeoutHandler.removeCallbacksAndMessages(null);
                                                        successListener.onResponse(response);
                                                    }
                                                }, errorListener));
                            }
                        }, errorListener));
                    }
                }
        );

        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // disable notification in case it was enabled.
                scanResultCharacteristic.enableCharacteristicNotification(
                        getSetupDevice().getBluetoothGatt(),
                        false,
                        new Response.Listener<AylaBaseGattCharacteristic>() {
                            @Override
                            public void onResponse(AylaBaseGattCharacteristic response) {
                                AylaLog.d(LOG_TAG, "scan results notification disabled");
                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError error) {
                                AylaLog.d(LOG_TAG, "failed to disable scan results notification");
                            }
                        });
                errorListener.onErrorResponse(new TimeoutError("fetching scan results timed out"));
            }
        }, targetTime);

        originalRequest.setChainedRequest(enableNotificationRequest);
        return originalRequest;
    }

    /**
     * Start wifi scan on the device for Wifi access points.
     */
    private AylaAPIRequest startScan(Response.Listener<AylaScanCharacteristic> successListener,
                                     ErrorListener errorListener) {
        AylaScanCharacteristic scanCharacteristic = (AylaScanCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaScanCharacteristic.CHAR_UUID);
        if (scanCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("scan characteristic not available"));
            return null;
        }

        return scanCharacteristic.writeCharacteristic(getSetupDevice().getBluetoothGatt(),
                new Response.Listener<AylaBaseGattCharacteristic>() {
                    @Override
                    public void onResponse(AylaBaseGattCharacteristic response) {
                        successListener.onResponse((AylaScanCharacteristic)response);
                    }
                }, errorListener);
    }

    /**
     * Fetches scan results from the device by reading Scan Result characteristic till a null
     * SSID value is received.
     *
     * <p>Notes:</p>
     * <ol>
     *     <li>Make sure the scan request has been successfully initialized by writing 1 to
     *     GATT server before calling this method, which can be done by making a call to
     *     {@link #startScan(Response.Listener, ErrorListener)}. Otherwise, no scan
     *     results will be returned, or returned stale results, depending on the device
     *     implementation. For example, the Linux will returned the cached results for
     *     last scan.</li>
     *
     *     <li>This method is intended for polling purpose only in case characteristic
     *     notification didn't work(for example, failed to write notification descriptor).
     *     Notifications should always be the preferred way for receiving scan results.
     *     check {@link #scanForAccessPoints(int, Response.Listener, ErrorListener)} for more details.</li>
     * </ol>
     *
     * @param timeoutInSeconds  the specified period of seconds the operation will last at most.
     * @param intervalInMilliSeconds the interval between two consecutive characteristic reading.
     * @param successListener   Listener to be notified if the scan results are available.
     * @param errorListener     Listener to receive the error should one occur.
     * @return a cancelable AylaAPIRequest
     */
    private AylaAPIRequest fetchScanResults(int timeoutInSeconds,
                                            long intervalInMilliSeconds,
                                            Response.Listener<AylaWifiScanResults> successListener,
                                            ErrorListener errorListener) {
        AylaScanResultCharacteristic scanResultCharacteristic = (AylaScanResultCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaScanResultCharacteristic.CHAR_UUID);
        if (scanResultCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("scan result characteristic not available"));
            return null;
        } else {
            scanResultCharacteristic.setOnScanResultChangedListener(getOnScanResultChangedListener());
        }

        AylaAPIRequest originalRequest = AylaAPIRequest.dummyRequest(
                AylaWifiScanResults.class, successListener, errorListener);

        long targetTime = System.currentTimeMillis() + timeoutInSeconds * 1000;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            private Runnable fetchScanResultRunnable = this;
            private List<AylaWifiScanResults.Result> scanResultsList = new ArrayList<>();

            @Override
            public void run() {
                if (originalRequest.isCanceled()) {
                    scanResultCharacteristic.setOnScanResultChangedListener(null);
                    return;
                }

                if (System.currentTimeMillis() >= targetTime) {
                    scanResultCharacteristic.setOnScanResultChangedListener(null);
                    errorListener.onErrorResponse(new TimeoutError("fetching scan results timed out"));
                    return;
                }

                originalRequest.setChainedRequest(scanResultCharacteristic.readCharacteristic(
                        getSetupDevice().getBluetoothGatt(),
                        new Response.Listener<AylaBaseGattCharacteristic>() {
                            @Override
                            public void onResponse(AylaBaseGattCharacteristic response) {
                                AylaWifiScanResults.Result result = scanResultCharacteristic.getScanResult();
                                if ((result.ssid != null && !result.ssid.trim().isEmpty())) {
                                    scanResultsList.add(result);
                                    handler.postDelayed(fetchScanResultRunnable, intervalInMilliSeconds);
                                } else if (scanResultsList.size() > 0) {
                                    int size = scanResultsList.size();
                                    AylaWifiScanResults scanResults = new AylaWifiScanResults();
                                    scanResults.results = scanResultsList.toArray(new AylaWifiScanResults.Result[size]);
                                    successListener.onResponse(scanResults);
                                    scanResultsList.clear();
                                    scanResultCharacteristic.setOnScanResultChangedListener(null);
                                } else {
                                    // might get unexpected scan result here, just ignore and continue polling
                                    handler.postDelayed(fetchScanResultRunnable, intervalInMilliSeconds);
                                }
                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError error) {
                                scanResultCharacteristic.setOnScanResultChangedListener(null);
                                errorListener.onErrorResponse(error);
                            }
                        }
                ));
            }
        });

        return originalRequest;
    }


    /**
     * Sends setup token to the device by writing to Setup Token characteristic. Setup Token
     * is a characteristic in Ayla Connectivity GATT Service. The value written to Setup Token
     * characteristic will be used to confirm that the device connected to Ayla cloud.
     * @param setupToken setup token to be sent to device.
     * @param successListener Listener to be notified if request was successful.
     * @param errorListener Listener to receive an error should one occur.
     * @return camcelable AylaAPIRequest
     */
    public AylaAPIRequest sendSetupToken(String setupToken,
                                         final Response.Listener<AylaSetupTokenGattCharacteristic> successListener,
                                         final ErrorListener errorListener) {
        AylaSetupTokenGattCharacteristic setupTokenCharacteristic = (AylaSetupTokenGattCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaSetupTokenGattCharacteristic.CHAR_UUID);
        if (setupTokenCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("setup token characteristic not available"));
            return null;
        }

        setupTokenCharacteristic.setSetupToken(setupToken);

        return setupTokenCharacteristic.writeCharacteristic(
                getSetupDevice().getBluetoothGatt(),
                new Response.Listener<AylaBaseGattCharacteristic>() {
                    @Override
                    public void onResponse(AylaBaseGattCharacteristic response) {
                        successListener.onResponse((AylaSetupTokenGattCharacteristic) response);
                    }
                }, errorListener);
    }

    /**
     * Fetches connection status from the device till the device is connected or the
     * operation timed out, report the connection status if the connection is still in progress.
     *
     * Note: This method is intended for polling purpose only in case characteristic
     * notification didn't work(for example, failed to write notification descriptor).
     * Notification should always be the preferred way for receiving connection status.
     * check {@link #connectDeviceToAP(int, String, String, AylaSetup.WifiSecurityType,
     * Response.Listener, ErrorListener)} for more details.
     *
     * @param successListener Listener to receive the result of reading connection status
     *                        characteristic. The connection result can be got by either:
     *                        <ol><li>registering connection status changed listener if one was set by call
     *                        {@link #setOnConnectStatusChangedListener(OnConnectStatusChangedListener)}</li>
     *                        <li>checking the corresponding methods of AylaConnectStatusCharacteristic returned
     *                        in the success listener, by calling {@link AylaConnectStatusCharacteristic#getState()} and
     *                        {@link AylaConnectStatusCharacteristic#getError()} respectively.</li></ol>
     * @param timeoutInSeconds the specified period of seconds the operation will last at most.
     * @param intervalInMilliSeconds the interval between two consecutive characteristic reading.
     * @param errorListener Listener to receive error information should an error occur.
     * @return cancelable AylaAPIRequest
     */
    private AylaAPIRequest fetchConnectionStatus(int timeoutInSeconds,
                                                 long intervalInMilliSeconds,
                                                 Response.Listener<AylaConnectStatusCharacteristic> successListener,
                                                 ErrorListener errorListener) {
        AylaConnectStatusCharacteristic connectStatusCharacteristic = (AylaConnectStatusCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaConnectStatusCharacteristic.CHAR_UUID);
        if (connectStatusCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("connect characteristic not available"));
            return null;
        } else {
            connectStatusCharacteristic.setOnConnectStatusChangedListener(getOnConnectStatusChangedListener());
        }

        AylaAPIRequest originalRequest = AylaAPIRequest.dummyRequest(
                AylaConnectStatusCharacteristic.class, successListener, errorListener);
        long targetTime = System.currentTimeMillis() + timeoutInSeconds * 1000;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            private final Runnable fetchConnectStatusRunnable = this;

            @Override
            public void run() {
                if (originalRequest.isCanceled()) {
                    connectStatusCharacteristic.setOnConnectStatusChangedListener(null);
                    return;
                }

                if (System.currentTimeMillis() >= targetTime) {
                    connectStatusCharacteristic.setOnConnectStatusChangedListener(null);
                    errorListener.onErrorResponse(new TimeoutError("timed out polling connection status"));
                    return;
                }

                AylaAPIRequest connectStatusReadRequest = connectStatusCharacteristic.readCharacteristic(
                        getSetupDevice().getBluetoothGatt(),
                        new Response.Listener<AylaBaseGattCharacteristic>() {
                            @Override
                            public void onResponse(AylaBaseGattCharacteristic response) {
                                AylaWifiStatus.HistoryItem.Error error = connectStatusCharacteristic.getError();
                                State state = connectStatusCharacteristic.getState();
                                switch (error) {
                                    case NoError:
                                    case InProgress:
                                        if (state == State.CONNECTED) {
                                            successListener.onResponse(connectStatusCharacteristic);
                                        } else {
                                            handler.postDelayed(fetchConnectStatusRunnable, intervalInMilliSeconds);
                                        }
                                        break;

                                    default:
                                        errorListener.onErrorResponse(new PreconditionError(error.name()));
                                }
                            }
                        }, errorListener);
                originalRequest.setChainedRequest(connectStatusReadRequest);
            }
        });

        return originalRequest;
    }

    /**
     * Connects device to selected Wifi access point. Internally, it will try first to enable
     * characteristic notification to receive connection status, however, if notification
     * doesn't work(for example, failed to write notification descriptor), it will fall
     * back to polling the connection status automatically.
     *
     * @param timeoutInSeconds the specified period of seconds the connection will last at most.
     * @param ssid SSID of the access point to connect the device to.
     * @param password password for the access point.
     * @param successListener Listener to be notified with results.
     * @param errorListener Listener to receive an error should one occur.
     * @return a cancelable AylaAPIRequest
     */
    public AylaAPIRequest connectDeviceToAP(int timeoutInSeconds,
                                            String ssid,
                                            String password,
                                            AylaSetup.WifiSecurityType securityType,
                                            Response.Listener<AylaConnectStatusCharacteristic> successListener,
                                            ErrorListener errorListener) {

        AylaConnectStatusCharacteristic connectStatusCharacteristic = (AylaConnectStatusCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaConnectStatusCharacteristic.CHAR_UUID);
        if (connectStatusCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("connect status characteristic not available"));
        }

        AylaConnectCharacteristic connectCharacteristic = (AylaConnectCharacteristic)
                getSetupDevice().getManagedCharacteristic(AylaConnectCharacteristic.CHAR_UUID);
        if (connectCharacteristic == null) {
            errorListener.onErrorResponse(new PreconditionError("connect characteristic not available"));
            return null;
        }

        connectCharacteristic.setSSID(ssid);
        connectCharacteristic.setPassword(password);
        connectCharacteristic.setSecurityType(securityType);

        AylaAPIRequest originalRequest = AylaAPIRequest.dummyRequest(
                AylaConnectStatusCharacteristic.class, successListener, errorListener);

        AylaAPIRequest enableNotificationRequest = connectStatusCharacteristic.enableCharacteristicNotification(
                getSetupDevice().getBluetoothGatt(),
                true,
                new Response.Listener<AylaBaseGattCharacteristic>() {
                    @Override
                    public void onResponse(AylaBaseGattCharacteristic response) {
                        AylaLog.d(LOG_TAG, "connect status notification enabled");
                        connectStatusCharacteristic.setOnConnectStatusChangedListener(getOnConnectStatusChangedListener());
                        originalRequest.setChainedRequest(connectCharacteristic.writeCharacteristic(
                                getSetupDevice().getBluetoothGatt(),
                                new Response.Listener<AylaBaseGattCharacteristic>() {
                                    @Override
                                    public void onResponse(AylaBaseGattCharacteristic response) {
                                        AylaLog.d(LOG_TAG, "connect request sent, " +
                                                "waiting for connection status notification");
                                    }
                                }, errorListener));
                    }
                }, new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        AylaLog.e(LOG_TAG, "failed to enable connect status notification cause " + error
                                + ", automatically fall back to poll connection result");
                        originalRequest.setChainedRequest(fetchConnectionStatus(timeoutInSeconds,
                                DEFAULT_CONNECTION_STATUS_POLL_INTERVAL, successListener, errorListener));
                    }
                });

        originalRequest.setChainedRequest(enableNotificationRequest);
        return originalRequest;
    }

    /**
     * Confirms that the setup device has connected to the Ayla service. The mobile device needs
     * to be able to reach the Device service in order for this call to succeed.
     *
     * @param timeoutInSeconds Timeout for this operation.
     * @param setupToken       Value written to Setup Token characteristic during setup
     * @param successListener  Listener called when the device has been confirmed to connect to
     *                         the service. The listener will be called with an AylaBLEWiFiSetupDevice
     *                         containing at least the LAN IP address, device type, registration
     *                         type and time the device connected.
     * @param errorListener    Listener called if an error occurs or the operation times out.
     * @return the AylaAPIRequest for this request
     */
    public AylaAPIRequest confirmDeviceConnected(int timeoutInSeconds,
                                                 String setupToken,
                                                 Response.Listener<AylaBLEWiFiSetupDevice> successListener,
                                                 ErrorListener errorListener) {
        if (getSetupDevice().getDsn() == null) {
            errorListener.onErrorResponse(new PreconditionError("Device DSN is not available"));
            return null;
        }

        if (getSessionManager() == null) {
            errorListener.onErrorResponse(new PreconditionError("SessionManager is null"));
            return null;
        }

        Map<String, String> params = new HashMap<>();
        params.put("dsn", getSetupDevice().getDsn());
        if (setupToken != null) {
            params.put("setup_token", setupToken);
        }

        String base = AylaNetworks.sharedInstance().getServiceUrl(
                ServiceUrls.CloudService.Device, "apiv1/devices/connected.json");

        final Date startTime = new Date();

        final String url = URLHelper.appendParameters(base, params);
        // Create our internal listener object here. We're creating it outside the request object
        // so we can re-use it when we poll.
        final Response.Listener<AylaDevice.Wrapper> internalListener = new Response.Listener<AylaDevice.Wrapper>() {
            @Override
            public void onResponse(AylaDevice.Wrapper response) {
                // Unwrap the device object update our existing setup device
                getSetupDevice().setLanIp(response.device.getLanIp());
                getSetupDevice().setRegistrationType(response.device.getRegistrationType().stringValue());
                successListener.onResponse(getSetupDevice());
            }
        };

        List<AylaAPIRequest> compoundRequests = new ArrayList<>();
        AylaAPIRequest<AylaDevice.Wrapper> request = new AylaAPIRequest<AylaDevice.Wrapper>(
                Request.Method.GET,
                url,
                null,
                AylaDevice.Wrapper.class,
                getSessionManager(),
                internalListener,
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        // Did we time out?
                        if (new Date().getTime() - startTime.getTime() >
                                (timeoutInSeconds * 1000)) {
                            errorListener.onErrorResponse(error);
                        } else {
                            // We need to try again
                            final AylaAPIRequest<AylaDevice.Wrapper> request = new AylaAPIRequest<>(
                                    Request.Method.GET,
                                    url,
                                    null,
                                    AylaDevice.Wrapper.class,
                                    getSessionManager(),
                                    internalListener,
                                    this);

                            compoundRequests.add(request);
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    AylaNetworks.sharedInstance().getLoginManager()
                                            .sendUserServiceRequest(request);
                                }
                            }, getConfirmPollInterval());
                        }
                    }
                }) {
            @Override
            public void cancel() {
                for (AylaAPIRequest req : compoundRequests) {
                    req.cancel();
                }
            }
        };
        // We don't have a session to use, so we will send this out via the LoginManager's queue.
        AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
        return request;
    }

}
