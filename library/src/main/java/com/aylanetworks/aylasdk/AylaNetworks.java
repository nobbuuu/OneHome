package com.aylanetworks.aylasdk;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.aylanetworks.aylasdk.auth.AylaAuthProvider;
import com.aylanetworks.aylasdk.auth.AylaAuthorization;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import com.aylanetworks.aylasdk.metrics.AylaUncaughtExceptionHandler;
import com.aylanetworks.aylasdk.metrics.AylaUserDataGrant;
import com.aylanetworks.aylasdk.plugin.AylaPlugin;
import com.aylanetworks.aylasdk.util.AylaTypeAdapterFactory;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * The AylaNetworks is the main object used to configure the Ayla SDK. Applications need to
 * initialize the CoreManager before any other SDK functionality is used. The CoreManager is
 * initilalized via a call to {@link #initialize(AylaSystemSettings)}, where the application
 * needs to provide the SDK with information about the system. This information includes the app
 * ID, app secret and an object that can assist the SDK by providing specifics about devices and
 * properties known to the application.
 * <p>
 * Once the SDK has been initialized, the CoreManager can be accessed globally via a call to the
 * {@link #sharedInstance()} static method.
 * <p>
 * After initialization, the CoreManager provides an {@link AylaLoginManager}, which can be used to
 * sign in and obtain an {@link AylaSessionManager}. The AylaSessionManager maintains the
 * session, updating the authorization as needed, and provides the {@link AylaDeviceManager},
 * which takes care of maintaining the set of devices registered to the user's account.
 */
public class AylaNetworks {
    private static final String SDK_VERSION = "6.4.02";

    private static final String SDK_SUPPORT_EMAIL = "support-mobile@aylanetworks.com";
    /**
     * Shared instance of the AylaNetworks, accessible via sharedInstance()
     */
    private static AylaNetworks __sharedCoreManager;

    /**
     * System settings used to initialize the Ayla library.
     */
    private AylaSystemSettings _systemSettings;

    /**
     * Our login manager, created when we are initialized
     */
    private AylaLoginManager _loginManager;

    /**
     * AylaMetricsManager for handling SDK metrics upload to AylaLogService
     */
    private AylaMetricsManager _metricsManager;

    //
    // Internal Section
    //
    /**
     * The current session, or null if nobody is logged in
     */
    private Map<String, AylaSessionManager> _sessionManagers;

    /**
     * Request queue for connections to the User service
     */
    private RequestQueue _userServiceRequestQueue;

    /**
     * Gson object used for object serialization / deserialization
     */
    private Gson _gson;

    /**
     * AylaConnectivity lets us know when the network state changes
     */
    private AylaConnectivity _connectivity;

    /**
     * Map of plug-in services. This allows plug-in services to register themselves.
     */
    final private Map<String, AylaPlugin> _pluginServiceMap = new HashMap<>();

    /**
     * Value to be set based on user's preferences using combination of
     * {@link AylaUserDataGrant}
     */
    private AylaUserDataGrant _userDataGrants = new AylaUserDataGrant();

    public AylaUserDataGrant getUserDataGrants() {
        return _userDataGrants;
    }

    /**
     * Set user's preferences for data usage. To enable data uploads, use either one or
     * combination of AYLA_USER_DATA_GRANT_METRICS_SERVICE and
     * AYLA_USER_DATA_GRANT_UNCAUGHTEXCEPTION.
     * To disable all data uploads set AYLA_USER_DATA_GRANT_NONE
     * eg: To allow uploading of anonymous usage statistics and crash logs, use
     * setUserDataGrants(AylaUserDataGrantMetricsService | AylaUserDataGrantUncaughtException)
     * @param userDataGrants
     */
    public void setUserDataGrants(int userDataGrants) {
        this._userDataGrants.setUserDataGrant(userDataGrants);
        if(_metricsManager != null){
            if((userDataGrants & AylaUserDataGrant.AYLA_USER_DATA_GRANT_METRICS_SERVICE) != 0){
                _metricsManager.enable(!_systemSettings.disableMetricsService);
            } else{
                _metricsManager.enable(false);
            }
        }

        if(_systemSettings != null && getContext() != null){
            if((userDataGrants &
                    AylaUserDataGrant.AYLA_USER_DATA_GRANT_UNCAUGHTEXCEPTION) != 0){
                Thread.UncaughtExceptionHandler currentExceptionHandler =
                        _systemSettings.context.getMainLooper().getThread().getUncaughtExceptionHandler();
                _systemSettings.context.getMainLooper().getThread().setUncaughtExceptionHandler(
                        new AylaUncaughtExceptionHandler(getContext(), currentExceptionHandler));
            }
        }
    }

    /**
     * Do not instantiate directly- call {@link #initialize} once, then call {@link #sharedInstance}
     * to access the singleton object.
     */
    private AylaNetworks() {
    }

    /**
     * Initializes the Ayla library with the provided AylaSystemSettings. This method must be called
     * before any other Ayla library methods.
     *
     * @param systemSettings AylaSystemSettings object used to initialize the library.
     * @return the newly created AylaNetworks object
     */
    public static AylaNetworks initialize(AylaSystemSettings systemSettings) {
        // If we were already initialized, just copy the system settings.
        if (__sharedCoreManager != null) {
            __sharedCoreManager._systemSettings = new AylaSystemSettings(systemSettings);
            return __sharedCoreManager;
        }

        // This is a fresh call to initialize.
        __sharedCoreManager = new AylaNetworks();
        __sharedCoreManager.initializeInternal(systemSettings);

        Context context = systemSettings.context;
        Cache cache = new DiskBasedCache(context.getCacheDir(), 1024 * 1024);

        // Set up the HTTPURLConnection network stack
        Network network = new BasicNetwork(new HurlStack());

        // Create and start our queue
        __sharedCoreManager._userServiceRequestQueue = new RequestQueue(cache, network);
        __sharedCoreManager._userServiceRequestQueue.start();

        if(__sharedCoreManager.getUserDataGrants().isEnabled(AylaUserDataGrant
                .AYLA_USER_DATA_GRANT_UNCAUGHTEXCEPTION) && !systemSettings.disableUncaughtExceptionHandler){
            Thread.UncaughtExceptionHandler currentExceptionHandler =
                    systemSettings.context.getMainLooper().getThread().getUncaughtExceptionHandler();
            systemSettings.context.getMainLooper().getThread().setUncaughtExceptionHandler(
                    new AylaUncaughtExceptionHandler(context, currentExceptionHandler));
        } else {
            systemSettings.context.getMainLooper().getThread().setUncaughtExceptionHandler(null);
        }


        return __sharedCoreManager;
    }

    /**
     * Called when the application is about to exit. This method removes the shared cored manager,
     * removing any references to the application it might contain.
     */
    public static void shutDown() {

        if (__sharedCoreManager != null) {

            __sharedCoreManager.getUserServiceRequestQueue().cancelAll(new RequestQueue.RequestFilter() {
                @Override
                public boolean apply(Request<?> request) {
                    return true;
                }
            });

            __sharedCoreManager.closeSessions();
            AylaLogService.shutDown();
            AylaMetricsManager.shutDown();

            __sharedCoreManager = null;
        }

    }

    public static boolean isShutDown() {
        return __sharedCoreManager == null;
    }

    /**
     * Returns the current version of the SDK
     * @return the current version of the SDK, e.g. "5.0".
     */
    public static String getVersion() {
        return SDK_VERSION;
    }

    /**
     * Returns the SDK support email address. Email this address with support issues related to
     * the Ayla Mobile SDK
     * @return the email address for SDK support
     */
    public static String getSupportEmail() {
        return SDK_SUPPORT_EMAIL;
    }

    /**
     * Returns the global shared instance of the AylaNetworks. This will be null until
     * {@link #initialize(AylaSystemSettings)} is called.
     *
     * @return The shared AylaNetworks instance, or null if the instance has not been initialized
     */
    public static AylaNetworks sharedInstance() {
        return __sharedCoreManager;
    }

    /**
     * Returns the {@link AylaSessionManager} with the given name, or null if the session with that
     * name was not found.
     *
     * @param sessionName Name of the session to return
     *
     * @return The AylaSessionManager created with the given sessionName, or null if no session
     *         manager with that name was found
     */
    public AylaSessionManager getSessionManager(String sessionName) {
        return _sessionManagers.get(sessionName);
    }

    public AylaLoginManager getLoginManager() {
        return _loginManager;
    }
    
    /**
     * Returns a copy of the {@link AylaSystemSettings} object used to initialize the
     * AylaNetworks.
     *
     * @return A copy of the system settings object
     */
    public AylaSystemSettings getSystemSettings() {
        if (_systemSettings == null) {
            return null;
        }
        return new AylaSystemSettings(_systemSettings);
    }

    public RequestQueue getUserServiceRequestQueue() {
        return _userServiceRequestQueue;
    }

    /**
     * Updates the context used by the library. Call this method if the context used to initialize
     * the library becomes stale, e.g. if the context is destroyed due to the Activity lifecycle,
     * etc.
     *
     * @param context New context to be used by the library
     */
    public void updateContext(Context context) {
        _systemSettings.context = context;
    }

    //
    // Private
    //

    /**
     * Internal method used to initialize the AylaNetworks. This is called when the CoreManager
     * is created via {@link #initialize(AylaSystemSettings)}.
     *
     * @param systemSettings AylaSystemSettings object used to initialize this CoreManager instance
     */
    private void initializeInternal(AylaSystemSettings systemSettings) {
        // Create a copy of the systemSettings object. We don't want to use the one passed in
        // because we want to make sure that our copy cannot be changed externally.
        _systemSettings = new AylaSystemSettings(systemSettings);
        _loginManager = new AylaLoginManager();
        _sessionManagers = new HashMap<>();
        _connectivity = new AylaConnectivity(getContext());
        _connectivity.startMonitoring(getContext());
        _metricsManager = new AylaMetricsManager();
        _metricsManager.enable(!systemSettings.disableMetricsService);
    }

    /**
     * Returns the service URL of the specified cloud service type, optionally appending the
     * specified path. The method takes into account the
     * {@link AylaSystemSettings.CloudProvider},
     * {@link AylaSystemSettings.ServiceType}
     * and {@link AylaSystemSettings.ServiceLocation} values from the
     * {@link AylaSystemSettings} object used to initialize the core manager when determining the
     * correct base URL.
     * <p>
     * If the path field is null, the base URL for the specified cloud service will be returned.
     * Otherwise the path will be appended to the base URL and returned.
     *
     * @param cloudService Dynamic | User | Application | Log
     * @param path         Optional path to append to the cloud service URL. May be null.
     * @return A valid URL string for the requested cloud service
     */
    public String getServiceUrl(ServiceUrls.CloudService cloudService, @Nullable String path) {
        String url = ServiceUrls.getBaseServiceURL(_systemSettings.cloudProvider, cloudService,
                _systemSettings.serviceType, _systemSettings.serviceLocation);
        if (!TextUtils.isEmpty(path)) {
            url = url + path;
        }
        return url;
    }

    /**
     * Sets up the current session for the provided user. This method is called by AylaLoginManager
     * after the user has signed in.
     * <p>
     * The returned authorization object is used to initialize the {@link AylaSessionManager}, which
     * can be obtained from the {@link AylaNetworks} while the session is active (until the
     * user signs out).
     *
     * If a session already exists with the provided sessionName, the session will be refreshed
     * with the new authorization.
     *
     * @param authorization AylaAuthorization object returned from a successful sign-in request.
     * @param isOfflineUse  True if the current session was set up for offline use.
     */
    void signInSuccessful(String sessionName, AylaAuthorization authorization,
                          AylaAuthProvider authProvider, boolean isOfflineUse) {
        AylaSessionManager sm = _sessionManagers.get(sessionName);
        if (sm == null) {
           sm = new AylaSessionManager(authorization, authProvider, sessionName);
            _sessionManagers.put(sessionName, sm);
        } else {
            sm.authorizationRefreshed(authorization);
        }
        AylaLog.d("AylaMetricsManager", "Initializing");
        _metricsManager.setSessionManagerRef(sm);
        _metricsManager.onResume();
        sm.setCachedSession(isOfflineUse);

        // Initialize our plugins with the new session
        for (Map.Entry<String, AylaPlugin> entry : _pluginServiceMap.entrySet()) {
             entry.getValue().initialize(entry.getKey(), sm);
        }
    }

    void sessionClosed(String sessionName) {
        AylaSessionManager sm = _sessionManagers.remove(sessionName);
        if (sm != null) {
            // Let the plugins know we're done with the session
            for (Map.Entry<String, AylaPlugin> entry : _pluginServiceMap.entrySet()) {
                entry.getValue().shutDown(entry.getKey(), sm);
            }
        }
    }

    protected void closeSessions() {
        if (_sessionManagers != null) {
            for (Map.Entry<String, AylaSessionManager> entry : _sessionManagers.entrySet()) {
                AylaSessionManager sm = _sessionManagers.remove(entry.getValue().getSessionName());
                // We don't want to shut down the session manager, because that logs out the user
                sm.getDeviceManager().shutDown();
                sm.getRulesService().shutDown();
            }
        }
    }

    /**
     * Applications should call this method when the app is about to enter the background.
     */
    public void onPause() {
        // Unregister our connectivity receiver
        if (_connectivity != null) {
            _connectivity.stopMonitoring();
        }

        // Pause all of our session managers
        for (AylaSessionManager sm : _sessionManagers.values()) {
            // Pause all of our plugins
            for (Map.Entry<String, AylaPlugin> entry : _pluginServiceMap.entrySet()) {
                entry.getValue().onPause(entry.getKey(), sm);
            }
            sm.onPause();
        }
        _metricsManager.onPause();
    }

    /**
     * Applications should call this method when the app is resuming from the background
     */
    public void onResume() {
        // Register our connectivity receiver
        if (_connectivity == null) {
            _connectivity = new AylaConnectivity(getContext());
        }

        _connectivity.startMonitoring(getContext());

        // Resume all of our session managers
        for (AylaSessionManager sm : _sessionManagers.values()) {
            sm.onResume();
            _metricsManager.setSessionManagerRef(sm);
            // Resume all of our plugins
            for (Map.Entry<String, AylaPlugin> entry : _pluginServiceMap.entrySet()) {
                entry.getValue().onResume(entry.getKey(), sm);
            }
        }
        _metricsManager.onResume();

    }

    /**
     * Returns the AylaConnectivity instance that can be used to register for network state
     * change notifications.
     * @return the AylaConnectivity used for monitoring the network state
     */
    public AylaConnectivity getConnectivity() {
        return _connectivity;
    }

    /**
     * Returns the AylaMetricsManager instance used to upload logs to Ayla Log
     * Service.
     */
    public AylaMetricsManager getMetricsManager(){
        return _metricsManager;    }

    /**
     * Returns the Gson object used for object deserialization from JSON data. This method
     * lazily creates the Gson object when requested the first time.
     *
     * @return the Gson object to use for deseriaialization of Ayla JSON objects
     */
    public Gson getGson() {
        if (_gson == null) {
            // Create the Gson object
            final GsonBuilder builder = new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .excludeFieldsWithoutExposeAnnotation()
                    .registerTypeAdapterFactory(new AylaTypeAdapterFactory());
            _gson = builder.create();

        }
        return _gson;
    }

    /**
     * Enqueues the provided request to the Ayla Cloud User Service.
     *
     * @param request the request to send
     * @return the request, which can be used for cancellation.
     */
    public AylaAPIRequest sendUserServiceRequest(AylaAPIRequest<?> request) {
        request.setShouldCache(false);
        request.logResponse();
        getUserServiceRequestQueue().add(request);
        return request;
    }

    /**
     * Helper method to retrieve the context from the system settings
     *
     * @return the context member of the SystemSettings used to initialize this CoreManager object
     */
    public Context getContext() {
        return _systemSettings.context;
    }

    /**
     * Device Class plugin identifier. Device class plugins implement the
     * {@link com.aylanetworks.aylasdk.plugin.DeviceClassPlugin} interface.
     */
    public static final String PLUGIN_ID_DEVICE_CLASS = "com.aylanetworks.aylasdk.deviceclass";

    /**
     * Returns an installed plug-in, if present
     * @param pluginId the ID of the plugin service to obtain
     * @return the AylaPlugin that was previously installed, or null if not found
     */

    public AylaPlugin getPlugin(String pluginId) {
        synchronized (_pluginServiceMap) {
            return _pluginServiceMap.get(pluginId);
        }
    }

    /**
     * Installs a plug-in into the Ayla SDK.  Plug-ins may be obtained by calling getPlugIn().
     * Installing a plug-in with the same ID as a previous plug-in will overwrite the previous
     * plug-in.
     *
     * @param pluginId The ID for this plug-in identifying its utility
     * @param plugin the plugin to install
     */
    public void installPlugin(String pluginId, AylaPlugin plugin) {
        synchronized (_pluginServiceMap) {
            _pluginServiceMap.put(pluginId, plugin);
        }
    }
}
