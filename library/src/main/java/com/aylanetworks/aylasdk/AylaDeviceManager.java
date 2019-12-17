package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HurlStack;
import com.aylanetworks.aylasdk.change.ListChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanConfig;
import com.aylanetworks.aylasdk.setup.AylaRegistration;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;
import com.aylanetworks.aylasdk.util.AylaPredicate;
import com.aylanetworks.aylasdk.util.NetworkUtils;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.aylanetworks.aylasdk.util.TypeUtils;
import com.aylanetworks.aylasdk.util.URLHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aylanetworks.aylasdk.AylaAlertHistory.*;

/**
 * AylaDeviceManager is responsible for maintaining the list of devices from the Ayla cloud
 * service. It will poll for updates to the list of devices, and will notify listeners if
 * the list changes.
 * <p>
 * After sign-in, the AylaSessionManager that was created as the result of a successful sign-in
 * operation will contain an AylaDeviceManager object for the session. This may be accessed via
 * the {@link AylaSessionManager#getDeviceManager()} method.
 * <p>
 * Application developers will usually register a {@link DeviceManagerListener}
 * with AylaDeviceManager as soon as the user has signed in and the {@link AylaSessionManager} was
 * created.
 * <p>
 * Once a listener has been registered, applications should wait for the {@link com.aylanetworks
 * .aylasdk.AylaDeviceManager.DeviceManagerListener#deviceManagerInitComplete} method to be called,
 * which indicates the AylaDeviceManager has successfully fetched the list of devices and their
 * properties, LAN configuration information, etc. At that point, the list of devices may be
 * obtained via {@link #getDevices()}, and each {@link AylaDevice} object may be interacted with.
 * <p>
 * If the AylaDeviceManager cannot retrieve the device list or encounters unrecoverable errors
 * during intialization, the listeners'
 * {@link DeviceManagerListener#deviceManagerInitFailure} method will instead
 * be called with the specific {@link AylaError} that was encountered as well as the
 * {@link DeviceManagerState} the manager was in when the error occurred.
 */
public class AylaDeviceManager implements AylaConnectivity.AylaConnectivityListener {
    /**
     * Tag for log messages produced by AylaDeviceManager
     */
    final private static String LOG_TAG = "AylaDeviceManager";

    /**
     * Default poll time interval of 15 seconds
     */
    final private static int DEFAULT_POLL_INTERVAL_MS = 15000;

    /**
     * Request queue for device service messages
     */
    final private RequestQueue _deviceRequestQueue;

    /**
     * Map of device DSNs to devices. This is where we store the master device list.
     */
    final private Map<String, AylaDevice> _deviceHashMap;
    /**
     * Set of DeviceManagerListeners to be notified of changes
     */
    final private Set<DeviceManagerListener> _deviceManagerListeners;
    /**
     * Set of DSNs that encountered errors during device manager initialization
     */
    final private Map<String, AylaError> _deviceInitErrors;
    /**
     * Device currently being set up. This is set when we are performing WiFi Setup only.
     */
    private AylaSetupDevice _setupDevice;
    /**
     * Handler for queueing poll messages
     */
    private Handler _pollTimerHandler;

    /**
     * Time between calls to the server to fetch the current device list
     */
    private int _pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

    /**
     * True if we are polling the service for device list changes
     */
    private boolean _isPolling = false;

    /**
     * True if the device list returned from AylaDeviceManager is a cached list, and might be stale.
     */
    private boolean _isCachedDeviceList = false;


    /**
     * HTTP server for LAN mode incoming requests
     */
    private AylaHttpServer _lanServer;

    /**
     * State of the DeviceManager. On startup, the DeviceManager goes through several states to
     * fetch the list of devices and their properties. When all devices have been updated, the
     * state moves to Ready and listeners are notified that initialization is complete.
     */
    private DeviceManagerState _state = DeviceManagerState.Uninitialized;

    /**
     * Weak reference to the AylaSessionManager that owns this object.
     */
    private WeakReference<AylaSessionManager> _sessionManagerRef;

    /**
     * Set to true once we have gone through the initialization process once. This is useful for
     * when we have to run through the init phases again, like when devices are added or we come
     * back from the background, and need to know if we have fetched a good device list or not.
     */
    private boolean _hasInitialized;

    /**
     * The status code 201 is returned in each of the Batch Data Point Response on success
     */
    final private static int DATAPOINT_CREATED_SUCCESS = 201;

    /**
     * Package-private constructor. Only the SessionManager should create a Device manager.
     */
    AylaDeviceManager(AylaSessionManager sessionManager) {
        _sessionManagerRef = new WeakReference<>(sessionManager);

        Context context = AylaNetworks.sharedInstance().getContext();
        Cache cache = new DiskBasedCache(context.getCacheDir(), 1024 * 1024);

        // Set up the HTTPURLConnection network stack
        Network network = new BasicNetwork(new HurlStack());

        // Create and start our queue
        _deviceRequestQueue = new RequestQueue(cache, network);
        _deviceRequestQueue.start();

        // Create our device maps
        _deviceHashMap = new HashMap<>();

        // Create our set of listeners
        _deviceManagerListeners = new HashSet<>();

        // Create our list of errored DSNs
        _deviceInitErrors = new HashMap<>();

        // Create our handler for the poll timer
        _pollTimerHandler = new Handler(AylaNetworks.sharedInstance().getContext()
                .getMainLooper());

        startWebserver();

        // Kick things off by fetching the current list of devices
        fetchDevices();
    }

    /**
     * Returns the current list of devices. The returned list is a copy and may be sorted or
     * modified by the caller.
     *
     * @return A list of AylaDevices known to the DeviceManager.
     */
    public List<AylaDevice> getDevices() {
        synchronized (_deviceHashMap) {
            // Return a copy of our list
            return new ArrayList<>(_deviceHashMap.values());
        }
    }

    /**
     * Returns the current list of devices filtered with the specified predicate.
     *
     * @param predicate Predicate determining whether or not to return the device in the list
     * @return List of devices filtered with the given predicate
     */
    public List<AylaDevice> getDevices(AylaPredicate<AylaDevice> predicate) {
        List<AylaDevice> results = new ArrayList<>();
        synchronized (_deviceHashMap) {
            for (AylaDevice device : _deviceHashMap.values()) {
                if (predicate.test(device)) {
                    results.add(device);
                }
            }
        }
        return results;
    }

