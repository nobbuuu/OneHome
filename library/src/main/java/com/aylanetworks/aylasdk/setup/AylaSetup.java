package com.aylanetworks.aylasdk.setup;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.aylanetworks.aylasdk.AylaConnectivity;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaLogService;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.AylaSystemSettings;
import com.aylanetworks.aylasdk.error.AppPermissionError;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.NetworkError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.error.ServerError;
import com.aylanetworks.aylasdk.error.TimeoutError;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanCommand;
import com.aylanetworks.aylasdk.lan.AylaLanConfig;
import com.aylanetworks.aylasdk.lan.AylaLanModule;
import com.aylanetworks.aylasdk.lan.AylaLanRequest;
import com.aylanetworks.aylasdk.lan.StartScanCommand;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import com.aylanetworks.aylasdk.metrics.AylaSetupMetric;
import com.aylanetworks.aylasdk.metrics.AylaUserDataGrant;
import com.aylanetworks.aylasdk.util.AylaPredicate;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.aylanetworks.aylasdk.util.NetworkUtils;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.aylanetworks.aylasdk.util.PermissionUtils;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.aylanetworks.aylasdk.util.URLHelper;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

/**
 * The AylaSetup class is used to scan for devices that are in AP mode, or in other words have
 * not yet been configured to join the local WiFi network. Devices that are not able to join a
 * WiFi network will put themselves into AP mode and broadcast an SSID that is identifiable as an
 * Ayla device.
 * <p>
 * Once a device's AP has been discovered via a call to {@link #scanForAccessPoints}, the result
 * can be passed to {@link #connectToNewDevice} to connect the mobile device to the
 * device's AP. Once the mobile device and setup device are connected this way, the mobile device
 * asks the setup device to do its own scan for access points via
 * {@link #scanForAccessPoints}, which returns another set of access points.
 * <p>
 * At this point the user may choose an access point she wishes the device to connect to and
 * supply the app with the WiFi password. Once this is done, a call to
 * {@link #connectDeviceToService} will provide the setup device with the AP SSID and password.
 * <p>
 * At that point, the device will attempt to connect to the specified AP using the supplied
 * password. This of course will drop our connection with the setup device's AP, where we will
 * re-join the network we were previously connected to.
 * <p>
 * In order to confirm that the device has successfully connected to the WiFi network, and
 * subsequently the Ayla service, the {@link #confirmDeviceConnected} method can be called to
 * verify that the device has connected.
 * <p>
 * Once the device has been connected to the network, it may be registered to the user's account
 * if it was not already registered (e.g. WiFi password or SSID changed, etc.).
 * <p>
 * To ensure that the mobile device has returned to its original WiFi network, call
 * {@link #exitSetup} and wait for the response to ensure the network is configured as it was
 * before starting setup operations.
 */