    /**
     * Returns a list of all gateways registered to the account
     *
     * @return A list of all gateways registered to the account
     */
    public List<AylaDeviceGateway> getGateways() {
        List<AylaDeviceGateway> gateways = new ArrayList<>();
        synchronized (_deviceHashMap) {
            for (AylaDevice device : _deviceHashMap.values()) {
                if (device.isGateway()) {
                    gateways.add((AylaDeviceGateway) device);
                }
            }
        }
        return gateways;
    }

    /**
     * @return the SessionManager that owns this object
     */
    public AylaSessionManager getSessionManager() {
        return _sessionManagerRef.get();
    }

    /**
     * Adds the listener to the list of objects to be notified of DeviceManager changes.
     *
     * @param listener Listener to be notified
     */
    public void addListener(DeviceManagerListener listener) {
        synchronized (_deviceManagerListeners) {
            _deviceManagerListeners.add(listener);
        }
    }

    /**
     * Removes the listener from the list of objects to be notified of DeviceManager changes.
     *
     * @param listener Listener to be removed
     */
    public void removeListener(DeviceManagerListener listener) {
        synchronized (_deviceManagerListeners) {
            _deviceManagerListeners.remove(listener);
        }
    }

    /**
     * Returns the current state of the DeviceManager.
     *
     * @return The current state of the DeviceManager.
     */
    public DeviceManagerState getState() {
        return _state;
    }

    /**
     * Sets the current state of AylaDeviceManager and notifies any listeners if the state changed.
     *
     * @param state State to set AylaDeviceManager to
     */
    private synchronized void setState(DeviceManagerState state) {
        if (state != _state) {
            if (state == DeviceManagerState.Ready) {
                // This is now set and will not be cleared.
                _hasInitialized = true;
            }

            DeviceManagerState oldState = _state;
            _state = state;
            AylaLog.v(LOG_TAG, "DeviceManager: " + oldState + " --> " + _state);
            notifyStateChange(oldState, _state);
            if (_state == DeviceManagerState.Ready) {
                notifyInitComplete();
            }
        }
    }

    /**
     * Returns the managed AylaDevice object with the given DSN, or null if no device was found.
     *
     * @param dsn The DSN of the device to retrieve
     * @return The AylaDevice with the given DSN, or null if not found
     */
    public AylaDevice deviceWithDSN(String dsn) {
        if ( _setupDevice != null && TextUtils.equals(dsn, _setupDevice.getDsn())) {
            return _setupDevice;
        }

        synchronized (_deviceHashMap) {
            return _deviceHashMap.get(dsn);
        }
    }

    /**
     * Returns the managed AylaDevice object with the given LAN IP address, nor null if no device
     * was found.
     * <p>
     * Since it is possible to have multiple devices with the same IP address, for example on
     * different networks, or devices that are shared with this account, this method will return
     * an AylaDevice only if exactly one device owned by this account (not shared with this
     * account) has the specified IP address. Otherwise null will be returned.
     * <p>
     * This means that devices shared with this account cannot be returned from this method, and
     * therefore cannot enter LAN mode.
     *
     * @param lanIP The LAN IP address as a dotted string
     * @return The AylaDevice with the given IP address, or null if not found
     */
    public AylaDevice deviceWithLanIP(String lanIP) {
        if ( _setupDevice != null ) {
            if (TextUtils.equals(_setupDevice.getLanIp(), lanIP)) {
                return _setupDevice;
            }
            // If we have a setup device, we can't look at other devices for LAN connections as
            // we are most likely not on the same network, but connected to the setup device's AP.
            return null;
        }

        synchronized (_deviceHashMap) {
            boolean allowShared = AylaNetworks.sharedInstance().getSystemSettings()
                    .allowLANConnectionToSharedDevices;

            for (AylaDevice device : _deviceHashMap.values()) {
                if (TextUtils.equals(device.getLanIp(), lanIP)) {
                    // Found a match. Is this device shared with me?
                    if (!allowShared) {
                        if (device.getGrant() != null) {
                            continue;
                        }
                    }

                    // LAN mode is temporarily disabled on devices that fail key negotiation.
                    // This prevents devices with the same LAN IP address from "answering" to
                    // this call to identify them by IP.
                    if (device.isLANTemporarilyDisabled()) {
                        // This device attempted LAN mode but failed due to a key mismatch.
                        continue;
                    }

                    return device;
                }
            }
        }

        return null;
    }

    /**
     * Fetches the list of devices from the server and merges them into our set. Listeners will be
     * notified if the device list changes as a result of this operation.
     * <p>
     * Generally this method does not need to be called by the application; the DeviceManager will
     * automatically keep this list up-to-date by polling the service and notify all listeners
     * of any changes that occur.
     */
    public void fetchDevices() {
        // Unless we are in the "ready" state already, we need to update our state.
        boolean startedFetch = false;
        if (getState() != DeviceManagerState.Ready) {
            setState(DeviceManagerState.FetchingDeviceList);
        }

        String url = deviceServiceUrl("apiv1/devices.json");


        //If app is already in LAN login session, load device list and do not wait for fetch to
        // complete
        if(getSessionManager().isCachedSession() && !isCachedDeviceList()){
            startedFetch = loadCachedDevices();
        }

        // The service returns the device list as an array of objects called "device". We need to
        // unwrap the devices from this structure when we receive them from the service. We use
        // the AylaDeviceWrapper class to take care of this for us.
        final boolean finalStartedFetch = startedFetch;
        AylaAPIRequest<AylaDevice.Wrapper[]> request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaDevice.Wrapper[].class,
                getSessionManager(),
                new Response.Listener<AylaDevice.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDevice.Wrapper[] wrappedDevices) {

                        setIsCachedDeviceList(false);
                        if(getSessionManager().isCachedSession()){
                            getSessionManager().setCachedSession(false);
                        }
                        // Remove the wrapper
                        AylaDevice[] devices = AylaDevice.Wrapper.unwrap(wrappedDevices);

                        // Merge the list into our own. This may cause a state change if there
                        // are new devices in the list that need to be updated.
                        mergeDevices(devices);
                        AylaLog.v(LOG_TAG, "DeviceManager merged device list");

                        // If we are in the FetchingDeviceList state, we now need to fetch
                        // properties.
                        if (getState() == DeviceManagerState.FetchingDeviceList) {
                            fetchProperties();
                        } else {
                            // We are in the ready state. If we're polling, post a new message.
                            continuePolling();
                        }
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        AylaLog.e(LOG_TAG, "fetchDevices returned " + error.getMessage());
                        boolean returnError = false;

                        //Check if already fetched
                        if(finalStartedFetch){
                           //SuccessListener was already called with cached values
                            returnError = false;
                        } else{
                            boolean tryOfflineUse =
                                    error.getErrorType() == AylaError.ErrorType.NetworkError ||
                                            error.getErrorType() == AylaError.ErrorType.Timeout;
                            if(tryOfflineUse && getState() != DeviceManagerState.Ready &&
                                    AylaNetworks.sharedInstance().getSystemSettings().
                                            allowOfflineUse) {
                                //get cached device list
                                if(!isCachedDeviceList()){
                                    boolean loadCachedDevices = loadCachedDevices();
                                    returnError = !loadCachedDevices;
                                }

                            } else{
                                returnError = true;
                            }
                        }

                        if(returnError){
                            setIsCachedDeviceList(false);
                            notifyError(error);
                            // If we already have initialized, we keep polling even if we encounter
                            // an error. This is normal if we are not connected to a network.
                            if (hasInitialized()) {
                                continuePolling();
                            } else {
                                // We could not get the device list during initialization. This is a
                                // critical failure.
                                setState(DeviceManagerState.Error);
                                notifyInitFailure(error, DeviceManagerState.FetchingDeviceList);
                            }
                        } else{
                            setIsCachedDeviceList(true);
                        }
                    }
                });

        sendDeviceServiceRequest(request);
    }


    private boolean loadCachedDevices(){
        AylaCache cache = getSessionManager().getCache();
        String key = cache.getKey(AylaCache.CacheType.DEVICE, "");
        String devicesFromCache = cache.getData(key);
        AylaLog.d("LAN_Login", "devicesFromCache "+devicesFromCache);
        if (devicesFromCache != null) {
            Gson gson = AylaNetworks.sharedInstance().getGson();
            AylaDevice[] devices = gson.fromJson(devicesFromCache, AylaDevice[].class);
            setIsCachedDeviceList(true);
            if(devices != null) {
                initializeFromCache(devices);
                return true;
            }
        }
        return false;
    }

    private void initializeFromCache(AylaDevice[] devices){
        String propKey;
        String lanConfigKey;
        AylaCache cache = getSessionManager().getCache();
        Gson gson = AylaNetworks.sharedInstance().getGson();
        for(AylaDevice device: devices){
            device.setDeviceManager(this);
            device.stopPolling();
            _deviceHashMap.put(device.getDsn(), device);
            propKey = cache.getKey(AylaCache.CacheType.PROPERTY,device.getDsn());
            lanConfigKey = cache.getKey(AylaCache.CacheType.LAN_CONFIG, device.getDsn());

            String cachedProperties = cache.getData(propKey);
            AylaProperty properties[] = gson.fromJson(cachedProperties, AylaProperty[].class);

            String cachedlanConfig = cache.getData(lanConfigKey);
            AylaLanConfig lanConfig = gson.fromJson(cachedlanConfig, AylaLanConfig.class);

            if(properties != null) {
                for (AylaProperty property : properties) {
                    String propertyName = property.getName();
                    property.setOwner(device);
                    device._propertyMap.put(propertyName, property);
                    property.setLastUpdateSource(AylaDevice.DataSource.CACHED);
                }
            }
            device._lanConfig = lanConfig;
            device.startLanSession(getLanServer());
            device.setLastUpdateSource(AylaDevice.DataSource.CACHED);

        }
        setState(DeviceManagerState.Ready);

        continuePolling();
    }

    /**
     * Internal method used to continue polling for the device list after receiving a response from
     * the server.
     */
    private void continuePolling() {
        if (_isPolling) {
            _pollTimerHandler.removeCallbacksAndMessages(null);
            _pollTimerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AylaLog.v(LOG_TAG, "Device list poll timer hit");
                    fetchDevices();
                }
            }, getPollInterval());
        }
    }

    /**
     * Starts polling operations. This includes polling of the device service for updates to the
     * device list as well as polling each device's properties for changes.
     * <p>
     * Devices that are not allowed to poll (isPollingPermitted() returns false) or devices
     * that are already in LAN mode will not start polling as a result of this operation.
     */
    public void startPolling() {
        _isPolling = true;
        synchronized (_deviceHashMap) {
            for (AylaDevice device : _deviceHashMap.values()) {
                device.startPolling();
            }
        }
        fetchDevices();
    }

    /**
     * Stops polling operations. Any devices that are polling will be stopped, and the
     * DeviceManager will stop polling for device list changes.
     */
    public void stopPolling() {
        _pollTimerHandler.removeCallbacksAndMessages(null);
        _isPolling = false;
        synchronized (_deviceHashMap) {
            for (AylaDevice device : _deviceHashMap.values()) {
                device.stopPolling();
            }
        }
    }

    /**
     * @return true if the DeviceManager is polling for device list changes
     */
    public boolean isPolling() {
        return _isPolling;
    }

    /**
     * @return the time in milliseconds between polls of the service for the list of devices
     * registered to the active account
     */
    public int getPollInterval() {
        return _pollIntervalMs;
    }

    /**
     * Sets the poll interval to the specified time in milliseconds. If polling is currently
     * active, this will cause an immediate fetch of the device list. Subsequent fetches will
     * happen after the specified time period.
     *
     * @param timeMs Time in milliseconds between calls to fetch the device list while polling
     */
    public void setPollInterval(int timeMs) {
        _pollIntervalMs = timeMs;
        if (_isPolling) {
            stopPolling();
            startPolling();
        }
    }

    /**
     * Returns the IP address of the LAN server. This is the mobile device's WiFi IP address.
     * @return the IP address of the LAN server
     */
    public String getLANServerIP() {
        if (_lanServer == null) {
            return null;
        }
        Context context = AylaNetworks.sharedInstance().getContext();
        return NetworkUtils.getWifiIpAddress(context);
    }

    /**
     * Returns the port the LAN server is listening on
     * @return the port the LAN server is listening on, or 0 if the LAN server is not running
     */
    public int getLANServerPort() {
        if (_lanServer == null) {
            return 0;
        }
        return _lanServer.getListeningPort();
    }

    public AylaHttpServer getLanServer() {
        return _lanServer;
    }

    /**
     * Internal method used to fetch the properties for each device we are managing. This method
     * should only be called as part of initialization.
     */
    private void fetchProperties() {
        if (getState() == DeviceManagerState.Ready) {
            AylaLog.e(LOG_TAG, "DeviceManager calling fetchProperties when we are already " +
                    "initialized");
            return;
        }

        // Clear out our list of DSNs that encountered errors during initialization
        _deviceInitErrors.clear();
        setState(DeviceManagerState.FetchingDeviceProperties);

        fetchNextDeviceProperties();
    }

    /**
     * Used internally during initialization, this method finds a device from the device list
     * that needs its properties fetched from the cloud service. If a device encounters an error
     * when its properties are fetched, the device will not be re-tried, but its DSN will be
     * passed to the call to deviceManagerInitComplete() along with the error that occurred.
     * <p>
     * This allows the DeviceManager to complete initialization even if all devices do not
     * respond properly to queries for their property values.
     */
    private void fetchNextDeviceProperties() {
        // Find a device that has not had its properties fetched yet
        boolean foundOne = false;
        synchronized (_deviceHashMap) {
            for (final AylaDevice device : _deviceHashMap.values()) {
                // We are looking for devices that do not have any properties yet, and
                // have not encountered errors from a previous fetch.
                if (device.getProperties().size() == 0 &&
                        device.getHasProperties() != null &&
                        device.getHasProperties() &&
                        !_deviceInitErrors.containsKey(device.getDsn())) {
                    foundOne = true;

                    AylaLog.d(LOG_TAG, "Fetching properties for " + device.getDsn());
                    // We need to fetch the properties for this device

                    // Find the list of property names for this device from the system settings.
                    // It may be null, that's OK- we will fetch all of the properties in that case.
                    String[] propertyNames = null;
                    AylaSystemSettings.DeviceDetailProvider provider =
                            AylaNetworks.sharedInstance().getSystemSettings().deviceDetailProvider;

                    if (provider != null) {
                        propertyNames = provider.getManagedPropertyNames(device);
                    }

                    device.fetchProperties(propertyNames, new Response.Listener<AylaProperty[]>() {
                                @Override
                                public void onResponse(AylaProperty[] response) {
                                    // Properties have already been merged by the call to device
                                    // .fetchProperties.
                                    AylaLog.d(LOG_TAG, "Got properties for " + device.getDsn());

                                    // Check to see if zero properties were returned- we need to
                                    // flag that as an error or else we will continue to try to
                                    // fetch properties for this device
                                    if (device.getProperties().size() == 0) {
                                        _deviceInitErrors.put(device.getDsn(),
                                                new com.aylanetworks.aylasdk.error.InternalError
                                                        ("No properties found for this device"));
                                    }
                                    fetchNextDeviceProperties();
                                }
                            },
                            new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    AylaLog.e(LOG_TAG, "Error fetching properties for " + device
                                            .getDsn());
                                    // Set the error on the DSN in our map
                                    // We know that put() will return a throwable AylaError if
                                    // one already existed in the map, but we
                                    // don't want to throw it. Disable inspection.
                                    //noinspection ThrowableResultOfMethodCallIgnored
                                    _deviceInitErrors.put(device.getDsn(), error);
                                    fetchNextDeviceProperties();

                                }
                            });
                    break;
                }
            }
        }
        if (!foundOne) {
            // We're done. Move on to the next state.
            setState(DeviceManagerState.FetchingLanConfig);
            fetchLanConfig();
        }
    }

    /**
     * Internal method used by DeviceManager to kick off a fetch of the LAN configuration
     * information for each device
     */
    void fetchLanConfig() {
        if (getState() == DeviceManagerState.Ready) {
            AylaLog.e(LOG_TAG, "DeviceManager calling fetchProperties when we are already " +
                    "initialized");
            return;
        }

        fetchNextDeviceLanConfig();
    }

    /**
     * Internal method used by DeviceManager to fetch the LAN configuration information for the
     * next device in our list that does not already have it, or has already tried to fetch it
     * but received an error.
     */
    void fetchNextDeviceLanConfig() {
        // Find a device that has not had its LAN config fetched yet
        boolean foundOne = false;
        synchronized (_deviceHashMap) {
            for (final AylaDevice device : _deviceHashMap.values()) {
                // We are looking for devices that are not nodes, do not have any properties yet,
                // and have not encountered errors from a previous fetch.
                if (!device.isNode() &&
                        device.isLanModePermitted() &&
                        device.getLanConfig() == null &&
                        !_deviceInitErrors.containsKey(device.getDsn())) {
                    foundOne = true;
                    // We need to fetch the LAN config for this device

                    AylaLog.d(LOG_TAG, "Fetching config for " + device.getDsn());
                    device.fetchLanConfig(new Response.Listener<AylaLanConfig>() {
                                              @Override
                                              public void onResponse(AylaLanConfig response) {
                                                  AylaLog.d(LOG_TAG, "Got LAN config for " +
                                                          device.getDsn());
                                                  device.startLanSession(getLanServer());
                                                  fetchNextDeviceLanConfig();
                                              }
                                          },
                            new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    AylaLog.e(LOG_TAG, "Error getting LAN config for " + device
                                            .getDsn());
                                    // Set the error on the DSN in our map
                                    // We know that put() will return a throwable AylaError if
                                    // one already existed in the map, but we
                                    // don't want to throw it. Disable inspection.
                                    //noinspection ThrowableResultOfMethodCallIgnored
                                    _deviceInitErrors.put(device.getDsn(), error);
                                    fetchNextDeviceLanConfig();
                                }
                            });
                    break;
                }
            }
        }

        if (!foundOne) {
            // We're done.
            setState(DeviceManagerState.Ready);
            startPolling();
        }
    }

    /**
     * Shuts down the DeviceManager. Used only when signing out or signing in when a session is
     * already active.
     */
    void shutDown() {
        synchronized (_deviceHashMap) {
            for ( AylaDevice device : _deviceHashMap.values()) {
                device.shutDown();
            }
        }

        stopPolling();
        synchronized (_deviceManagerListeners) {
            _deviceManagerListeners.clear();
        }

        _deviceHashMap.clear();
        _deviceRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });

        if ( _lanServer != null ) {
            _lanServer.stop();
            _lanServer = null;
        }
    }

    /**
     * Pauses the DeviceManager. This method is called from AylaSessionManager, and should not be
     * called directly.
     */
    void onPause() {
        AylaLog.d(LOG_TAG, "onPause()");
        AylaNetworks.sharedInstance().getConnectivity().unregisterListener(this);
        if (_lanServer != null) {
            _lanServer.stop();
        }
        stopPolling();
        synchronized (_deviceHashMap) {
            for (AylaDevice d : _deviceHashMap.values()) {
                d.stopLanSession();
                d.stopPolling();
            }
        }
        setState(DeviceManagerState.Paused);
    }

    /**
     * Resumes the DeviceManager. This method is called from AylaSessionManager, and should not
     * be called directly.
     */
    void onResume() {
        AylaLog.d(LOG_TAG, "onResume()");

        // Listen for connectivity changes
        AylaNetworks.sharedInstance().getConnectivity().registerListener(this);

        if (_lanServer == null) {
            startWebserver();
        } else {
            int port = _lanServer.getListeningPort();
            AylaSessionManager sm = getSessionManager();
            try {
                if (!_lanServer.isAlive()) {
                    _lanServer.start();
                }
            } catch (IOException e) {
                AylaLog.e(LOG_TAG, "Could not resume HTTP server for session " +
                        sm.getSessionName());
                // Make a new one
                startWebserver();
            }
        }

        if (getState() == DeviceManagerState.Paused) {
            startPolling();
            synchronized (_deviceHashMap) {
                for (AylaDevice d : _deviceHashMap.values()) {
                    d.startLanSession(getLanServer());
                }
            }
        }
    }

    /**
     * Returns true if the DeviceManager has successfully completed the initialization process at
     * least once. If this returns true, a call to getDevices() will return a list of devices
     * that was at one point fetched from the service, regardless of the current state of the
     * DeviceManager. If this returns false, no device list was able to be fetched from the
     * service since DeviceManager was created.
     *
     * @return true if DeviceManager has completed initialization at least once, false if not
     */
    public boolean hasInitialized() {
        return _hasInitialized;
    }

    //
    // Helper methods called to notify our list of listeners of various events
    //

    /**
     * Notifies listeners that the device manager state has changed
     * @param oldState State the device manager is transitioning from
     * @param newState State the device manager transitioned to
     */
    private void notifyStateChange(final DeviceManagerState oldState,
                                   final DeviceManagerState newState) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceManagerListeners) {
                    Set<DeviceManagerListener> copy = new HashSet<>(_deviceManagerListeners);
                    for (DeviceManagerListener listener : copy) {
                        listener.deviceManagerStateChanged(oldState, newState);
                    }
                }
            }
        });
    }

    /**
     * Notifies listeners that the list of devices has changed
     * @param change Change object with details about the list changes. This object may be null
     *               when the device list is newly fetched.
     */
    private void notifyDeviceListChanged(final ListChange change) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceManagerListeners) {
                    Set<DeviceManagerListener> copy = new HashSet<>(_deviceManagerListeners);
                    for (DeviceManagerListener listener : copy) {
                        listener.deviceListChanged(change);
                    }
                }
            }
        });
    }

    /**
     * Notifies listeners that the DeviceManager has encountered an error
     * @param error Error encountered by DeviceManager
     */
    private void notifyError(final AylaError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceManagerListeners) {
                    Set<DeviceManagerListener> copy = new HashSet<>(_deviceManagerListeners);
                    for (DeviceManagerListener listener : copy) {
                        listener.deviceManagerError(error);
                    }
                }
            }
        });
    }

    /**
     * Notifies listeners that the DeviceManager has completed initialization. At the time this
     * notification is received, the device list is complete and all devices' properties have
     * been fetched from the cloud.
     */
    private void notifyInitComplete() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceManagerListeners) {
                    Set<DeviceManagerListener> copy = new HashSet<>(_deviceManagerListeners);
                    for (DeviceManagerListener listener : copy) {
                        listener.deviceManagerInitComplete(_deviceInitErrors);
                    }
                }
                notifyDeviceListChanged(null);
            }
        });
    }

    /**
     * Notifies listeners that DeviceManager could not complete initialization.
     * @param error Error preventing DeviceManager from completing initialization
     * @param failureState State of the DeviceManager at the point of failure
     */
    private void notifyInitFailure(final AylaError error, final DeviceManagerState failureState) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceManagerListeners) {
                    Set<DeviceManagerListener> copy = new HashSet<>(_deviceManagerListeners);
                    for (DeviceManagerListener listener : copy) {
                        listener.deviceManagerInitFailure(error, failureState);
                    }
                }
            }
        });
    }

    /**
     * Helper method to start the LAN server. It will attempt to listen on our default port, but
     * if that fails, it will increment the port number by one and try again until it succeeds.
     */
    private void startWebserver() {
        // Start our webserver on the default port, if possible. If not, then keep incrementing the
        // port until we get one.
        AylaSessionManager sessionManager = getSessionManager();
        if ( sessionManager == null ) {
            throw new RuntimeException("No session manager for device manager");
        }

        try {
            _lanServer = AylaHttpServer.createDefault(sessionManager);
        } catch (IOException e) {
            AylaLog.e(LOG_TAG, "Failed to create webserver!");
            _lanServer = null;
            return;
        }

        AylaLog.d(LOG_TAG, "LAN server started on port " + _lanServer.getListeningPort());
    }

    //
    // Device management methods
    //

    /**
     * Merges the provided list of devices with the list of devices managed by AylaDeviceManager.
     * This method assumes that the provided array of devices is the exhaustive list of devices
     * owned by the logged-in user. Devices managed by this class that do not exist in the
     * provided array of devices are assumed to be "removed", and will be removed from the
     * AylaDeviceManager's list and will cause listeners to be notified of their removal.
     * <p>
     * This method should be called any time the complete list of devices is fetched from the
     * server.
     * <p>
     * Devices contained in the provided array will be merged with existing copies owned by
     * AylaDeviceManager, and if the merge produces any changes to the individual AylaDevice
     * objects, listeners will be notified of the changes.
     *
     * @param devices Complete array of devices to be managed by AylaDeviceManager.
     */
    void mergeDevices(AylaDevice[] devices) {
        List<String> addedDSNs = new ArrayList<>();
        List<AylaDevice> addedDevices = new ArrayList<>();
        Set<String> providedDSNs = new HashSet<>();

        // We're setting up a set of all of our DSNs here, and will remove all of the provided
        // DSNs from it later to get the set of removed DSNs. We need to make a copy of the key
        // set, because removing anything from the returned set will actually remove objects from
        // the map!
        Set<String> removedDSNs = new HashSet<>(_deviceHashMap.keySet());

        synchronized (_deviceHashMap) {

            // First see if anything was added
            for (AylaDevice device : devices) {
                // Save the DSN in our set of provided DSNs. We will use this set later to
                // determine if any devices were removed.
                providedDSNs.add(device.getDsn());

                // See if we have one of these already
                AylaDevice existingDevice = _deviceHashMap.get(device.getDsn());
                if (existingDevice != null) {
                    // This will notify listeners of changes if any occur
                    existingDevice.updateFrom(device, AylaDevice.DataSource.CLOUD);
                } else {
                    // We need to add the device
                    device.setDeviceManager(this);
                    _deviceHashMap.put(device.getDsn(), device);
                    addedDSNs.add(device.getDsn());
                    addedDevices.add(device);
                }
            }

            // Now see if anything was removed
            removedDSNs.removeAll(providedDSNs);
            for (String removedDsn : removedDSNs) {
                AylaDevice d = _deviceHashMap.remove(removedDsn);
                d.shutDown();
            }
        }

        //If offline mode is supported, and this is not a cached device list, save fetched device
        // list to cache
        AylaCache cache = getSessionManager().getCache();
        if(AylaNetworks.sharedInstance().getSystemSettings().allowOfflineUse &&
                !isCachedDeviceList()){
            Gson gson = AylaNetworks.sharedInstance().getGson();
            String devicesToCache = gson.toJson(devices, AylaDevice[].class);
            cache.save(cache.getKey(AylaCache.CacheType.DEVICE, ""), devicesToCache);
        }

        // First check to see if our list has changed, and if so, notify any listeners.
        if (addedDSNs.size() + removedDSNs.size() > 0) {
            // Our list has changed.
            ListChange change = new ListChange(addedDevices, removedDSNs);
            notifyDeviceListChanged(change);
        }

        // If we added any devices, move back to the FetchingDeviceProperties state to get all of
        // the new devices ready
        if (addedDSNs.size() > 0) {
            setState(DeviceManagerState.FetchingDeviceProperties);
            fetchProperties();
        }
    }

    /**
     * Adds a device to the device manager. This should only be called internally after a device
     * has been registered to the account.
     *
     * @param device Device that was just registered
     * @return true if the device was added, false if it already exists or an error occurred
     */
    public boolean addDevice(AylaDevice device) {
        if (device == null || device.getDsn() == null) {
            return false;
        }

        synchronized (_deviceHashMap) {
            // Make sure we don't already have this device
            if (_deviceHashMap.containsKey(device.getDsn())) {
                return false;
            }
            _deviceHashMap.put(device.getDsn(), device);
            device.setDeviceManager(this);

            // Since we have a new device, we need to go back to an earlier state to fetch all of
            // the required configuration information for the device.
            setState(DeviceManagerState.FetchingDeviceList);
            fetchDevices();
        }
        return true;
    }

    /**
     * Returns a URL for the Device service pointing to the specified path.
     * This URL will vary depending on the AylaSystemSettings provided to the CoreManager
     * during initialization.
     *
     * @param path Path of the URL to be returned, e.g. "users/sign_in.json"
     * @return Full URL, e.g. "https://ads-field.aylanetworks.com/apiv1/users/sign_in.json"
     */
    public String deviceServiceUrl(String path) {
        return AylaNetworks.sharedInstance().getServiceUrl(ServiceUrls.CloudService.Device, path);
    }

    /**
     * Enqueues the provided request to the Ayla Cloud Device Service.
     *
     * @param request the request to send
     * @return the request, which can be used for cancellation.
     */
    public AylaAPIRequest sendDeviceServiceRequest(AylaAPIRequest request) {
        request.setShouldCache(false);
        request.logResponse();
        _deviceRequestQueue.add(request);
        return request;
    }

    public AylaRegistration getAylaRegistration() {
        return new AylaRegistration(this);
    }

    /**
     * Create a Batch of AylaDatapoint objects. Each item in the array of requests may be for any
     * device or property of that device, allowing many properties across multiple devices to be
     * updated simultaneously.
     * <p>
     * The caller should call {@link AylaDatapointBatchResponse#getStatus()} on each element
     * returned in the response array for a good (200-level) status code indicating the datapoint
     * was created successfully. Datapoints with responses in this array that have non-200-level
     * status codes were not created.
     * <p>
     * The errorListener will be called if the service could not be reached or returned an error
     * from the batch request. The successListener will be called if the batch operation itself
     * was successful, even if some of the datapoints were not created.
     *
     * @param requests An array of AylaDatapointBatchRequest objects
     * @param successListener An array of AylaDatapointBatchResponse objects
     * @param errorListener  Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to create a Datapoint Batch
     */
    public AylaAPIRequest createDatapointBatch(final AylaDatapointBatchRequest[] requests,
                                               final Response.Listener<AylaDatapointBatchResponse[]>
                                                       successListener,
                                               final ErrorListener errorListener) {
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        if (requests == null || requests.length == 0) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("DatapointBatchRequests " +
                        "cannot be empty"));
            }
            return null;
        }
        JSONArray array = new JSONArray();
        for (AylaDatapointBatchRequest request : requests) {
            try {
                array.put(request.toJSONObject());
            } catch (JSONException e) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(new JsonError(null, "JSONException trying to " +
                            "create DatapointBatchRequest message", e));
                }
                return null;
            }
        }
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("batch_datapoints", array);
        } catch (JSONException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new JsonError(null, "JSONException in Batch " +
                        "DataPoints Request Body", e));
            }
            return null;
        }
        String postBodyString = jsonObject.toString();
        String url = deviceServiceUrl("apiv1/batch_datapoints.json");

        AylaAPIRequest request = new AylaJsonRequest<AylaDatapointBatchResponse[]>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaDatapointBatchResponse[].class,
                sessionManager,
                new Response.Listener<AylaDatapointBatchResponse[]>() {
                    @Override
                    public void onResponse(AylaDatapointBatchResponse[] responseArray) {
                        for (AylaDatapointBatchResponse datapointResponse : responseArray) {
                            if (datapointResponse.getStatus() == DATAPOINT_CREATED_SUCCESS) {
                                AylaDevice device = deviceWithDSN(datapointResponse.getDsn());
                                AylaProperty property = device.getProperty(
                                        datapointResponse.getName());
                                AylaDatapoint datapoint = datapointResponse.getDatapoint();
                                String baseType = property.getBaseType();
                                if(baseType.equals("boolean") || baseType.equals("integer") ||
                                        baseType.equals("decimal")){
                                    String   value = (String) datapoint.getValue();
                                    datapoint.setValue( TypeUtils.getTypeConvertedValue(baseType, value));
                                }
                                property.updateFrom(datapoint, AylaDevice.DataSource.CLOUD);
                            }
                        }
                        successListener.onResponse(responseArray);
                    }
                }, errorListener){

            @Override
            protected Response parseNetworkResponse(NetworkResponse response) {
                // Save our response headers
                _responseHeaders = response.headers;

                // Deserialize the JSON data into an object
                String json;
                try {
                    json = new String(
                            response.data,
                            HttpHeaderParser.parseCharset(response.headers));
                    if ( _logResponse ) {
                        String responseString = new String(response.data);
                        AylaLog.d(getLogTag(), "Request: " + this.toString() +
                                " response code: " + response.statusCode +
                                " response body: " + responseString);
                    }
                } catch (UnsupportedEncodingException e) {
                    return Response.error(new ParseError(e));
                }
                //Convert all responses to string because they belong to different properties.
                Type type = new TypeToken<AylaDatapointBatchResponse<String>[]>(){}.getType();
                AylaDatapointBatchResponse[] datapointBatchResponses = getGson().fromJson(json, type);

                try {
                    return Response.success(
                            datapointBatchResponses,
                            HttpHeaderParser.parseCacheHeaders(response));
                } catch (JsonSyntaxException e) {
                    return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                            json, e)));
                } catch (JsonParseException e) {
                    return Response.error(new ParseError(new AylaError(AylaError.ErrorType.JsonError,
                            json, e)));
                }
            }
        };

        sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Internal method called when a device is unregistered. Listeners will be notified that the
     * device list has changed.
     * @param device Device that was unregistered
     */
    public void removeDevice(AylaDevice device) {
        // Shut down LAN mode
        device.setLanModePermitted(false);
        synchronized (_deviceHashMap) {
            _deviceHashMap.remove(device.getDsn());
        }

        // Notify listeners
        Set<String> removedDSNs = new HashSet<>();
        removedDSNs.add(device.getDsn());
        notifyDeviceListChanged(new ListChange(null, removedDSNs));
    }

    @Override
    public void connectivityChanged(boolean wifiEnabled, boolean cellularEnabled) {
        AylaLog.d(LOG_TAG, "Connectivity changed: wifi " + wifiEnabled + ", cellular " + cellularEnabled);
        // Let our devices try LAN mode again if they had issues before
        synchronized (_deviceHashMap) {
            for (AylaDevice device : _deviceHashMap.values()) {
                device.disableLANUntilNetworkChanges(false);
            }
        }
    }

    /**
     * States for the DeviceManager.
     * <p>
     * On initialization, the DeviceManager enters the {@link #FetchingDeviceList} state.
     * <p>
     * When the list of devices has been fetched, the DeviceManager enters the {@link
     * #FetchingDeviceProperties} state where it obtains the latest set of datapoints from each
     * device.
     * <p>
     * Once the properties have been updated for each device, the DeviceManager enters the {@link
     * #Ready} state.
     * <p>
     * Listeners will be notified of each state change as it occurs.
     */
    public enum DeviceManagerState {
        Uninitialized,                      // Initial state of the DeviceManager
        FetchingDeviceList,                 // Fetching the list of devices from the cloud service
        FetchingDeviceProperties,           // Fetching the current list of property values
        FetchingLanConfig,                  // Fetching LAN configuration info for each device
        Ready,                              // Initialization complete
        Error,                              // Unable to initialize, usually due to the inability
                                            // to fetch the device list from the cloud service
        Paused,                             // A call to onPause() was made without a call to
                                            // onResume()
    }

    /**
     * The DeviceManagerListener interface is used to provide notifications of changes to the
     * AylaDeviceManager. Listeners implementing this interface may regsiter for DeviceManager
     * notifications via a call to {@link #addListener(DeviceManagerListener)}, and remove
     * themselves via a call to {@link #removeListener(DeviceManagerListener)}.
     */
    public interface DeviceManagerListener {
        /**
         * Called when the device manager has completed initialization. When this method is called,
         * the device manager has the complete set of devices and their properties available.
         *
         * @param deviceFailures A map of DSNs of devices that failed to be initialized to the
         *                       specific error that caused initialization failure. If a
         *                       device's properties were not available, or details could not be
         *                       fetched about a particular device, the DSN of that device will be
         *                       included deviceFailures map.
         *                       <p>
         *                       If all devices could be initialized, the deviceFailures map will
         *                       be empty.
         */
        void deviceManagerInitComplete(Map<String, AylaError> deviceFailures);

        /**
         * Called when the DeviceManager could not complete initialization. This usually occurs
         * when the DeviceManager is unable to fetch the list of devices from the cloud and does
         * not have the device list cached.
         * <p>
         * The DeviceManager will be in the Error state when this notification is received. The
         * state the DeviceManager was in when the failure occurred will be passed via the
         * failureState argument to this method.
         *
         * @param error        Error that occurred
         * @param failureState State of the device manager when the error occurred
         */
        void deviceManagerInitFailure(AylaError error, DeviceManagerState failureState);

        /**
         * Called whenever the list of devices has changed. The change object can be queried to
         * find the DSNs of the devices that were added or removed from the device list.
         *
         * @param change The {@link ListChange} object containing
         *               information about the devices that were added or removed.
         */
        void deviceListChanged(ListChange change);

        /**
         * Called if the DeviceManager encounters an error during operation. This may be called at
         * any time.
         *
         * @param error Error encountered by the DeviceManager
         */
        void deviceManagerError(AylaError error);

        /**
         * Called whenever the DeviceManager changes state.
         *
         * @param oldState State the DeviceManager was in before the change
         * @param newState Current state of the DeviceManager
         */
        void deviceManagerStateChanged(DeviceManagerState oldState, DeviceManagerState newState);
    }

    /**
     * @return True if the current device list is a cached list, and might be stale
     */
    public boolean isCachedDeviceList() {
        return _isCachedDeviceList;
    }

    /**
     * Set whether the device list is cached. This is set during offline use of the app.
     * @param _isCachedDeviceList true if device list is cached.
     */
    public void setIsCachedDeviceList(boolean _isCachedDeviceList) {
        this._isCachedDeviceList = _isCachedDeviceList;
    }

    /**
     * Fetch list of alerts sent to the user for this device.
     * @param dsn DSN of the device for which the alert history is to be fetched. The device must be
     *            currently registered to this user.
     * @param successListener Listener to receive an array of AylaAlertHistory objects on success.
     * @param errorListener Listener to receive errors should an error occur.
     * @param paginated true if paginated. false otherwise.
     * @param perPage Number of entries per page. This will be ignored if paginated is false.
     * @param page Page number to request. This will be ignored if paginated is false.
     * @param alertFilter Filter to apply to the result. (Optional). An {@link AlertFilter} object
     *                    passed to this request will apply the filter set in the object to the
     *                    result from this method.
     *                    Refer
     *                    {@link AlertFilter#add(AlertHistoryFilters, FilterOperators, String)}
     *                    to add filters.
     * @param sortParams Map with key-value specifying sorting orders for results returned from the
     *                   API. For default order, use null value for this parameter.
     *                   To sort by a field, use key "order_by" and value as field name.
     *                   eg: To get results in descending order of sent_at field, use map
     *                   "order_by": "sent_at", "order": "desc"
     *                   And to get results in ascending order of sent_at field, use map
     *                   "order_by": "sent_at", "order": "asc".
     *
     *
     */
    public AylaAPIRequest fetchAlertHistory(String dsn,
                                            final Response.Listener<AylaAlertHistory[]> successListener,
                                            ErrorListener errorListener, boolean paginated,
                                            int perPage, int page, AlertFilter alertFilter,
                                            Map<String, String> sortParams){
        if(dsn == null){
            errorListener.onErrorResponse(new PreconditionError("DSN is null"));
            return null;
        }
        if(getSessionManager() == null){
            errorListener.onErrorResponse(new PreconditionError("No valid session"));
            return null;
        }
        Map<String, String> params = new HashMap<>();
        String url = deviceServiceUrl("apiv1/dsns/" + dsn + "/devices/alert_history.json");
        if(paginated){
            params.put("paginated", String.valueOf(paginated));
            params.put("page", String.valueOf(page));
            params.put("per_page", String.valueOf(perPage));
        }
        if(sortParams != null){
            for(String key: sortParams.keySet()){
                params.put(key, sortParams.get(key));       }
        }

        if(alertFilter != null){
            HashMap<String, String> filterMap = alertFilter.build();
            if(filterMap != null ){
                Set<String> keySet = filterMap.keySet();
                for(String key: keySet){
                    params.put(key, filterMap.get(key));
                }
            }
        }
        if(params.size() > 0){
            url = URLHelper.appendParameters(url, params);
        }

        Log.d(LOG_TAG, "fetchAlertHistory. url "+ url);
        AylaAPIRequest request = new AylaAPIRequest(Request.Method.GET, url, null,
                Wrapper[].class, getSessionManager(), new Response
                .Listener<Wrapper[]>() {
            @Override
            public void onResponse(Wrapper[] response) {
                successListener.onResponse(Wrapper.unwrap(response));
            }
        }, errorListener){
            @Override
            protected Response parseNetworkResponse(NetworkResponse response) {
                // Deserialize the JSON data into an object
                String json;
                try {
                    json = new String(
                            response.data,
                            HttpHeaderParser.parseCharset(response.headers));
                } catch (UnsupportedEncodingException e) {
                    return Response.error(new ParseError(e));
                }
                JsonParser jsonParser = new JsonParser();
                JsonElement responseJson = jsonParser.parse(json);
                JsonArray alertHistoryArray = responseJson.getAsJsonObject().get("alert_histories")
                        .getAsJsonArray();
                Gson gson = AylaNetworks.sharedInstance().getGson();
                Wrapper[] alertHistory = gson.fromJson(alertHistoryArray,
                        Wrapper[].class);
                return Response.success(alertHistory, HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Enables or disables LAN mode on all devices owned by this user.
     * @param permitted true to enable LAN mode on all devices, false to disable.
     */
    public void setLanModePermitted(boolean permitted){
        for(String dsn: _deviceHashMap.keySet()){
            _deviceHashMap.get(dsn).setLanModePermitted(permitted);
        }
    }

    /**
     * Get device initialization errors so that we have a chance to get to know if a device
     * didn't get fully initialized due to some unexpected reasons, for example, unavailable
     * network connectivity.
     * @return Set of device DSNs and corresponding errors that encountered during device
     * manager initialization.
     */
    public Map<String, AylaError> getDeviceInitErrors() {
        return _deviceInitErrors;
    }

    /**
     * Get device initialization error for a particular device, hence we have chance to get
     * to know if the device didn't get fully initialized due to some unexpected reasons,
     * for example, unavailable network connectivity.
     * @return The corresponding initialization error, if any, for the specified device,
     * or return null if no such error or the specified device is null.
     */
    public AylaError getDeviceInitError(AylaDevice device) {
        return device != null ? _deviceInitErrors.get(device.getDsn()) : null;
    }

}