public class AylaSetup {
    /**
     * Check every 2 seconds to see if the device has connected to the service
     */
    public final static int DEFAULT_CONFIRM_POLL_INTERVAL = 1000;
    public final static int NETID_UNKNOWN = -1;
    public final static String SETUP_DEVICE_IP = "192.168.0.1";
    /**
     * Array of permissions required by setup. Methods will check these to make sure they have
     * been permitted before proceeding.
     */
    public static final String[] SETUP_REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE
    };

    public enum WifiSecurityType{
        WPA("WPA"),
        WPA2("WPA2_Personal"),
        WEP("WEP"),
        NONE("none");

        WifiSecurityType(String value) {
            _stringValue = value;
        }
        public String stringValue() {
            return _stringValue;
        }
        private String _stringValue;
    }

    /**
     * Timeout for the requests to poll wifi status. The device is generally busy during this
     * time and shorter requests may time out.
     */
    private final static int REQ_TIMEOUT_POLL_WIFI_STATUS = 15000;
    private final static String LOG_TAG = "AylaSetup";
    private final static String UNKNOWN_STATE = "unknown";
    private final static String DEVICE_STATE_UP = "up";
    protected int _confirmPollInterval = DEFAULT_CONFIRM_POLL_INTERVAL;
    protected int _regPollInterval = 2000;
    private Context _context;
    private ScanReceiver _scanReceiver;
    private WifiInfo _currentNetworkInfo;       // Saved network info to re-join on exit
    private AylaSetupDevice _setupDevice;
    private AylaSessionManager _sessionManager;
    private AylaHttpServer _httpServer;
    private String _setupDeviceIp = SETUP_DEVICE_IP;
    private AylaWifiStatus _lastWifiStatus;
    private String _lastWifiState;
    private boolean _isSecureSetup = false;
    private boolean _fetchedRegInfo = false;
    private final Set<DeviceWifiStateChangeListener> _wifiStateChangeListeners;
    private long _setupStartTime;
    private long _setupEndTime;
    private boolean _isSetupCompleted;

    private static int _networkId = -1;
    private static String _setupSessionId = null;

    public static final String LAN_PRECONDITION_ERROR = "LAN module is null";

    public WifiInfo getCurrentNetworkInfo() {
        return _currentNetworkInfo;
    }

    /**
     * Constructor
     *
     * @param context        Context to use for registering receivers and accessing the WiFi manager
     * @param sessionManager Session manager used to manage the results, etc.
     * @throws AppPermissionError if the required permissions have not been granted to perform
     *                            WiFi setup operations
     */
    public AylaSetup(Context context, AylaSessionManager sessionManager) throws AylaError {
        //Initialize AylaLogService
        AylaLogService.initLogService(sessionManager);
        if(sessionManager != null && sessionManager.getDSManager() != null){
            sessionManager.getDSManager().onPause();
        }
        // Make sure we have permission to do this first
        AylaError permissionError = PermissionUtils.checkPermissions(context,
                SETUP_REQUIRED_PERMISSIONS);
        if (permissionError != null) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "Permission Error in AylaSetup");
            throw permissionError;
        }
        AylaLogService.addLog(LOG_TAG, "Info", String.valueOf(System.currentTimeMillis()),
                "Starting setup");
        _context = context;
        _sessionManager = sessionManager;
        _scanReceiver = new ScanReceiver();
        _wifiStateChangeListeners = new HashSet<>();
        _setupStartTime = System.currentTimeMillis();
        _isSetupCompleted = false;

        if (_sessionManager != null) {
            _httpServer = _sessionManager.getDeviceManager().getLanServer();
            _sessionManager.getDeviceManager().setLanModePermitted(false);
        } else {
            try {
                _httpServer = AylaHttpServer.createDefault(null);
            } catch (IOException e) {
                AylaLog.e(LOG_TAG, "Failed to create HTTP server: " + e);
                throw new InternalError("Failed to create HTTP server", e);
            }
        }

        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
        if(metricsManager != null){
            // Stop logs upload because there will be no internet connectiviy during setup.
            metricsManager.stopMetricsUpload();
        }
        _setupSessionId = UUID.randomUUID().toString();
    }

    /**
     * Adds the listener to be notified of device wifi setup state changes.
     *
     * @param listener Listener to be notified
     */
    public void addListener(DeviceWifiStateChangeListener listener){
        synchronized (_wifiStateChangeListeners) {
            _wifiStateChangeListeners.add(listener);
        }
    }

    public void removeListener(DeviceWifiStateChangeListener listener) {
        synchronized (_wifiStateChangeListeners) {
            _wifiStateChangeListeners.remove(listener);
        }
    }

    /**
     * Notifies listeners that the device's wifi setup state has changed.
     * @param state last fetched wifi setup state from the device.
     */
    private void notifyWifiStateListeners(final String state){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_wifiStateChangeListeners) {
                    for (DeviceWifiStateChangeListener listener : _wifiStateChangeListeners) {
                        listener.wifiStateChanged(state);
                    }
                }
            }
        });

        }


    /**
     * Returns the last fetched AylaWifiStatus received from the module. This object is returned
     * to the listeners via calls to {@link #connectDeviceToService}, but may be retreived at any
     * time from this method. If the wifi status has not been fetched since the last call to
     * connectDeviceToService, this method will return null.
     *
     * @return the last AylaWifiStatus object retrieved from the module
     */
    public AylaWifiStatus getLastWifiStatus() {
        return _lastWifiStatus;
    }


    /**
     * Scans for wireless access points visible to the mobile device and returns the list of APs.
     * This is used to find devices near the mobile device that have not yet been configured to
     * join the local WiFi network. Ayla devices have SSIDs that have recognizable names, which
     * can be used to filter the result set via a Predicate filter that may be passed to this
     * method.
     * <p>
     * Once a suitable access point has been found, applications should call
     * {@link #connectToNewDevice} to connect the mobile device to the device, and then
     * have the device scan for visible WiFi access points.
     *
     * @param timeoutInSeconds Maximum time to spend scanning for APs, in seconds. If the scan
     *                         does not complete in the specified time, the caller's
     *                         errorListener will be called with a {@link TimeoutError}.
     * @param filter           An optional Predicate that can be used to filter the result set
     * @param successListener  Listener to receive the list of scan results
     * @param errorListener    Listener to receive an error if one should occur
     * @return an AylaAPIRequest, which may be canceled to stop the scan. If the scan is canceled
     * the errorListener will be called with a {@link TimeoutError}. Other errors that may be
     * passed to the errorListener include:
     * <ul>
     * <li>AppPermissionError on M and above if permissions were not granted</li>
     * <li>PreconditionError on M and above if Location Services are not turned on</li>
     * <li>TimeoutError if the scan timed out</li>
     * </ul>
     */
    public AylaAPIRequest scanForAccessPoints(
            int timeoutInSeconds,
            final AylaPredicate<ScanResult> filter,
            final Listener<ScanResult[]> successListener,
            final ErrorListener errorListener) {

        // Make sure we have permission to do this first
        final Context context = AylaNetworks.sharedInstance().getContext();
        AylaError permissionError = PermissionUtils.checkPermissions(context,
                SETUP_REQUIRED_PERMISSIONS);
        if (permissionError != null) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "Permission Error in AylaSetup.scanForAccessPoints()");
            errorListener.onErrorResponse(permissionError);
            return null;
        }
        _context.registerReceiver(_scanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Make sure location services are turned on on M+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            LocationManager lm = (LocationManager)
                    _context.getSystemService(Context.LOCATION_SERVICE);
            boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!isNetworkEnabled && !isGPSEnabled) {
                AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                        "Android M+: Unable to start scanForAccessPoints. Location permission not" +
                                " enabled");
                errorListener.onErrorResponse(new PreconditionError("Location services are not " +
                        "enabled. WiFi scans are not permitted if location services are not " +
                        "enabled on Android M and later."));
                return null;
            }
        }

        // Set a timer to time out if necessary
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                errorListener.onErrorResponse(new TimeoutError("Timed out"));
            }
        }, timeoutInSeconds * 1000);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) !=
                PackageManager.PERMISSION_GRANTED) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "Permission Error CHANGE_WIFI_STATE in AylaSetup.scanForAccessPoints()");
            errorListener.onErrorResponse(new AppPermissionError(
                    Manifest.permission.CHANGE_WIFI_STATE));
            return null;
        }
        // Create a subclass of AylaAPIRequest. We will not be sending this request out the network
        // at all- it is just to keep the API consistent.

        AylaAPIRequest<ScanResult[]> request = new AylaAPIRequest<ScanResult[]>(
                Request.Method.GET,
                null,
                null,
                ScanResult[].class,
                null,
                new Listener<ScanResult[]>() {
                    @Override
                    public void onResponse(ScanResult[] response) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        // Filter the response if needed
                        if (filter != null) {
                            List<ScanResult> filteredList = new ArrayList<>();
                            for (ScanResult result : response) {
                                if (filter.test(result)) {
                                    filteredList.add(result);
                                }
                            }
                            response = filteredList.toArray(new ScanResult[filteredList.size()]);
                        }
                        successListener.onResponse(response);
                        if(_scanReceiver != null){
                            try{
                                _context.unregisterReceiver(_scanReceiver);
                            } catch(Exception e){
                                e.printStackTrace();
                                AylaLog.e(LOG_TAG, "Exception caught trying to unregister: " + e);
                            }
                        }

                        AylaSetupMetric setupMetric = new AylaSetupMetric(
                                AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                "scanForAccessPoints", _setupSessionId,
                                AylaMetric.Result.PARTIAL_SUCCESS, null);
                        sendToMetricsManager(setupMetric);


                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        errorListener.onErrorResponse(error);
                        AylaSetupMetric setupMetric = new AylaSetupMetric(
                                AylaMetric.LogLevel.INFO,
                                AylaSetupMetric.MetricType.SETUP_FAILURE,
                                "scanForAccessPoints", _setupSessionId,
                                AylaMetric.Result.FAILURE, error.getMessage());
                        sendToMetricsManager(setupMetric);

                    }
                }) {
            @Override
            public void cancel() {
                super.cancel();
                // Remove ourselves from the list of scan requests
                _scanReceiver.removeRequest(this);
                timeoutHandler.removeCallbacksAndMessages(null);
            }
        };

        _scanReceiver.addRequest(request);

        WifiManager wifiManager = (WifiManager) _context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.startScan();

        return request;
    }

    /**
     * Scans for wireless access points visible to the mobile device and returns the list of APs.
     * This is used to find devices near the mobile device that have not yet been configured to
     * join the local WiFi network. Ayla devices have SSIDs that have recognizable names, which
     * can be used to filter the result set via a Predicate filter that may be passed to this
     * method.
     * <p>
     * Once a suitable access point has been found, applications should call
     * {@link #connectToNewDevice} to connect the mobile device to the device, and then
     * have the device scan for visible WiFi access points.
     *
     * @param timeoutInSeconds Maximum time to spend scanning for APs, in seconds. If the scan
     *                         does not complete in the specified time, the caller's
     *                         errorListener will be called with a {@link TimeoutError}.
     * @param scanRegex        A Regular expression which is used to filter the result set, all wifi
     *                         ssid from returned result will match regex
     * @param successListener  Listener to receive the list of scan results
     * @param errorListener    Listener to receive an error if one should occur
     * @return an AylaAPIRequest, which may be canceled to stop the scan. If the scan is canceled
     * the errorListener will be called with a {@link TimeoutError}. Other errors that may be
     * passed to the errorListener include:
     * <ul>
     * <li>AppPermissionError on M and above if permissions were not granted</li>
     * <li>PreconditionError on M and above if Location Services are not turned on</li>
     * <li>TimeoutError if the scan timed out</li>
     * </ul>
     */
    public AylaAPIRequest scanAPsWithRegex(int timeoutInSeconds,
                                              final String scanRegex,
                                              final Response.Listener<ScanResult[]> successListener,
                                              final ErrorListener errorListener){
        return this.scanForAccessPoints(timeoutInSeconds, new AylaPredicate<ScanResult>() {
            @Override
            public boolean test(ScanResult scanResult) {
                return scanResult.SSID.matches(scanRegex);
            }
        }, successListener, errorListener);
    }

    /**
     * Joins the device's access point specified by the ssid and fetches information about the
     * device we just connected to. All access points are open, so no password is required.
     *
     * @param ssid             SSID of the access point to join
     * @param timeoutInSeconds Number of seconds to wait for the join to complete
     * @param successListener  Listener to be notified when the operation was successful
     * @param errorListener    Listener to receive an error should one occur
     * @return an AylaAPIRequest representing this operation
     */

    public AylaAPIRequest connectToNewDevice(final String ssid,
                                             final int timeoutInSeconds,
                                             final Listener<AylaSetupDevice> successListener,
                                             final ErrorListener errorListener) {
        return connectToNewDevice(ssid, null, WifiSecurityType.NONE, timeoutInSeconds,
                successListener, errorListener);
    }



    /**
     * Joins the device's access point specified by the ssid and fetches information about the
     * device we just connected to. All access points are open, so no password is required.
     *
     * @param ssid             SSID of the access point to join
     * @param password         Key to connect to the SSID
     * @param securityType     Security type for this device. Supported security types are
     *                         WPA2_Personal, WPA, WEP, or none
     * @param timeoutInSeconds Number of seconds to wait for the join to complete
     * @param successListener  Listener to be notified when the operation was successful
     * @param errorListener    Listener to receive an error should one occur
     * @return an AylaAPIRequest representing this operation
     */
    public AylaAPIRequest connectToNewDevice(final String ssid,
                                             final String password,
                                             final WifiSecurityType securityType,
                                             final int timeoutInSeconds,
                                             final Listener<AylaSetupDevice> successListener,
                                             final ErrorListener errorListener) {

        // Make sure we have permission to do this first
        Context context = AylaNetworks.sharedInstance().getContext();
        AylaError permissionError = PermissionUtils.checkPermissions(context,
                SETUP_REQUIRED_PERMISSIONS);
        if (permissionError != null) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "PermissionError in AylaSetup.connectToNewDevice()");
            errorListener.onErrorResponse(permissionError);
            return null;
        }


        //This request is not sent, and is used to cancel the chained requests in the compound
        // request.
        final AylaAPIRequest chainRequest = new AylaAPIRequest(Request.Method.GET, "", null, null,
                _sessionManager, successListener, errorListener);

        //This errorListener will call the successListener if error occurred due to mobile trying
        // cleartext setup on a secure device. In this case, _setupDevice does not have a DSN.
        final ErrorListener deviceDetailsErrorListener = new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                if(error instanceof ServerError){
                    if(((ServerError) error).getServerResponseCode() == 404){
                        AylaLog.d(LOG_TAG, "Got error 404. Starting secure setup");
                        _isSecureSetup = true;
                        AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                                AylaSetupMetric.MetricType.SETUP_SUCCESS, "connectToNewDevice",
                                _setupSessionId, AylaMetric.Result.PARTIAL_SUCCESS, null);
                        setupMetric.setMetricText("Starting secure server setup");
                        setupMetric.secureSetup(true);
                        setupMetric.setDeviceSecurityType(securityType._stringValue);
                        sendToMetricsManager(setupMetric);
                        fetchDeviceDetailsLAN(chainRequest, successListener, errorListener);

                    }
                } else{
                    _isSecureSetup = false;
                    errorListener.onErrorResponse(error);
                    AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                            AylaSetupMetric.MetricType.SETUP_FAILURE, "connectToNewDevice",
                            _setupSessionId, AylaMetric.Result.FAILURE, error.getMessage());
                    setupMetric.secureSetup(false);
                    setupMetric.setDeviceSecurityType(securityType._stringValue);
                    sendToMetricsManager(setupMetric);
                }
            }
        };

        //This sucessListener will return the _setupDevice object to the app, so that we can call
        // updateFrom() on this object to let the app know of any changes
        final Response.Listener<AylaSetupDevice> deviceDetailsSuccessListener =
                new Response.Listener<AylaSetupDevice>(){
                    @Override
                    public void onResponse(AylaSetupDevice response) {
                        _isSecureSetup = false;
                        successListener.onResponse(_setupDevice);
                        AylaSetupMetric setupMetric = new AylaSetupMetric(
                                AylaMetric.LogLevel.INFO,
                                AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                "connectToNewDevice", _setupSessionId,
                                AylaMetric.Result.PARTIAL_SUCCESS, null);
                        setupMetric.setMetricText("fetchDeviceDetail() was success");
                        setupMetric.setDeviceSecurityType(securityType._stringValue);
                        sendToMetricsManager(setupMetric);
                    }
                };

        final WifiManager wifiManager =
                (WifiManager) _context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // First save our host network configuration if we're not already connected to the
        // SSID. If we are, we're done.
        WifiInfo currentNetworkInfo = wifiManager.getConnectionInfo();
        if(ssid !=null && ssid.equals(ObjectUtils.unquote(currentNetworkInfo.getSSID()))) {
            // If phone is already connected to the device AP, setup cannot complete
            // succesfully as there is no internet connected
            // network to reconnect to after setup finishes.
            errorListener.onErrorResponse(new PreconditionError("Phone is already connected to " +
                    "the device to be setup. Please open WiFi Settings, and forget the network " +
                    "named "+ssid));
            return null;
        } else if (_currentNetworkInfo == null) {
            // Save the current network information so we can reconnect later
            _currentNetworkInfo = currentNetworkInfo;
        }
        // Set up a WifiConfiguration to pass to the OS
        final WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = ObjectUtils.quote(ssid);
        setWifiSecurity(conf, securityType, password);

        // Attempt to connect to the network. First see if we need to add the network, or if it
        // already exists.
        int netId;
        try {
            netId = getNetworkIdBySSIDName(wifiManager, ObjectUtils.unquote(conf.SSID));
        } catch (SecurityException e) {
            errorListener.onErrorResponse(new AppPermissionError("MissingPermission"));
            return null;
        }

        if (netId == NETID_UNKNOWN) {
            netId = wifiManager.addNetwork(conf);
            AylaLog.d(LOG_TAG, "Adding network " + netId + " for " + ssid);
        }

        _networkId = netId;
        AylaLog.d(LOG_TAG, "enableNetwork...");
        wifiManager.enableNetwork(netId, true);

        // Kick off a connect request. We will actually wait for the response via the connectivity
        // listener, so we don't care about the actual response from this
        EmptyListener<EmptyResponse> noListener = new EmptyListener<>();
        final ConnectRequest request = new ConnectRequest(noListener, noListener);

        final Handler timeoutHandler = new Handler(Looper.getMainLooper());

        // Set up a connectivity listener to see when we change networks
        final AylaConnectivity connectivity = AylaNetworks.sharedInstance().getConnectivity();
        final AylaConnectivity.AylaConnectivityListener connectivityListener =
                new AylaConnectivity.AylaConnectivityListener() {
                    @Override
                    public void connectivityChanged(boolean wifiEnabled, boolean cellularEnabled) {
                        // Check to see if we're connected to the requested AP
                        WifiInfo info = wifiManager.getConnectionInfo();
                        AylaLog.d(LOG_TAG, "connectivityChanged: wifi: " + wifiEnabled + " cell: " +
                                "" + cellularEnabled + "| SSID: " + info.getSSID());

                        // wifiEnabled doesn't work with the Nexus 6P, it's never set to true
                        // absolutely hate doing this, but the opposite of NOT Checking wifiEnabled
                        // is just as bad...
                        if (TextUtils.equals(Build.MODEL, "Nexus 6P") &&
                                TextUtils.equals(Build.MANUFACTURER, "Huawei"))
                        {
                            wifiEnabled = true;
                        }
                        if (TextUtils.equals(Build.MODEL, "Nexus 5X") &&
                                TextUtils.equals(Build.MANUFACTURER, "LGE"))
                        {
                            wifiEnabled = true;
                        }
                        String infoSSID = (info.getSSID() == null) ? null: ObjectUtils
                                .unquote(info.getSSID());

                        AylaLog.d(LOG_TAG, "Compare with: " + wifiEnabled + ", info.ssid: " +
                                infoSSID + " and ssid: "+ssid);

                        if (infoSSID !=null && infoSSID.equals(ssid)) {
                            timeoutHandler.removeCallbacksAndMessages(null);
                            AylaLog.d(LOG_TAG, "connectivityListener unregistered in " +
                                    "connectToDevice");
                            connectivity.unregisterListener(this);
                            AylaLog.d(LOG_TAG, "Connected to " + infoSSID);

                            // bindToNetwork needs to run on a background thread
                            chainRequest.setChainedRequest(request);
                            executeBindToNetwork(request, deviceDetailsSuccessListener, deviceDetailsErrorListener);
                        } else {
                            AylaLog.d(LOG_TAG, "Connected to " + infoSSID + ", want" + " to " + "connect to " + ssid);
                        }
                    }
                };

        // Set a timer to time out if we can't join the network
        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AylaLog.d(LOG_TAG, "connectivityListener unregistered in connectToDevice " +
                        "timeoutHandler");
                connectivity.unregisterListener(connectivityListener);

                // The following code is to handle the issue where connectivity broadcast on Nexus
                // phones is unreliable for networks with no internet access. The if-else
                // block is to be removed later, and only the code in the else block needs to
                // be executed once we have reliable connectivity event broadcasts.
                final WifiManager wifiManager =
                        (WifiManager) _context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifiManager.getConnectionInfo();

                if (info != null && ssid !=null && ssid.equals(ObjectUtils.unquote(info.getSSID()))) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    AylaLog.d(LOG_TAG, " In timeOutHandler. Connected to " + info.getSSID());

                    // bindToNetwork needs to run on a background thread
                    executeBindToNetwork(request, successListener, errorListener);
                } else{
                    AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                            "Timed out while trying to join " + ssid);
                    deleteConfiguredNetwork(_context);
                    errorListener.onErrorResponse(new TimeoutError("Timed out while trying to join "
                            + ssid));
                }
            }
        }, timeoutInSeconds * 1000);

        // Listen for the network state change
        connectivity.registerListener(connectivityListener);

        request._connectivityListener = connectivityListener;
        request._timeoutHandler = timeoutHandler;
        request.setRetryPolicy(new DefaultRetryPolicy(timeoutInSeconds * 1000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        return chainRequest;
    }

    /*
    Starts a background thread to run bindToNetwork()
     */
    private void executeBindToNetwork(final ConnectRequest request, final Listener<AylaSetupDevice>
            successListener, final ErrorListener errorListener ){
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AylaError error = bindToNetwork();
                if(error == null){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    fetchDeviceDetail(request, successListener,
                            errorListener);
                } else{
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            //Remove network, so it won't be saved to the phone's network settings.
                            deleteConfiguredNetwork(_context);
                            errorListener.onErrorResponse(error);
                            AylaSetupMetric setupMetric = new AylaSetupMetric(
                                    AylaMetric.LogLevel.INFO,
                                    AylaSetupMetric.MetricType.SETUP_FAILURE, "executeBindToNetwork",
                                    _setupSessionId, AylaMetric.Result.FAILURE, error.getMessage());
                            setupMetric.setMetricText("bindToNetwork() failed");
                            sendToMetricsManager(setupMetric);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Remove the specified network configuration from the list of configured devices.
     */
    public static boolean deleteConfiguredNetwork(Context context) {
        AylaLog.d(LOG_TAG, "deleteConfiguredNetwork, networkId:" + _networkId);
        if (_networkId != NETID_UNKNOWN) {
            final WifiManager wifiManager =
                    (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            boolean isSuccess = wifiManager.removeNetwork(_networkId);
            AylaLog.d(LOG_TAG, "deleteConfiguredNetwork return value  " + isSuccess);
            if (!isSuccess) {
                AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                        AylaSetupMetric.MetricType.SETUP_FAILURE, "deleteConfiguredNetwork",
                        _setupSessionId, AylaMetric.Result.FAILURE,
                        "Failed to remove configured network for the device");
                sendToMetricsManager(setupMetric);
            } else {
                _networkId = NETID_UNKNOWN;
                wifiManager.saveConfiguration();
            }

            return isSuccess;
        }

        return false;
    }

    /**
     * Internal method used to ensure we have an IP address and will remain on the selected wifi
     * network. On M+ devices, will bind the process to the network as well.
     */
    private AylaError bindToNetwork() {
        AylaLog.d(LOG_TAG, "starting bindToNetwork()");
        WifiManager wm = (WifiManager) _context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = new DhcpInfo();

        // We'll wait up to 5 seconds for DHCP info
        long start = System.nanoTime();
        long MAX_TIME = TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
        while (dhcpInfo.ipAddress == 0 && (System.nanoTime() - start < MAX_TIME)) {
            try {
                Thread.sleep(500);
                AylaLog.i(LOG_TAG, "Looking for DHCP address...");
            } catch (InterruptedException e) {
                AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                        "Interrupted in bindToNetwork");
                AylaLog.e(LOG_TAG, "Interrupted in bindToNetwork");
                break;
            }
            dhcpInfo = wm.getDhcpInfo();
        }

        if (dhcpInfo.ipAddress == 0) {
            AylaLog.e(LOG_TAG, "Failed to get DHCP address");
            return new PreconditionError("Failed to get Ip address from device.") ;
        }

        AylaLog.d(LOG_TAG, "Mobile got IPAddress " + NetworkUtils.getIpAddress(dhcpInfo.ipAddress));
        String gatewayIpAddress = NetworkUtils.getIpAddress(dhcpInfo.gateway);
        if(gatewayIpAddress == null){
            AylaLog.e(LOG_TAG, "Failed to get gateway address from DHCP");
            return new PreconditionError("Failed to get gateway Ip address from " +
                    "device.");
        }
        setSetupDeviceIp(gatewayIpAddress);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AylaLog.d(LOG_TAG, "Attempting to bind to network for Android M+ phones");
            ConnectivityManager cm = (ConnectivityManager) _context.getSystemService(Context
                    .CONNECTIVITY_SERVICE);

            // Find the network we just got an address for
            Network[] networks = cm.getAllNetworks();
            Network foundNetwork = null;
            for (Network n : networks) {
                NetworkInfo info = cm.getNetworkInfo(n);
                if (info != null &&
                        info.getType() == ConnectivityManager.TYPE_WIFI &&
                        info.isConnectedOrConnecting()) {
                    foundNetwork = n;
                    break;
                }
            }

            if (foundNetwork != null) {
                AylaLog.d(LOG_TAG, "foundNetwork:" + foundNetwork + ", info:" + cm.getNetworkInfo(foundNetwork));
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    AylaLog.e(LOG_TAG, e.getMessage());
                }
                boolean bound = cm.bindProcessToNetwork(foundNetwork);
                if(!bound){
                    return new InternalError("Could not bind process to " +
                            "device's network");
                }
            } else{
                AylaLog.e(LOG_TAG, "No network found!!");
                return new PreconditionError("Phone failed to connect to " +
                        "device's AP");

            }

        }

        return null;
    }

    /**
     * If the IP address of the device access point is not the default 192.168.0.1, this method
     * must be called to set the correct IP address before attempting to connect to the device.
     *
     * @param setupDeviceIp the IP address of the access point of the device
     */
    public void setSetupDeviceIp(String setupDeviceIp) {
        _setupDeviceIp = setupDeviceIp;
    }

    /**
     * Initiates a scan for WiFi access points on the setup device. If successful, this call will
     * tell the setup device to start scanning for visible WiFi access points. The results of the
     * scan can be obtained via a call to {@link #fetchDeviceAccessPoints}.
     *
     * @param successListener Success listener called if the operation is successful
     * @param errorListener   Listener called in case of an error
     * @return the AylaAPIRequest representing this request
     */
    public AylaAPIRequest startDeviceScanForAccessPoints(final Listener<EmptyResponse> successListener,
                                                         final ErrorListener errorListener) {

        if(!_isSecureSetup){
            AylaLog.d(LOG_TAG, "startDeviceScanForAccessPoints for _isSecureSetup "+_isSecureSetup);
            String url = formatLocalUrl("wifi_scan.json");
            final AylaAPIRequest<EmptyResponse> request =
                    new AylaAPIRequest<EmptyResponse>(
                            Request.Method.POST, url, null, EmptyResponse.class,
                            null,
                            successListener,
                            errorListener) {
                        @Override
                        protected Response<EmptyResponse>
                        parseNetworkResponse(NetworkResponse networkResponse) {
                            return Response.success(new EmptyResponse(), null);
                        }
                    };
            AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
            return request;
        }
        AylaLog.d(LOG_TAG, "StartScanCommand for _isSecureSetup "+_isSecureSetup);
        final StartScanCommand cmd = new StartScanCommand();
        AylaLanRequest request = new AylaLanRequest(_setupDevice, cmd, _setupDevice.getSessionManager(),
                new Response.Listener<AylaLanRequest.LanResponse>() {
                    @Override
                    public void onResponse(AylaLanRequest.LanResponse response) {
                        AylaLog.d(LOG_TAG, "startDeviceScanForAccessPoints response" + response);
                        AylaError error = cmd.getResponseError();
                        if (error != null) {
                            AylaLog.e(LOG_TAG, "Start scan command returned error "+
                                    error.getMessage());
                            errorListener.onErrorResponse(error);
                            return;
                        }

                        // Add a delay to wait for device to complete scan.
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                successListener.onResponse(new EmptyResponse());
                                AylaLog.d(LOG_TAG, "Start scan command sent");
                                AylaSetupMetric setupMetric = new AylaSetupMetric
                                        (AylaMetric.LogLevel.INFO,
                                                AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                                "startDeviceScanForAccessPoints",
                                                _setupSessionId, AylaMetric.Result.PARTIAL_SUCCESS,
                                                null);
                                sendToMetricsManager(setupMetric);
                            }
                        }, 3000);
                    }
                }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "Error in Start scan command "+error.getMessage());
                errorListener.onErrorResponse(error);
                AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                                AylaSetupMetric.MetricType.SETUP_FAILURE,
                                "startDeviceScanForAccessPoints",
                                _setupSessionId, AylaMetric.Result.FAILURE, error.getMessage());
                sendToMetricsManager(setupMetric);
            }
        });

        if (_setupDevice.getLanModule() != null) {
            _setupDevice.getLanModule().sendRequest(request);
        } else {
            errorListener.onErrorResponse(new PreconditionError(LAN_PRECONDITION_ERROR));
        }

        return request;

        //Todo: Check if this is needed for Pi now. 
       /* if(_setupDevice.isLanModeActive()){
            AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
        } else{
            final Date startTime = new Date();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(_setupDevice != null && !_setupDevice.isLanModeActive()){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                        if(_setupDevice == null || _setupDevice.isLanModeActive()){
                            break;
                        }
                        if ((new Date().getTime() - startTime.getTime()) > 15000) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    errorListener.onErrorResponse(new PreconditionError("Setup device" +
                                            " not in LAN mode. Cannot complete setup"));
                                }
                            });
                            break;
                        }
                    }
                    AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
                }
            });
            thread.start();
        }*/

    }

    /**
     * Fetches the list of WiFi Access Points that were discovered by the device via a prior call
     * to {@link #startDeviceScanForAccessPoints}.
     *
     * @param filter          A Predicate used to filter the scan results returned. May be null to
     *                        return all results.
     * @param successListener Listener to receive the {@link AylaWifiScanResults} containing the
     *                        set of Access Points discovered by the setup device
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest for this operation, which may be canceled.
     */
    public AylaAPIRequest fetchDeviceAccessPoints(final AylaPredicate<AylaWifiScanResults.Result> filter,
                                                  final Listener<AylaWifiScanResults>
                                                          successListener,
                                                  final ErrorListener errorListener) {
        final int requestTimeout = 10000;

        if(!_isSecureSetup){
            String url = formatLocalUrl("wifi_scan_results.json");
            final AylaAPIRequest<AylaWifiScanResults.Wrapper> request =
                    new AylaAPIRequest<AylaWifiScanResults.Wrapper>(
                            Request.Method.GET, url, null, AylaWifiScanResults.Wrapper.class,
                            null, new Listener<AylaWifiScanResults.Wrapper>() {
                                @Override
                                public void onResponse(AylaWifiScanResults.Wrapper response) {
                                    if (filter != null) {
                                        // Filter the results.
                                        AylaWifiScanResults.Result[] results =
                                                response.wifi_scan.results;
                                        if (results == null){
                                            results =  new AylaWifiScanResults.Result[0];
                                        }

                                        List<AylaWifiScanResults.Result> filteredResults =
                                                new ArrayList<>();
                                        for (AylaWifiScanResults.Result result : results) {
                                            if (filter.test(result)) {
                                                filteredResults.add(result);
                                            }
                                        }
                                        response.wifi_scan.results = filteredResults.toArray(new
                                                AylaWifiScanResults.Result[filteredResults.size()]);
                                    }

                                    successListener.onResponse(response.wifi_scan);
                                    AylaSetupMetric setupMetric = new AylaSetupMetric
                                            (AylaMetric.LogLevel.INFO,
                                                    AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                                    "fetchDeviceAccessPoints", _setupSessionId,
                                                    AylaMetric.Result.PARTIAL_SUCCESS, null);
                                    setupMetric.secureSetup(_isSecureSetup);
                                    setupMetric.setMetricText("fetched scan list size "+
                                            response.wifi_scan.results.length);
                                    sendToMetricsManager(setupMetric);
                                }
                            },
                            errorListener) {
                        @Override
                        public void deliverError(VolleyError error) {
                            super.deliverError(error);
                            AylaSetupMetric setupMetric = new AylaSetupMetric
                                    (AylaMetric.LogLevel.INFO,
                                            AylaSetupMetric.MetricType.SETUP_FAILURE,
                                            "fetchDeviceAccessPoints", _setupSessionId,
                                            AylaMetric.Result.FAILURE, error.getMessage());
                            sendToMetricsManager(setupMetric);
                        }
                    };

            // We don't have a session to use, so we will send this out via the LoginManager's queue.
            request.setRetryPolicy(new DefaultRetryPolicy(requestTimeout/2, 2, 1.0f));
            AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
            return request;
        } else{
            AylaLog.d(LOG_TAG, "CreateGetScanResultsCommand _isSecureSetup "+_isSecureSetup);
            final AylaLanCommand cmd = new AylaLanCommand("GET", "wifi_scan_results.json", null,
                    "/local_lan/wifi_scan_results.json");
            cmd.setRequestTimeout(requestTimeout);
            AylaLanRequest request = new AylaLanRequest(_setupDevice, cmd, _setupDevice.getSessionManager(),
                    new Response.Listener<AylaLanRequest.LanResponse>() {
                        @Override
                        public void onResponse(AylaLanRequest.LanResponse response) {
                            AylaLog.d(LOG_TAG, "fetchDeviceScanResults response" + response);
                            AylaError error = cmd.getResponseError();
                            if (error != null) {
                                AylaLog.d(LOG_TAG, "fetchDeviceScanResults command returned error "+
                                        error.getMessage());
                                errorListener.onErrorResponse(error);
                                AylaSetupMetric setupMetric = new AylaSetupMetric
                                        (AylaMetric.LogLevel.INFO,
                                                AylaSetupMetric.MetricType.SETUP_FAILURE,
                                                "fetchDeviceAccessPoints", _setupSessionId,
                                                AylaMetric.Result.FAILURE, error.getMessage());
                                setupMetric.secureSetup(_isSecureSetup);
                                sendToMetricsManager(setupMetric);
                                return;
                            }
                            String commandResponse = cmd.getModuleResponse();
                            AylaLog.d(LOG_TAG, "fetchDeviceScanResults command sent. Response "
                                    + commandResponse);
                            AylaWifiScanResults.Wrapper wrapper = AylaNetworks.sharedInstance()
                                    .getGson().fromJson(commandResponse, AylaWifiScanResults
                                            .Wrapper.class);

                            if (filter != null) {
                                // Filter the results.
                                AylaWifiScanResults.Result[] results =
                                        wrapper.wifi_scan.results;
                                List<AylaWifiScanResults.Result> filteredResults =
                                        new ArrayList<>();
                                for (AylaWifiScanResults.Result result : results) {
                                    if (filter.test(result)) {
                                        filteredResults.add(result);
                                    }
                                }
                                wrapper.wifi_scan.results = filteredResults.toArray(new
                                        AylaWifiScanResults.Result[filteredResults.size()]);
                            }

                            successListener.onResponse(wrapper.wifi_scan);

                        }
                    }, new ErrorListener() {
                @Override
                public void onErrorResponse(AylaError error) {
                    AylaLog.e(LOG_TAG, "Error in fetchDeviceScanResults command "+error
                            .getMessage());
                    errorListener.onErrorResponse(error);
                    AylaSetupMetric setupMetric = new AylaSetupMetric
                            (AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_FAILURE,
                                    "fetchDeviceAccessPoints", _setupSessionId,
                                    AylaMetric.Result.FAILURE, error.getMessage());
                    setupMetric.secureSetup(_isSecureSetup);
                    sendToMetricsManager(setupMetric);
                }
            });

            if (_setupDevice.getLanModule() != null) {
                _setupDevice.getLanModule().sendRequest(request);
            } else {
                errorListener.onErrorResponse(new PreconditionError(LAN_PRECONDITION_ERROR));
            }

            return request;
        }

    }


    /**
     * Fetches the list of WiFi Access Points that were discovered by the device via a prior call
     * to {@link #startDeviceScanForAccessPoints}.
     *
     * @param scanRegex       A regular expression used to filter the scan results returned, returned
     *                        wifi ssid won't match regexp
     * @param successListener Listener to receive the {@link AylaWifiScanResults} containing the
     *                        set of Access Points discovered by the setup device
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest for this operation, which may be canceled.
     */
    public AylaAPIRequest fetchDeviceAPsWithRegex(final String scanRegex,
                                                  final Response.Listener<AylaWifiScanResults> successListener,
                                                  ErrorListener errorListener){
        return this.fetchDeviceAccessPoints(new AylaPredicate<AylaWifiScanResults.Result>() {
            @Override
            public boolean test(AylaWifiScanResults.Result result) {
                return !result.ssid.matches(scanRegex);
            }
        }, successListener, errorListener);
    }

    /**
     * Sends the SSID and password of the WiFi network the setup device should join, along with
     * an optional setup token and location coordinates.
     *
     * @param ssid             SSID the setup device should attempt to join
     * @param password         Password for the WiFi network
     * @param setupToken       Optional setup token provided by the application
     * @param latitude         Optional latitude
     * @param longitude        Optional longitude
     * @param timeoutInSeconds Timeout to poll for connection
     * @param successListener  Listener called on a successful operation
     * @param errorListener    Listener called if an error occurred
     * @return the AylaAPIRequest for this operation
     */
    public AylaAPIRequest connectDeviceToService(final String ssid,
                                                 String password,
                                                 String setupToken,
                                                 Double latitude,
                                                 Double longitude,
                                                 int timeoutInSeconds,
                                                 final Listener<AylaWifiStatus> successListener,
                                                 final ErrorListener errorListener) {
        if (_setupDevice == null) {
            errorListener.onErrorResponse(new PreconditionError("Not connected to a setup device"));
            AylaSetupMetric setupMetric = new AylaSetupMetric
                    (AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_FAILURE,
                            "connectDeviceToService", _setupSessionId, AylaMetric.Result.FAILURE,
                            "No SetupDevice found");
            setupMetric.secureSetup(_isSecureSetup);
            sendToMetricsManager(setupMetric);
            return null;
        }

        if (!_setupDevice.isLanModeActive()) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "Setup device is not in LAN mode");
            errorListener.onErrorResponse(new PreconditionError("Setup device is not in LAN mode"));
            AylaSetupMetric setupMetric = new AylaSetupMetric
                    (AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_FAILURE,
                            "connectDeviceToService", _setupSessionId, AylaMetric.Result.FAILURE,
                            "Setup device is not in LAN mode");
            setupMetric.secureSetup(_isSecureSetup);
            sendToMetricsManager(setupMetric);
            return null;
        }

        _lastWifiStatus = null;

        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final Handler pollHandler = new Handler(Looper.getMainLooper());


        String url = "wifi_connect.json";
        Map<String, String> params = new HashMap<>();
        params.put("ssid", ssid);
        if (password != null) {
            params.put("key", password);
        }
        if (setupToken != null) {
            if (setupToken.length() > 8) {
                AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                        "Setup token may be 8 characters at most");
                errorListener.onErrorResponse(new InvalidArgumentError("Setup token may be 8 " +
                        "characters at most"));
                AylaSetupMetric setupMetric = new AylaSetupMetric
                        (AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_FAILURE,
                                "connectDeviceToService", _setupSessionId, AylaMetric.Result.FAILURE,
                                "Setup token length "+setupToken.length());
                setupMetric.secureSetup(_isSecureSetup);
                sendToMetricsManager(setupMetric);
                return null;
            }
            params.put("setup_token", setupToken);
        }
        if(AylaNetworks.sharedInstance().getUserDataGrants().isEnabled(
                AylaUserDataGrant.AYLA_USER_DATA_GRANT_METRICS_SERVICE)){
            if (latitude != null && longitude != null) {
                params.put("location", String.format(Locale.US, "%f,%f", latitude, longitude));
            }
        }
        url = URLHelper.appendParameters(url, params);
        AylaLanCommand command = new AylaLanCommand("POST", url, "none",
                "/local_lan/connect_status");
        final AylaLanRequest request = new AylaLanRequest(_setupDevice, command, null,
                new Listener<AylaLanRequest.LanResponse>() {
                    @Override
                    public void onResponse(AylaLanRequest.LanResponse response) {
                        // This is called if we cannot start polling in ap-sta mode
                        timeoutHandler.removeCallbacksAndMessages(null);

                        if (command.getResponseError() != null) {
                            AylaError error = command.getResponseError();
                            AylaLog.d(LOG_TAG, "wifi_connect command returned error " + error.getMessage());
                            errorListener.onErrorResponse(error);
                            return;
                        }

                        String moduleResponse = command.getModuleResponse();
                        AylaWifiStatus.HistoryItem history = AylaNetworks.sharedInstance().getGson()
                                .fromJson(moduleResponse, AylaWifiStatus.HistoryItem.class);
                        if (history != null && history.error != null
                                && history.error != AylaWifiStatus.HistoryItem.Error.NoError) {
                            errorListener.onErrorResponse(new ServerError(
                                    NanoHTTPD.Response.Status.BAD_REQUEST.getRequestStatus(),
                                    moduleResponse.getBytes(), history.error.name(), null));
                        } else {
                            successListener.onResponse(_lastWifiStatus);
                        }
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        errorListener.onErrorResponse(error);
                    }
                }) {
            @Override
            protected void deliverResponse(LanResponse response) {
                if (_setupDevice.hasFeature(AylaSetupDevice.FEATURE_AP_STA)) {
                    AylaLog.i(LOG_TAG, "Device supports ap-sta, polling wifi status...");

                    // We also need to check for the AP going away. Sometimes the module will just
                    // drop the AP while we are polling it.
                    AylaConnectivity.AylaConnectivityListener connectivityListener =
                            new AylaConnectivity.AylaConnectivityListener() {
                                @Override
                                public void connectivityChanged(boolean wifiEnabled,
                                                                boolean cellularEnabled) {
                                    AylaLog.d(LOG_TAG, "connectivityChanged: wifi: " +
                                            wifiEnabled + " cell: " + cellularEnabled);

                                    // We don't care what happened- if connectivity has changed,
                                    // then our polling is not going to work. We'll just consider
                                    // ourselves done.
                                    timeoutHandler.removeCallbacksAndMessages(null);
                                    pollHandler.removeCallbacksAndMessages(null);
                                    AylaNetworks.sharedInstance().getConnectivity()
                                            .unregisterListener(this);
                                    if(!isCanceled()){
                                        errorListener.onErrorResponse(new
                                                NetworkError
                                                ("Network state changed while polling WiFi " +
                                                        "connect status on the setup device. " +
                                                        "Unable to determine if the device " +
                                                        "has joined the WiFi network", null));
                                    }
                                }
                            };
                    AylaNetworks.sharedInstance().getConnectivity()
                            .registerListener(connectivityListener);
                    pollDeviceConnectToAP(this, pollHandler, timeoutHandler, connectivityListener,
                            ssid, successListener, errorListener);
                } else {
                    AylaLog.i(LOG_TAG, "Device does not support ap-sta");
                    super.deliverResponse(response);
                }
            }

            @Override
            public void cancel() {
                super.cancel();
                timeoutHandler.removeCallbacksAndMessages(null);
            }
        };

        // Before telling the module to connect to the AP, first get its current WiFi connect
        // history. We will use this to determine when the connect history has changed.
        final AylaAPIRequest wifiStatusRequest = fetchDeviceWifiStatus(
                new Listener<AylaWifiStatus>() {
                    @Override
                    public void onResponse(AylaWifiStatus response) {
                        updateAndNotifyState(response.getState());
                        _lastWifiStatus = response;
                        AylaLog.d(LOG_TAG, "connectDeviceToService getting _lastWifiStatus "
                                +_lastWifiStatus.toString());
                        if (_setupDevice.getLanModule() != null) {
                            // Now we can send out the request to join the wifi network.
                            _setupDevice.getLanModule().sendRequest(request);
                        } else {
                            AylaLog.e(LOG_TAG, "Device is not in LAN mode");
                            errorListener.onErrorResponse(new PreconditionError(LAN_PRECONDITION_ERROR));
                        }
                    }
                },
                errorListener);



        // This will take a long time. We have our own timeout handler anyway. Set the request
        // timeout to something ridiculous like 10 minutes.
        request.setRetryPolicy(new DefaultRetryPolicy(600000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        wifiStatusRequest.setChainedRequest(request);

        // Post the timeout runnable. It will be cancelled if we get a response or the request is
        // canceled.
        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                wifiStatusRequest.cancel();
                request.cancel();
                errorListener.onErrorResponse(new TimeoutError("Timed out"));
            }
        }, timeoutInSeconds * 1000);

        return wifiStatusRequest;
    }

    /**
     * Fetches the current WiFi status from the module. The AylaWifiStatus object contains the
     * current status as well as the history of connections.
     *
     * @param successListener Listener to receive the AylaWifiStatus object if successful
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest for this request, which may be canceled.
     */
    public AylaAPIRequest fetchDeviceWifiStatus(final Listener<AylaWifiStatus> successListener,
                                                final ErrorListener errorListener) {
        AylaLanModule lanModule = _setupDevice.getLanModule();
        if (lanModule == null) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "AylaSetup.fetchDeviceWifiStatus(). PreconditionError. Device is not in LAN " +
                            "mode ");
            errorListener.onErrorResponse(new PreconditionError("Device is not in LAN mode"));
            AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                            AylaSetupMetric.MetricType.SETUP_FAILURE, "fetchDeviceWifiStatus",
                            _setupSessionId, AylaMetric.Result.FAILURE, "Device is not in LAN mode");
            setupMetric.secureSetup(_isSecureSetup);
            sendToMetricsManager(setupMetric);
            return null;
        }

        if(!_isSecureSetup){
            AylaAPIRequest<AylaWifiStatus.Wrapper> request = new AylaAPIRequest<>(
                    Request.Method.GET, formatLocalUrl("wifi_status.json"), null,
                    AylaWifiStatus.Wrapper.class, null,
                    new Listener<AylaWifiStatus.Wrapper>() {
                        @Override
                        public void onResponse(AylaWifiStatus.Wrapper response) {
                            successListener.onResponse(response.wifi_status);
                        }
                    }, errorListener);

            lanModule.sendRequest(request);
            return request;
        } else{
            final AylaLanCommand command = new AylaLanCommand("GET", "wifi_status.json", null,
                    "/local_lan/wifi_status.json");
            AylaLanRequest request = new AylaLanRequest(_setupDevice, command, _sessionManager, new
                    Listener<AylaLanRequest.LanResponse>() {
                @Override
                public void onResponse(AylaLanRequest.LanResponse response) {
                    AylaError error = command.getResponseError();
                    if (error != null) {
                        AylaLog.d(LOG_TAG, "fetch wifi_status command returned error "+
                                error.getMessage());
                        errorListener.onErrorResponse(error);
                        return;
                    }
                    String moduleResponse = command.getModuleResponse();
                    AylaLog.d(LOG_TAG, "Fetch wifi_status command response "+ moduleResponse);
                    AylaWifiStatus.Wrapper wrapper = AylaNetworks.sharedInstance().getGson()
                            .fromJson(moduleResponse, AylaWifiStatus.Wrapper.class);
                    successListener.onResponse(wrapper.wifi_status);
                }
            }, errorListener);
            lanModule.sendRequest(request);
            return request;
        }


    }

    /**
     * Fetches the registration info from this module. The AylaRegInfo object contains the
     * information required to register this device, such as reg token and registration type.
     *
     * @param successListener Listener to receive the AylaRegInfo object if successful
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest for this request, which may be canceled.
     */
    private AylaAPIRequest fetchRegInfo(final Listener<AylaRegInfo> successListener,
                                                final ErrorListener errorListener) {
        AylaLanModule lanModule = _setupDevice.getLanModule();
        if (lanModule == null) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "AylaSetup.fetchRegInfo(). PreconditionError. Device is not in LAN " +
                            "mode ");
            errorListener.onErrorResponse(new PreconditionError("Device is not in LAN mode"));
            AylaSetupMetric setupMetric = new AylaSetupMetric
                    (AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_FAILURE,
                            "fetchRegInfo", _setupSessionId, AylaMetric.Result.FAILURE,
                            "Device is not in LAN mode");
            setupMetric.secureSetup(_isSecureSetup);
            sendToMetricsManager(setupMetric);
            return null;
        }

        if(!_isSecureSetup){
            AylaAPIRequest<AylaRegInfo> request = new AylaAPIRequest<>(
                    Request.Method.GET, formatLocalUrl("regtoken.json"), null,
                    AylaRegInfo.class, null,
                    new Listener<AylaRegInfo>() {
                        @Override
                        public void onResponse(AylaRegInfo response) {
                            successListener.onResponse(response);
                            AylaSetupMetric setupMetric = new AylaSetupMetric
                                    (AylaMetric.LogLevel.INFO,
                                            AylaSetupMetric.MetricType.SETUP_SUCCESS, "fetchRegInfo",
                                            _setupSessionId, AylaMetric.Result.PARTIAL_SUCCESS,
                                            null);
                            setupMetric.secureSetup(_isSecureSetup);
                            sendToMetricsManager(setupMetric);
                        }
                    }, errorListener);

            lanModule.sendRequest(request);
            return request;
        } else{
            final AylaLanCommand command = new AylaLanCommand("GET", "regtoken.json", null,
                    "/local_lan/regtoken.json");
            AylaLanRequest request = new AylaLanRequest(_setupDevice, command, _sessionManager, new
                    Listener<AylaLanRequest.LanResponse>() {
                        @Override
                        public void onResponse(AylaLanRequest.LanResponse response) {
                            AylaError error = command.getResponseError();
                            if (error != null) {
                                AylaLog.d(LOG_TAG, "fetch regtoken command returned error "+
                                        error.getMessage());
                                errorListener.onErrorResponse(error);
                                AylaSetupMetric setupMetric = new AylaSetupMetric
                                        (AylaMetric.LogLevel.INFO,
                                                AylaSetupMetric.MetricType.SETUP_FAILURE,
                                                "fetchRegInfo", _setupSessionId,
                                                AylaMetric.Result.FAILURE, error.getMessage());
                                setupMetric.secureSetup(_isSecureSetup);
                                sendToMetricsManager(setupMetric);
                                return;
                            }
                            String moduleResponse = command.getModuleResponse();
                            AylaLog.d(LOG_TAG, "Fetch regtoken command response "+ moduleResponse);
                            AylaRegInfo regInfo = AylaNetworks.sharedInstance().getGson()
                                    .fromJson(moduleResponse, AylaRegInfo.class);
                            successListener.onResponse(regInfo);
                            AylaSetupMetric setupMetric = new AylaSetupMetric
                                    (AylaMetric.LogLevel.INFO,
                                            AylaSetupMetric.MetricType.SETUP_SUCCESS, "fetchRegInfo",
                                            _setupSessionId, AylaMetric.Result.PARTIAL_SUCCESS,
                                            null);
                            setupMetric.secureSetup(_isSecureSetup);
                            sendToMetricsManager(setupMetric);
                        }
                    }, errorListener);
            lanModule.sendRequest(request);
            return request;
        }


    }

    /**
     * Polls the device for its wifi status, looking to see when it joins the specified ssid.
     * When the device reports that it is connected to the specified ssid, the successListener
     * will be called. If an error is returned from the module, the errorListener will be called.
     * Otherwise this method will poll forever until it is canceled.
     * <p>
     * This method is an internal part of a chained request kicked off by connectDeviceToService.
     * The original request and connectivity listener are passed in to this method so that they
     * can be canceled or chained.
     *
     * @param originalRequest      Original request that kicked this off. This is from
     *                             connectDeviceToService.
     * @param connectivityListener Listener for connectivity changes that should be unregistered
     *                             before this method returns a result to the caller
     * @param ssid                 SSID the device is trying to connect to
     * @param successListener      Listener to be notified once the AP has been joined. The
     *                             listener will be provided with the final AylaWifiStatus
     *                             received from the module.
     * @param errorListener        Listener to be notified in case of an error
     */
    private void pollDeviceConnectToAP(final AylaAPIRequest originalRequest,
                                       final Handler pollHandler,
                                       final Handler timeoutHandler,
                                       final AylaConnectivity.AylaConnectivityListener
                                               connectivityListener,
                                       final String ssid,
                                       final Listener<AylaWifiStatus> successListener,
                                       final ErrorListener errorListener) {
        if (_setupDevice == null) {
            // This should not happen
            errorListener.onErrorResponse(new PreconditionError("Not connected to a setup device"));
            return;
        }

        AylaLanModule lanModule = _setupDevice.getLanModule();
        if (lanModule == null) {
            pollHandler.removeCallbacksAndMessages(null);
            timeoutHandler.removeCallbacksAndMessages(null);
            errorListener.onErrorResponse(new PreconditionError("Device is not in LAN mode"));
            return;
        }
        final AylaConnectivity connectivity = AylaNetworks.sharedInstance().getConnectivity();

        AylaAPIRequest chainedRequest = fetchDeviceWifiStatus(
                new Listener<AylaWifiStatus>() {
                    @Override
                    public void onResponse(AylaWifiStatus response) {
                        AylaLog.i(LOG_TAG, response.toString());
                        boolean tryAgain = true;
                       updateAndNotifyState(response.getState());
                        if (TextUtils.equals(response.getConnectedSsid(), ssid)){
                            if(response.getConnectHistory() != null && response.getConnectHistory().length > 0
                                    && response.getConnectHistory()[0].error ==
                                    AylaWifiStatus.HistoryItem.Error.NoError){
                                // Device connected to the correct SSID.
                                tryAgain = false;
                                connectivity.unregisterListener(connectivityListener);
                                AylaLog.i(LOG_TAG, "Device successfully connected to " + ssid);
                                _lastWifiStatus = response;
                                timeoutHandler.removeCallbacksAndMessages(null);
                                pollHandler.removeCallbacksAndMessages(null);
                                if(_setupDevice.hasFeature(AylaSetupDevice.FEATURE_REG_TOKEN) &&
                                        response.getState().equals(DEVICE_STATE_UP)){
                                    AylaLog.d(LOG_TAG, "device wifi state is UP.  Fetch reg info");
                                    fetchRegInfo(new Response.Listener<AylaRegInfo>(){
                                        @Override
                                        public void onResponse(AylaRegInfo response) {
                                            AylaLog.d(LOG_TAG, "Reg info fetched "+response);
                                            _fetchedRegInfo = true;
                                            _setupDevice.setRegToken(response.getRegtoken());
                                            _setupDevice.setRegistrationType(AylaDevice
                                                    .RegistrationType.fromString(
                                                    response.getRegistrationType()));
                                        }
                                    }, new ErrorListener(){
                                        @Override
                                        public void onErrorResponse(AylaError error) {
                                            AylaLog.d(LOG_TAG, "Error in fetching reg info");
                                            _fetchedRegInfo = false;
                                        }
                                    });
                                }
                                successListener.onResponse(_lastWifiStatus);
                            }
                        } else {
                            AylaLog.i(LOG_TAG, "Device connected to " + response.getConnectedSsid() +
                                    ", trying to connect to " + ssid);
                            pollHandler.removeCallbacksAndMessages(null);

                            // See if we have any status
                            String errorMsg = "Unable to determine connect status";
                            AylaWifiStatus.HistoryItem[] history = response.getConnectHistory();
                            if ((history != null) && (history.length > 0))
                            {
                                errorMsg = history[0].error.toString();
                                if(_lastWifiStatus != null) {
                                    AylaWifiStatus.HistoryItem[] lastFetchedHistory =
                                            _lastWifiStatus.getConnectHistory();

                                    if (lastFetchedHistory != null &&
                                            lastFetchedHistory.length > 0 &&
                                            lastFetchedHistory[0].mtime ==
                                                    history[0].mtime) {
                                        AylaLog.d(LOG_TAG, "mtime has not changed, continuing to poll" +
                                                " wifi connect history");
                                    } else if (history[0].error !=
                                            AylaWifiStatus.HistoryItem.Error.InProgress) {
                                        AylaLog.e(LOG_TAG, "mtime changed, but connect error is not " +
                                                "InProgress, is: " + errorMsg);
                                        // An error has occurred.
                                        tryAgain = false;
                                        timeoutHandler.removeCallbacksAndMessages(null);
                                        connectivity.unregisterListener(connectivityListener);
                                        errorListener.onErrorResponse(new InternalError
                                                ("Device wifi status: "+errorMsg));
                                    }
                                }
                            }

                        }

                        if (tryAgain) {
                            pollHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!originalRequest.isCanceled()) {
                                        pollDeviceConnectToAP(originalRequest, pollHandler,
                                                timeoutHandler, connectivityListener, ssid,
                                                successListener, errorListener);
                                    }
                                }
                            }, getConfirmPollInterval());
                        }
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        if (error.getErrorType() == AylaError.ErrorType.Timeout) {
                            // We expect to get timeout errors on occasion when polling the
                            // device for wifi status. It gets busy connecting to the AP and will
                            // ignore us sometimes, so we will just try again later.
                            pollHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!originalRequest.isCanceled()) {
                                        pollDeviceConnectToAP(originalRequest, pollHandler,
                                                timeoutHandler, connectivityListener,
                                                ssid, successListener, errorListener);
                                    }
                                }
                            }, getConfirmPollInterval());
                        } else {
                            // Pass the error back to the caller
                            pollHandler.removeCallbacksAndMessages(null);
                            timeoutHandler.removeCallbacksAndMessages(null);
                            connectivity.unregisterListener(connectivityListener);
                            errorListener.onErrorResponse(error);
                        }
                    }
                }
        );

        // Increase the default timeout here- the device is very busy and slow to respond
        chainedRequest.setRetryPolicy(new DefaultRetryPolicy(REQ_TIMEOUT_POLL_WIFI_STATUS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        originalRequest.setChainedRequest(chainedRequest);

        // the chainedRequest has already been sent out by fetchDeviceWifiStatus
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
     * Confirms that the setup device has connected to the Ayla service. The mobile device needs
     * to be able to reach the Device service in order for this call to succeed. Make sure the
     * mobile device has called {@link #reconnectToOriginalNetwork}
     * before making this call to ensure we are not connected to the module's access point.
     *
     * @param timeoutInSeconds Timeout for this operation.
     * @param dsn              DSN of the device to confirm
     * @param setupToken       Setup token passed to the device in
     *                         {@link #connectDeviceToService}
     * @param successListener  Listener called when the device has been confirmed to connect to
     *                         the service. The listener will be called with an AylaSetupDevice
     *                         containing at least the LAN IP address, device type, registration
     *                         type and time the device connected.
     * @param errorListener    Listener called if an error occurs or the operation times out
     * @return the AylaAPIRequest for this request
     */
    public AylaAPIRequest confirmDeviceConnected(final int timeoutInSeconds,
                                                 String dsn,
                                                 String setupToken,
                                                 final Listener<AylaSetupDevice> successListener,
                                                 final ErrorListener errorListener) {
        if (dsn == null) {
            errorListener.onErrorResponse(new PreconditionError("DSN is required"));
            return null;
        }
        if(_sessionManager == null){
            errorListener.onErrorResponse(new PreconditionError("SessionManager is null"));
            return null;
        }

        if(_fetchedRegInfo &&_setupDevice.getRegToken() != null){
            // We already have all the info required for registration.
            AylaLog.d(LOG_TAG, "Found fetched reg info from the device. Returning _setupDevice");
            successListener.onResponse(_setupDevice);
            return null;
        }

        // Make sure we are not bound to any networks on M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = (ConnectivityManager) _context.getSystemService(Context
                    .CONNECTIVITY_SERVICE);
            cm.bindProcessToNetwork(null);
        }

        Map<String, String> params = new HashMap<>();
        params.put("dsn", dsn);
        if (setupToken != null) {
            params.put("setup_token", setupToken);
        }

        String base = AylaNetworks.sharedInstance().getServiceUrl(
                ServiceUrls.CloudService.Device, "apiv1/devices/connected.json");

        final Date startTime = new Date();

        final String url = URLHelper.appendParameters(base, params);
        // Create our internal listener object here. We're creating it outside the request object
        // so we can re-use it when we poll.
        final Listener<AylaDevice.Wrapper> internalListener = new Listener<AylaDevice.Wrapper>() {
            @Override
            public void onResponse(AylaDevice.Wrapper response) {
                // Unwrap the device object and either update our existing setup device,
                // or create a new one to return to the caller.
                _setupEndTime = System.currentTimeMillis();
                AylaSetupDevice setupDevice;
                if (_setupDevice != null) {
                    setupDevice = _setupDevice;
                } else {
                    setupDevice = new AylaSetupDevice();
                }

                setupDevice.updateFrom(response.device, AylaDevice.DataSource.CLOUD);
                successListener.onResponse(setupDevice);
                _isSetupCompleted = true;
                AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                        AylaSetupMetric.MetricType.SETUP_SUCCESS, "confirmDeviceConnected",
                        _setupSessionId, AylaMetric.Result.PARTIAL_SUCCESS, null);
                setupMetric.secureSetup(_isSecureSetup);
                sendToMetricsManager(setupMetric);

                long totalSetupTime = _setupEndTime - _setupStartTime;
                AylaLog.d(LOG_TAG, "Setup completed. Writing to logs setupTime "+totalSetupTime);
                setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                        AylaSetupMetric.MetricType.SETUP_SUCCESS, "exitSetup", _setupSessionId,
                        AylaMetric.Result.SUCCESS, null);
                setupMetric.setRequestTotalTime(totalSetupTime);
                sendToMetricsManager(setupMetric);


            }
        };

        final List<AylaAPIRequest> compoundRequests = new ArrayList<>();
        AylaAPIRequest<AylaDevice.Wrapper> request = new AylaAPIRequest<AylaDevice.Wrapper>(
                Request.Method.GET,
                url,
                null,
                AylaDevice.Wrapper.class,
                _sessionManager,
                internalListener,
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        // Did we time out?
                        if (new Date().getTime() - startTime.getTime() >
                                (timeoutInSeconds * 1000)) {
                            errorListener.onErrorResponse(error);
                            AylaSetupMetric setupMetric = new AylaSetupMetric
                                    (AylaMetric.LogLevel.INFO,
                                            AylaSetupMetric.MetricType.SETUP_FAILURE,
                                            "confirmDeviceConnected", _setupSessionId,
                                            AylaMetric.Result.FAILURE, error.getMessage());
                            setupMetric.secureSetup(_isSecureSetup);
                            sendToMetricsManager(setupMetric);
                        } else {
                            // We need to try again
                            final AylaAPIRequest<AylaDevice.Wrapper> request = new AylaAPIRequest<>(
                                    Request.Method.GET,
                                    url,
                                    null,
                                    AylaDevice.Wrapper.class,
                                    _sessionManager,
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

                super.cancel();
            }
        };

        // We don't have a session to use, so we will send this out via the LoginManager's queue.
        AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
        return request;
    }

    /**
     * Connects the mobile device to the given WiFi network. It will fail with a
     * PreconditionError if the specified ssidName is not found in WiFiManager's list of APs.
     *
     * @param ssidName         WiFi Network name
     * @param timeoutInSeconds Maximum time to spend trying to reconnect. A TimeoutError will be
     *                         delivered to the ErrorListener if this time is exceeded before we
     *                         are able to join the network.
     * @param successListener  Listener called when we have successfully re-joined the network
     * @param errorListener    Listener called if we fail to join the network in the specified time
     *                         period.
     * @return the AylaAPIRequest that may be canceled to stop this operation
     */
    public AylaAPIRequest connectToNetwork(final String ssidName,
                                             int timeoutInSeconds,
                                             final Listener<EmptyResponse> successListener,
                                             final ErrorListener errorListener) {
        if (_setupDevice != null) {
            _setupDevice.stopLanSession();
        }

        // Are we already on the right network?
        final WifiManager wifiManager =
                (WifiManager) _context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Make sure we have permission to do this first
        Context context = AylaNetworks.sharedInstance().getContext();
        AylaError permissionError = PermissionUtils.checkPermissions(context,
                SETUP_REQUIRED_PERMISSIONS);
        if (permissionError != null) {
            errorListener.onErrorResponse(permissionError);
            AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                    AylaSetupMetric.MetricType.SETUP_FAILURE, "reconnectToOriginalNetwork",
                    _setupSessionId, AylaMetric.Result.FAILURE, "Missing permissions.");
            setupMetric.secureSetup(_isSecureSetup);
            sendToMetricsManager(setupMetric);
            return null;
        }

        // Forget the module AP
        // Note: the network might have changed.
        deleteConfiguredNetwork(_context);

        final String unquotedNetworkSSID = ObjectUtils.unquote(ssidName);
        int netId;
        try {
            netId = getNetworkIdBySSIDName(wifiManager, unquotedNetworkSSID);
        } catch (SecurityException e) {
            errorListener.onErrorResponse(new AppPermissionError("MissingPermission"));
            return null;
        }

        if (netId == NETID_UNKNOWN) {
            // Couldn't find the original network
            errorListener.onErrorResponse(new PreconditionError("Unable to find original network " +
                    "with SSID " + ssidName));
            AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                    AylaSetupMetric.MetricType.SETUP_FAILURE, "reconnectToOriginalNetwork",
                    _setupSessionId, AylaMetric.Result.FAILURE, "Unable to find original network.");
            setupMetric.secureSetup(_isSecureSetup);
            sendToMetricsManager(setupMetric);
            return null;
        }

        // Re-join the network
        AylaLog.d(LOG_TAG, "enableNetwork...");
        wifiManager.enableNetwork(netId, true);

        // Now monitor the network changes and find out when we're joined to the original network
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final AylaConnectivity connectivity = AylaNetworks.sharedInstance().getConnectivity();
        if (connectivity == null) {
            errorListener.onErrorResponse(new InternalError("Connectivity unavailable. Are we " +
                    "shutting down?"));
            return null;
        }

        // Create a listener to monitor the network configuration changes
        final AylaConnectivity.AylaConnectivityListener connectivityListener =
                new AylaConnectivity.AylaConnectivityListener() {
                    @Override
                    public void connectivityChanged(boolean wifiEnabled, boolean cellularEnabled) {
                        if (wifiEnabled) {
                            WifiInfo info = wifiManager.getConnectionInfo();
                            if (info != null && _currentNetworkInfo != null) {
                                String unquotedSSID = ObjectUtils.unquote(info.getSSID());
                                if (unquotedSSID != null && unquotedSSID.equals(unquotedNetworkSSID)) {
                                    // We're all set.
                                    timeoutHandler.removeCallbacksAndMessages(null);
                                    connectivity.unregisterListener(this);
                                    _currentNetworkInfo = null;
                                    AylaLog.d(LOG_TAG, "Connected to " + info.getSSID());

                                    // bindToNetwork is a blocking call- let's run that in the
                                    // background as this listener is on the main thread.
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            final AylaError error = bindToNetwork();
                                            if(error == null){
                                                new Handler(Looper.
                                                        getMainLooper()).
                                                        post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                successListener.
                                                                        onResponse(new
                                                                                EmptyResponse());
                                                                AylaSetupMetric setupMetric =
                                                                        new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                                                                                AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                                                                "reconnectToOriginalNetwork",
                                                                                _setupSessionId,
                                                                                AylaMetric.Result.PARTIAL_SUCCESS,
                                                                                null);
                                                                setupMetric.secureSetup(_isSecureSetup);
                                                                sendToMetricsManager(setupMetric);
                                                            }
                                                        });
                                            } else{
                                                new Handler(Looper.getMainLooper()).
                                                        post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                errorListener.onErrorResponse(error);
                                                                AylaSetupMetric setupMetric =
                                                                        new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                                                                                AylaSetupMetric.MetricType.SETUP_FAILURE,
                                                                                "reconnectToOriginalNetwork",
                                                                                _setupSessionId,
                                                                                AylaMetric.Result.FAILURE,
                                                                                error.getMessage());
                                                                setupMetric.secureSetup(_isSecureSetup);
                                                                sendToMetricsManager(setupMetric);
                                                            }
                                                        });
                                            }

                                        }
                                    }).start();
                                } else {
                                    AylaLog.d(LOG_TAG, "Connected to " + info.getSSID() +
                                            ", want to connect to " +
                                            ssidName);
                                }
                            } else {
                                AylaLog.d(LOG_TAG, "no connectionInfo");
                            }
                        }
                    }
                };

        // Set a timer to time out if we can't join the network
        timeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectivity.unregisterListener(connectivityListener);
                String result = "(no current network)";
                if (_currentNetworkInfo != null) {
                    result = ssidName;
                }

                errorListener.onErrorResponse(new TimeoutError("Timed out waiting to re-join " +
                        result));
                AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                        AylaSetupMetric.MetricType.SETUP_FAILURE, "reconnectToOriginalNetwork",
                        _setupSessionId, AylaMetric.Result.FAILURE,
                        "Timed out waiting to reconnect to network");
                setupMetric.secureSetup(_isSecureSetup);
                sendToMetricsManager(setupMetric);
            }
        }, timeoutInSeconds * 1000);

        // Listen for the network state change
        connectivity.registerListener(connectivityListener);

        // Return an AylaAPIRequest that can be canceled
        return new AylaAPIRequest<EmptyResponse>(Request.Method.GET, null, null,
                EmptyResponse.class, null, successListener, errorListener) {
            @Override
            public void cancel() {
                super.cancel();
                connectivity.unregisterListener(connectivityListener);
                timeoutHandler.removeCallbacksAndMessages(null);
            }
        };
    }

    /**
     * Connects the mobile device to the WiFi network it was connected to when we joined the
     * device's access point. If a previous call to
     * {@link #connectDeviceToService}
     * was not made prior to calling this method, it will fail with a PreconditionError.
     *
     * @param timeoutInSeconds Maximum time to spend trying to reconnect. A TimeoutError will be
     *                         delivered to the ErrorListener if this time is exceeded before we
     *                         are able to join the network.
     * @param successListener  Listener called when we have successfully re-joined the network
     * @param errorListener    Listener called if we fail to join the network in the specified time
     *                         period.
     * @return the AylaAPIRequest that may be canceled to stop this operation
     */
    public AylaAPIRequest reconnectToOriginalNetwork(int timeoutInSeconds,
                                                     final Listener<EmptyResponse> successListener,
                                                     final ErrorListener errorListener) {
        // _currentNetworkInfo will be null here if we have already re-joined the AP due to
        // dropping off of the device's AP.
        // Are we already on the right network?
        final WifiManager wifiManager =
                (WifiManager) _context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (_currentNetworkInfo == null || wifiManager.getConnectionInfo()
                .getSSID().equals(_currentNetworkInfo.getSSID())) {
            successListener.onResponse(new EmptyResponse());
            return null;
        }

        return connectToNetwork(_currentNetworkInfo.getSSID(),timeoutInSeconds,successListener,
                errorListener);
    }

    /**
     * disconnectAPMode method is used to Shut down AP Mode on the module. This method is typically
     * not used by the Client Apps as the AP Mode will be shut down automatically in 30 seconds
     * when the device is connected to service. Method only works when android device is
     * connected to module. On success, an HTTP status of “204 No Content” is returned.
     * Otherwise, an HTTP status of “403 Forbidden” is returned. AP mode disconnect command is
     * only accepted when module is still in AP mode, and successfully connected to
     * service in STA mode (i.e STA mode and AP mode are active at the same time). This command
     * turns off the AP mode, leaving STA mode active only.
     *
     * @param successListener Listener called When the disconnect AP Mode is success
     * @param errorListener   Listener called in case of errors
     * @return the AylaAPIRequest for this request
     */
    public AylaAPIRequest disconnectAPMode(final Listener<EmptyResponse> successListener,
                                           final ErrorListener errorListener) {
        if (_setupDevice == null) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "No setup device found");
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No setup device found"));
                AylaSetupMetric setupMetric = new AylaSetupMetric(AylaMetric.LogLevel.INFO,
                        AylaSetupMetric.MetricType.SETUP_FAILURE, "disconnectAPMode",
                        _setupSessionId, AylaMetric.Result.FAILURE, "No setup device found");
                setupMetric.secureSetup(_isSecureSetup);
                sendToMetricsManager(setupMetric);
            }
            return null;
        }
        if (!_setupDevice.hasFeature(AylaSetupDevice.FEATURE_AP_STA)) {
            AylaLogService.addLog(LOG_TAG, "Error", String.valueOf(System.currentTimeMillis()),
                    "Device does not support AP/STA Feature");
            if (errorListener != null) {
                errorListener.onErrorResponse(new
                        PreconditionError("Device does not support AP/STA Feature"));
            }
            return null;
        }

        if(!_isSecureSetup){
            AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<>(
                    Request.Method.PUT,
                    formatLocalUrl("wifi_stop_ap.json"),
                    null,
                    EmptyResponse.class,
                    null,
                    successListener,
                    errorListener);

            // We don't have a session to use, so we will send this out via the LoginManager's queue.
            AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
            return request;
        } else{
            AylaLanModule lanModule = _setupDevice.getLanModule();
            if (lanModule == null && errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError(LAN_PRECONDITION_ERROR));
                return null;
            }

            AylaLanCommand command = new AylaLanCommand("PUT", "wifi_stop_ap.json", null,
                    "/local_lan/wifi_stop_ap.json");
            AylaLanRequest request = new AylaLanRequest(_setupDevice, command, _sessionManager,
                    new Listener<AylaLanRequest.LanResponse>() {
                        @Override
                        public void onResponse(AylaLanRequest.LanResponse response) {
                            successListener.onResponse(new EmptyResponse());
                            AylaSetupMetric setupMetric = new AylaSetupMetric
                                    (AylaMetric.LogLevel.INFO,
                                            AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                            "disconnectAPMode", _setupSessionId,
                                            AylaMetric.Result.PARTIAL_SUCCESS, null);
                            setupMetric.secureSetup(_isSecureSetup);
                            sendToMetricsManager(setupMetric);
                        }
                    }, errorListener);
            lanModule.sendRequest(request);
            return request;
        }

    }


    /**
     * Exits the setup process. This call will re-connect the mobile device to the AP it was
     * connected to at the start of the setup process, and clean up any internal state information.
     * DO NOT RE-USE an AylaSetup object after its exitSetup() method is called.
     *
     * @param successListener Listener called when the operation succeeds
     * @param errorListener   Listener called if the operation encountered an error
     * @return an AylaAPIRequest that may be used to cancel this operation, or null if the
     * operation may not be canceled (e.g. we are already on the original WiFi network or the
     * original AP was not saved)
     */
    public AylaAPIRequest exitSetup(Listener<EmptyResponse> successListener,
                                    ErrorListener errorListener) {
        _sessionManager.getDeviceManager().setLanModePermitted(true);

        if (_setupDevice != null) {
            _setupDevice.stopLanSession();
            _setupDevice = null;
        }

        if (_httpServer != null) {
            _httpServer.setSetupDevice(null);
        }

        if (_scanReceiver != null) {
            try{
                _context.unregisterReceiver(_scanReceiver);
            } catch(IllegalArgumentException e){
                AylaLog.e(LOG_TAG, "Exception while unregistering scanReceiver. "
                        + e.getLocalizedMessage());
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            ConnectivityManager cm = (ConnectivityManager) _context.getSystemService(Context
                    .CONNECTIVITY_SERVICE);
            cm.bindProcessToNetwork(null);
            AylaLog.d(LOG_TAG, "Unbound process from network");
        }

        if (_currentNetworkInfo != null) {
            return reconnectToOriginalNetwork(10, successListener, errorListener);
        }

        // Forget the module AP
        deleteConfiguredNetwork(_context);

        successListener.onResponse(new EmptyResponse());
        AylaLogService.sendToLogService();
        if(_sessionManager != null && _sessionManager.getDSManager() != null){
            _sessionManager.getDSManager().onResume();
        }

        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
        if (metricsManager != null) {
            metricsManager.onResume();
        }

        return null;
    }

    /**
     * Sets the time on the setup device. This is called internally after we receive the device
     * information (status.json) as part of the
     * {@link #fetchDeviceDetail(AylaAPIRequest, Listener, ErrorListener)} method.
     *
     * @param deviceTime Time to set on the device, or null to set the current time
     * @param successListener Listener called upon success
     * @param errorListener Listener called if an error occurs
     * @return The AylaAPIRequest for this request
     */
    private AylaAPIRequest setDeviceTime(Date deviceTime,
                                         Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
                                         ErrorListener errorListener) {
        if (_setupDevice == null) {
            errorListener.onErrorResponse(new PreconditionError("No setup device found"));
            return null;
        }

        if (deviceTime == null) {
            // Use now
            deviceTime = new Date();
        }

        JSONObject time = new JSONObject();
        try {
            //Send the time in seconds.  deviceTime.getTime() gives milliseconds so divide by 1000
            // for seconds
            time.put("time", (deviceTime.getTime())/1000);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        String url = formatLocalUrl("time.json");
        AylaAPIRequest<EmptyResponse> request = new AylaJsonRequest<>(Request.Method.PUT,
                url, time.toString(), null, EmptyResponse.class, _sessionManager,
                successListener, errorListener);
        AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);

        return request;
    }

    /**
     * After connecting to the device's access point, this method is called to fetch information
     * about the device. This method is the second part of a compound API call initiated by
     * {@link #connectToNewDevice}.
     *
     * In addition to retrieving the device information, this method also sets the time on the
     * device to the time on the mobile device.
     *
     * @param originalRequest Original AylaAPIRequest (the one from connectToNewDevice)
     * @param successListener Listener to receive the AylaSetupDevice on success
     * @param errorListener   Listener to receive an AylaError if an error occurred
     * @return the AylaAPIRequest for this request
     */
    private AylaAPIRequest fetchDeviceDetail(AylaAPIRequest originalRequest,
                                             final Listener<AylaSetupDevice> successListener,
                                             final ErrorListener errorListener) {

        AylaLog.d(LOG_TAG, "fetchDeviceDetail");

        String url = "http://" + _setupDeviceIp + "/status.json";
        AylaAPIRequest<AylaSetupDevice> request = new AylaAPIRequest<AylaSetupDevice>(
                Request.Method.GET,
                url,
                null,
                AylaSetupDevice.class,
                null,
                successListener,
                errorListener) {
            // Override parseNetworkResponse instead of deliverResponse so we do our key
            // generation on a background thread instead of the UI thread. It can take some time,
            // especially on older / slower devices.
            @Override
            protected Response<AylaSetupDevice>
            parseNetworkResponse(NetworkResponse networkResponse) {
                // Get the actual response from the superclass
                Response<AylaSetupDevice> response = super.parseNetworkResponse(networkResponse);
                if (!response.isSuccess()) {
                    AylaLog.e(LOG_TAG, "Error fetching device detail: " + response.error);
                    return response;
                }

                // Save and configure the new device before we hand it back to the caller
                _setupDevice = response.result;
                _setupDevice.setLanIp(_setupDeviceIp);

                //Now we have the new device's DSN. Set it in AylaLogService
               // Todo: AylaLogService.setDsn(_setupDevice.getDsn());
                AylaLog.d(LOG_TAG, "fetchDeviceDetail success. Starting LAN mode ");
                //Check for edge case when httpServer is null. This is a rare case
                if (_httpServer == null) {
                    AylaLog.e(LOG_TAG, "httpServer is null");
                    return Response.error(new VolleyError(new InternalError("httpServer is null")));
                }
                startSetupDeviceLanSession();

                // Set the device time to the current time. We won't fail if this call fails, so we
                // can ignore the results.
                EmptyListener<EmptyResponse> emptyListener = new EmptyListener<>();
                setDeviceTime(null, emptyListener, emptyListener);

                return response;
            }

            @Override
            protected VolleyError parseNetworkError(VolleyError error) {
                if(error.networkResponse != null && error.networkResponse.statusCode == 404){
                    // start key exchange
                    AylaLog.d(LOG_TAG, "fetchDeviceDetail returned 404 Starting LAN mode ");
                    if(_httpServer != null){
                        startSetupDeviceLanSession();
                    }
                }
                return super.parseNetworkError(error);
            }
        };

        //This is to handle case where raspberry Pi devices do not respond to
        //+requests made immediately after connecting to them in AP mode.
        AylaSystemSettings settings = AylaNetworks.sharedInstance().getSystemSettings();
        request.setRetryPolicy(new DefaultRetryPolicy(settings.defaultNetworkTimeoutMs, 3, 1f));

        // This is a compound request- we need to keep the chain going so canceling the original
        // request will cancel this new request.
        if (originalRequest != null) {
            if (originalRequest.isCanceled()) {
                request.cancel();
            } else {
                originalRequest.setChainedRequest(request);
                // We don't have a session to use, so we will send this out via the LoginManager's
                // queue.
                AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
            }
        } else {
            AylaNetworks.sharedInstance().getLoginManager().sendUserServiceRequest(request);
        }

        return request;
    }


    // Start Lan mode on the setup device
    private void startSetupDeviceLanSession(){

        // For secure setup _setupDevice will be null at this stage.
        if(_setupDevice == null){
            _setupDevice = new AylaSetupDevice();
        }
        _setupDevice.setLanIp(_setupDeviceIp);
        // Generate the RSA key pair for secure setup. This can be processor-intensive,
        // which is why we are doing this in parseNetworkResponse which is executed on
        // the networking thread.
        AylaSetupCrypto setupCrypto = new AylaSetupCrypto();
        setupCrypto.generateKeyPair();
        AylaLanConfig lanConfig = new AylaLanConfig(setupCrypto);
        _setupDevice.setLanConfig(lanConfig);

        // Let the HTTP server know about our setup device and kick off LAN mode
        _httpServer.setSetupDevice(_setupDevice);
        _setupDevice.startLanSession(_httpServer);

    }

    /**
     * Internal method to create a URL string for the setup device in AP mode
     *
     * @param url URL path
     * @return a formatted URL string pointing to the setup device
     */
    private String formatLocalUrl(String url) {
        return "http://" + _setupDeviceIp + "/" + url;
    }

    /**
     * Class holding information returned from a fetchRegistrationInfo() call
     */
    public static class RegistrationInfo {
        @Expose
        public String regtoken;
        @Expose
        public int registered;
        @Expose
        public String registrationType;
        @Expose
        public String host_symname;
    }

    /**
     * Helper class used to store cleanup information during the compound request to connect to
     * the new device
     */
    private static class ConnectRequest extends AylaAPIRequest<EmptyResponse> {
        public AylaConnectivity.AylaConnectivityListener _connectivityListener;
        public Handler _timeoutHandler;

        public ConnectRequest(Listener<EmptyResponse> successListener,
                              ErrorListener errorListener) {
            super(Request.Method.GET,
                    null,
                    null,
                    EmptyResponse.class,
                    null,
                    successListener, errorListener);
        }

        @Override
        public void cancel() {
            AylaNetworks.sharedInstance().getConnectivity()
                    .unregisterListener(_connectivityListener);

            _timeoutHandler.removeCallbacksAndMessages(null);

            super.cancel();
        }

        @Override
        protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
            return Response.success(new EmptyResponse(), null);
        }
    }

    /**
     * Internal class used to receive broadcast messages related to the WiFi scan for access points
     */
    private class ScanReceiver extends BroadcastReceiver {
        final List<AylaAPIRequest<ScanResult[]>> _requests;

        public ScanReceiver() {
            super();
            _requests = new ArrayList<>();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            AylaLog.i(LOG_TAG, "ScanReceiver onReceive: " + intent);

            // Get the list of APs
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> results = wifiManager.getScanResults();
            ScanResult[] resultArray = results.toArray(new ScanResult[results.size()]);
            synchronized (_requests) {
                for (AylaAPIRequest<ScanResult[]> request : _requests) {
                    request.getSuccessListener().onResponse(resultArray);
                }
                _requests.clear();
            }
        }

        public void addRequest(AylaAPIRequest<ScanResult[]> request) {
            synchronized (_requests) {
                _requests.add(request);
            }
        }

        public void removeRequest(AylaAPIRequest<ScanResult[]> request) {
            synchronized (_requests) {
                _requests.remove(request);
            }
        }
    }

    /**
     * DeviceWifiStateChangeListener is used to provide updates in the state of a device
     * during setup process.  If no state is returned from the module, a string value "unknown"is
     * returned. State values returned from Ayla devices are "disabled", "down",
     * "wifi_connecting", "network_connecting", "cloud_connecting",
     * and "up"
     */
    public interface DeviceWifiStateChangeListener{
        /**
         * Called when there ia a change in the device's wifi setup state.
         * @param currentState the last fetched wifi setup state.
         */
        void wifiStateChanged(String currentState);
    }

    private void updateAndNotifyState(String state){
        if(state == null){
            state = UNKNOWN_STATE;
        }
        if(!state.equals(_lastWifiState)){
            _lastWifiState = state;
            notifyWifiStateListeners(_lastWifiState);
        }

    }

    /**
     * Fetches device details in LAN mode
     *
     */
    private AylaAPIRequest fetchDeviceDetailsLAN(final AylaAPIRequest originalRequest, final Response
            .Listener<AylaSetupDevice> successListener, final ErrorListener errorListener){
        AylaLog.d(LOG_TAG, "fetchDeviceDetailsLAN");

        if(originalRequest.isCanceled()){
            return null;
        }
        
        if(_setupDevice == null){
            return null;
        }

        final Date startTime = new Date();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!_setupDevice.isLanModeActive()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                    if ((new Date().getTime() - startTime.getTime()) > 15000) {
                        originalRequest.cancel();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                errorListener.onErrorResponse(new PreconditionError("Setup device failed " +
                                        "to start LAN mode. Cannot complete setup"));
                                AylaSetupMetric setupMetric = new AylaSetupMetric(
                                        AylaMetric.LogLevel.INFO,
                                        AylaSetupMetric.MetricType.SETUP_FAILURE,
                                        "fetchDeviceDetailsLAN", _setupSessionId,
                                        AylaMetric.Result.FAILURE, null);
                                setupMetric.setMetricText("Phone connected to device. Setup device " +
                                        "failed to start LAN mode. Cannot complete setup");
                                sendToMetricsManager(setupMetric);

                            }
                        });
                        break;
                    }
                }
            }
        });
        thread.start();

        final AylaLanCommand cmd = new AylaLanCommand("GET", "status.json", null,
                "/local_lan/status.json");
        AylaLanRequest request = new AylaLanRequest(_setupDevice, cmd,
                _setupDevice.getSessionManager(),
                new Response.Listener<AylaLanRequest.LanResponse>() {
                    @Override
                    public void onResponse(AylaLanRequest.LanResponse response) {
                        AylaError error = cmd.getResponseError();
                        if (error != null) {
                            AylaLog.d(LOG_TAG, "fetch device details command returned error "+
                                    error.getMessage());
                            errorListener.onErrorResponse(error);
                            return;
                        }
                        AylaLog.d(LOG_TAG, "Fetch device details command response "+cmd
                                .getModuleResponse());
                        successListener.onResponse(_setupDevice);
                        AylaSetupMetric setupMetric = new AylaSetupMetric(
                                AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_SUCCESS,
                                "fetchDeviceDetailsLAN", _setupSessionId,
                                AylaMetric.Result.PARTIAL_SUCCESS, null);
                        setupMetric.secureSetup(_isSecureSetup);
                        sendToMetricsManager(setupMetric);

                    }
                }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "Error in fetching device details "+error.getMessage());
                errorListener.onErrorResponse(error);
                AylaSetupMetric setupMetric = new AylaSetupMetric(
                        AylaMetric.LogLevel.INFO, AylaSetupMetric.MetricType.SETUP_FAILURE,
                        "fetchDeviceDetailsLAN", _setupSessionId,
                        AylaMetric.Result.PARTIAL_SUCCESS, error.getMessage());
                setupMetric.setMetricText("Phone connected to device. fetchDeviceDetailsLAN failed ");
                sendToMetricsManager(setupMetric);
            }
        });

        if (originalRequest != null) {
            if (originalRequest.isCanceled()) {
                request.cancel();
                return originalRequest;
            } else {
                originalRequest.setChainedRequest(request);
            }
        }

        if (_setupDevice.getLanModule() != null) {
            _setupDevice.getLanModule().sendRequest(request);
        } else {
            errorListener.onErrorResponse(new PreconditionError(LAN_PRECONDITION_ERROR));
        }

        return originalRequest;
    }

    private void setWifiSecurity(WifiConfiguration config, WifiSecurityType security, final String password) {
        AylaLog.d(LOG_TAG, "Security type: "+security.stringValue());
        switch (security){
            case WEP:

                if (!TextUtils.isEmpty(password)) {
                    if (isHexWepKey(password)) {
                        config.wepKeys[0] = password;
                    } else {
                        config.wepKeys[0] = ObjectUtils.quote(password);
                    }
                }
                config.wepTxKeyIndex = 0;
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                break;
            case WPA:
            case WPA2:
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                config.allowedProtocols.set(security == (WifiSecurityType.WPA2) ? WifiConfiguration.Protocol.RSN :
                        WifiConfiguration.Protocol.WPA);
                config.preSharedKey = ObjectUtils.quote(password);
                break;
            case NONE:
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;

        }
    }

    private static Boolean isHexWepKey(String wepKey) {
        final int len = wepKey.length();

        // WEP-40, WEP-104, and some vendors using 256-bit WEP (WEP-232?)
        if (len != 10 && len != 26 && len != 58) {
            return false;
        }
        return isHex(wepKey);
    }

    private static Boolean isHex(String key) {
        for (int i = key.length() - 1; i >= 0; i--) {
            final char c = key.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private static void sendToMetricsManager(AylaMetric logMessage){
        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
        if (metricsManager != null) {
            metricsManager.addMessageToUploadsQueue(logMessage);
        } else {
            AylaLog.d(LOG_TAG, "metricsManager is null, ignore metric message " + logMessage);
        }
    }

    private int getNetworkIdBySSIDName(WifiManager wifiManager,
                                       String unquotedNetworkSSID) throws SecurityException {
        int netId = NETID_UNKNOWN;
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        if (configs != null && unquotedNetworkSSID != null) {
            for (WifiConfiguration config : configs) {
                if (unquotedNetworkSSID.equals(ObjectUtils.unquote(config.SSID))) {
                    netId = config.networkId;
                    break;
                }
            }
        }

        return netId;
    }
}
