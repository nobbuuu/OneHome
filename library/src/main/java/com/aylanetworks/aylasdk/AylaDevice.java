package com.aylanetworks.aylasdk;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.FieldChange;
import com.aylanetworks.aylasdk.change.PropertyChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanCommand;
import com.aylanetworks.aylasdk.lan.AylaLanConfig;
import com.aylanetworks.aylasdk.lan.AylaLanModule;
import com.aylanetworks.aylasdk.lan.AylaLanRequest;
import com.aylanetworks.aylasdk.lan.CreateDatapointCommand;
import com.aylanetworks.aylasdk.lan.LanCommand;
import com.aylanetworks.aylasdk.metrics.AylaMetric;
import com.aylanetworks.aylasdk.metrics.AylaMetricsManager;
import com.aylanetworks.aylasdk.metrics.AylaRegistrationMetric;
import com.aylanetworks.aylasdk.util.AylaTypeAdapterFactory;
import com.aylanetworks.aylasdk.util.DateUtils;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.aylanetworks.aylasdk.util.TypeUtils;
import com.aylanetworks.aylasdk.util.URLHelper;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import static com.aylanetworks.aylasdk.AylaAPIRequest.Method;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

// The library will never use many of these accessor methods. Don't warn about unused methods in
// this class.
@SuppressWarnings("unused")
/**
 * AylaDevice represents a physical device that is registered to the logged-in user. AylaDevices
 * are obtained via the {@link AylaDeviceManager} object, which may be obtained from the {@link
 * AylaSessionManager} for the logged-in user.
 * <p>
 * AylaDevices provide a listener interface that notifies listeners of any changes to the device.
 * Listeners may register with the device objects by calling {@link #addListener}, and remove
 * themselves by calling {@link #removeListener}.
 * <p>
 * AylaDevices also contain a list of {@link AylaProperty} objects, which may be used to read or
 * write {@link AylaDatapoint} objects to control or read values from the device.
 * <p>
 * Devices also may store user-defined metadata using the {@link #fetchAylaDatum} and {@link
 * #createDatum} set of APIs.
 * <p>
 * AylaDevice objects contain information about the device as it is known to the AylaService
 * including attributes such as DSN, a modifiable name for the device, time zone, LAN IP address,
 * {@link AylaSchedule} objects, and much more.
 */
public class AylaDevice {
    private static final String LOG_TAG = "AylaDevice";

    // device properties retrievable from the service
    @Expose
    protected String connectedAt;             // last time the device connected to the service
    @Expose
    protected String connectionStatus;        // near realtime indicator of device to service
    // connectivity. Values are "Online" or "OffLine"
    @Expose
    protected String dsn;                     // Unique Device Serial Number
    @Expose
    protected Boolean hasProperties;          // Does this device have properties
    // id
    @Expose
    protected String ip;                      // external WAN IP Address
    @Expose
    protected Number key;                     // Unique Device Service DB identifier
    @Expose
    protected boolean lanEnabled;             // is LAN Mode supported on this device
    @Expose
    protected String lanIp;                   // Device's local IP address
    @Expose
    protected String lat;                     // latitude coordinate
    @Expose
    protected String lng;                     // longitude coordinate
    @Expose
    protected String mac;                     // optional
    @Expose
    protected String model;                   // device model
    @Expose
    protected String moduleUpdatedAt;         // when this device last completed OTA
    @Expose
    protected String oem;
    @Expose
    protected String oemModel;                // OEM model of the device. Typically assigned by the template
    @Expose
    protected String productClass;            // device product class
    @Expose
    protected String productName;             // device product name, user assignable
    @Expose
    protected String swVersion;               // software version running on the device
    @Expose
    protected String ssid;                    // ssid of the AP the device is connected to
    @Expose
    protected Number userID;                  // User Id who has registered this device
    @Expose
    protected Number templateId;              // template Id associated with this device
    @Expose
    protected String registrationType;        // how to register this device. One of "Same-LAN",
    // "Button-Push", "AP-Mode", "Display", "None"(OEM)
    @Expose
    protected AylaGrant grant;                // If present, it indicates the device is registered to
    // another user, i.e. it has been shared with this user

    protected WeakReference<AylaDeviceManager> _deviceManagerRef;

    // Set to true if LAN mode should not be attempted until a network configuration change has
    // been detected
    protected boolean _lanModeTempDisabled;
    private Gson _gson;

    /**
     * Sources of updates for devices and properties. Devices and properties store a DataSource
     * value indicating the last source of updates, which may be queried by calling
     * {@link #getLastUpdateSource()}.
     */
    public enum DataSource {
        LAN,                // LAN mode device
        CLOUD,              // Ayla cloud
        DSS,                // Ayla datastream service
        CACHED,             // Offline mode
        LOCAL               // Local device connection
    }

    public enum RegistrationType {
        None("None"),
        SameLan("Same-LAN"),
        ButtonPush("Button-Push"),
        APMode("AP-Mode"),
        Display("Display"),
        DSN("Dsn"),
        Node("Node"),
        Local("Local");

        RegistrationType(String value) {
            _stringValue = value;
        }

        public String stringValue() {
            return _stringValue;
        }

        private String _stringValue;

        public static RegistrationType fromString(String type){
            for(RegistrationType regType: values()){
                if(regType.stringValue().equals(type)){
                    return regType;
                }
            }
            return AylaDevice.RegistrationType.None;
        }
    }

    public enum ConnectionStatus {
        Online("Online"),
        Offline("Offline");

        ConnectionStatus(String value) {
            _value = value;
        }

        private String _value;

        public String getStringValue(){
            return _value;
        }
    }

    public enum LocationProvider {
        Ip("ip-based"),
        Wifi("wifi-based"),
        User("user-based"),
        Setup("setup-based");

        LocationProvider(String value) {
            _value = value;
        }

        private String _value;
    }

    /**
     * Default timeout for polling, in ms.
     */
    public final static int DEFAULT_POLL_TIMEOUT_MS = 5000;

    // Managed members
    protected final Map<String, AylaProperty> _propertyMap = new HashMap<>();
    protected final Set<DeviceChangeListener> _deviceChangeListeners = new HashSet<>();
    protected boolean _lanModePermitted = true;
    protected boolean _pollingPermitted = true;
    protected boolean _isPollingActive = false;
    protected Handler _pollTimerHandler;
    protected int _pollIntervalMs = DEFAULT_POLL_TIMEOUT_MS;
    protected AylaLanModule _lanModule;
    protected AylaLanConfig _lanConfig;
    protected DataSource _lastUpdateSource = DataSource.CLOUD;

    public interface DeviceChangeListener {
        /**
         * Called when the status of a device changes. This includes property changes, online
         * status changes, etc.
         *
         * @param device The device that changed
         * @param change Change object describing the devices that changed. This is usually a
         *               FieldChange or PropertyChange object.
         */
        void deviceChanged(AylaDevice device, Change change);

        /**
         * Called whenever an error occurs for this device.
         *
         * @param device Device that encountered the error
         * @param error  Error encountered on this device.
         */
        void deviceError(AylaDevice device, AylaError error);

        /**
         * Called whenever the device enters or leaves LAN mode
         *
         * @param device the device that changed
         * @param lanModeEnabled true if the device has entered LAN mode, false if it left LAN mode
         * @param error meaningful only when lanModeEnabled is false, providing a supplementary reason to
         *              explain when lanmode is disabled.
         */
        void deviceLanStateChanged(AylaDevice device, boolean lanModeEnabled, AylaError error);
    }

    /**
     * Adds the provided listener to the list of listeners to be notified of changes for this
     * device.
     *
     * @param listener Listener to be notified of changes to this device
     */
    public void addListener(DeviceChangeListener listener) {
        synchronized (_deviceChangeListeners) {
            _deviceChangeListeners.add(listener);
        }
    }

    /**
     * Removes the specified listener from the list of listeners to be notified of changes for
     * this device.
     *
     * @param listener Listener to remove
     */
    public void removeListener(DeviceChangeListener listener) {
        synchronized (_deviceChangeListeners) {
            _deviceChangeListeners.remove(listener);
        }
    }

    public Date getConnectedAt() {
        return DateUtils.fromJsonString(connectedAt);
    }

    public ConnectionStatus getConnectionStatus() {
        if (TextUtils.equals(connectionStatus, ConnectionStatus.Online.toString())) {
            return ConnectionStatus.Online;
        }
        return ConnectionStatus.Offline;
    }

    public String getDsn() {
        return dsn;
    }

    public String getIp() {
        return ip;
    }

    public boolean isLanEnabled() {
        return lanEnabled;
    }

    public String getLanIp() {
        return lanIp;
    }

    public void setLanIp(String ip){
        lanIp = ip;
    }

    public String getLat() {
        return lat;
    }

    public String getLng() {
        return lng;
    }

    public String getMac() {
        return mac;
    }

    public String getModel() {
        return model;
    }

    public Date getModuleUpdatedAt() {
        return DateUtils.fromJsonString(moduleUpdatedAt);
    }

    public String getOem() {
        return oem;
    }

    public String getOemModel() {
        return oemModel;
    }

    public String getProductClass() {
        return productClass;
    }

    public String getProductName() {
        return productName;
    }

    public String getSsid() {
        return ssid;
    }

    public String getSwVersion() {
        return swVersion;
    }

    public Number getUserID() {
        return userID;
    }

    public Number getTemplateId() {
        return templateId;
    }

    /**
     * Returns {@link AylaDevice.RegistrationType} for this device
     */
    public RegistrationType getRegistrationType(){
        for (AylaDevice.RegistrationType type : AylaDevice.RegistrationType.values()) {
            if (registrationType.equals(type.stringValue())) {
                return type;
            }
        }
        return RegistrationType.None;
    }

    public String getDeviceName() {
        return getFriendlyName();
    }

    public AylaGrant getGrant() {
        return grant;
    }

    Number getKey() {
        return key;
    }

    public Boolean getHasProperties() {
        return hasProperties;
    }

    public boolean isGateway() {
        return false;
    }

    public boolean isNode() {
        return false;
    }

    /**
     * Returns true if the signed-in user is the owner of this device. Returns false if this
     * device was shared with the signed-in user.
     * @return true if the signed-in user is the owner of this device.
     */
    public boolean amOwner() {
        return getGrant() == null;
    }

    /**
     * Returns a friendly name for this device. If the product name is set, it will be returned,
     * otherwise the device's DSN will be returned.
     *
     * @return a friendly name for this device
     */
    public String getFriendlyName() {
        String response = getProductName();
        if (!TextUtils.isEmpty(response)) {
            return response;
        }

        return getDsn();
    }

    /**
     * Returns true if LAN mode is permitted on this device.
     *
     * @return True if LAN mode is permitted on this device.
     */
    public boolean isLanModePermitted() {
        return _lanModePermitted && lanEnabled && !_lanModeTempDisabled;
    }

    /**
     * Sets whether LAN mode is permitted on this device. If this is set to false, the device
     * will not enter LAN mode under any circumstances.
     *
     * @param permitted True to permit the device to enter LAN mode, false to disallow
     */
    public void setLanModePermitted(boolean permitted) {
        _lanModePermitted = permitted;
        if (!permitted && _lanModule != null) {
            _lanModule.deleteLANSession();
            _lanModule = null;
        } else if(permitted){
            startLanSession(getDeviceManager().getLanServer());
        }
    }

    /**
     * @return true if LAN mode is currently active for this device.
     */
    public boolean isLanModeActive() {
        return _lanModule != null && _lanModule.isActive();
    }

    /**
     * Called to disable LAN attempts until the network configuration has changed. This can be
     * used to prevent devices that share a LAN IP (that were configured on different WiFi
     * networks, for example) from destroying a LAN session of another device with the same LAN IP.
     *
     * Devices that fail to enter LAN mode due to a bad key exchange are disabled until the
     * network configuration changes. The SDK will automatically re-enable LAN attempts when a
     * network change has been detected.
     *
     * @param disable True to disable LAN attempts, false to allow them again.
     */
    public void disableLANUntilNetworkChanges(boolean disable) {
        _lanModeTempDisabled = disable;
        if (!disable) {
            // Attempt a LAN session
            if( !isLanModeActive()){
                startLanSession(getDeviceManager().getLanServer());
            }

        } else {
            stopLanSession();
        }
    }

    public boolean isLANTemporarilyDisabled() {
        return _lanModeTempDisabled;
    }

    /**
     * Returns true if the device may poll for property changes.
     *
     * @return true if the device may poll for property changes.
     */
    public boolean isPollingPermitted() {
        return _pollingPermitted;
    }

    /**
     * Sets whether polling for property changes is permitted on this device. If this is set to
     * false, the device will not poll for property changes.
     *
     * @param permitted true to allow polling for property changes, false to disallow
     */
    public void setPollingPermitted(boolean permitted) {
        _pollingPermitted = permitted;
    }

    /**
     * @return true if polling is currently active on this device
     */
    public boolean isPollingActive() {
        return _isPollingActive;
    }

    /**
     * Returns the DataSource representing the service used to last update this device's status.
     * @return the DataSource last used to update this device's status. May be one of:
     * <ul>
     *     <li>{@link AylaDevice.DataSource#LAN} last update came from device via LAN</li>
     *     <li>{@link AylaDevice.DataSource#DSS} last update came from Device Stream Service</li>
     *     <li>{@link AylaDevice.DataSource#CLOUD} last update came from Ayla Cloud Service</li>
     * </ul>
     */
    public DataSource getLastUpdateSource() {
        return _lastUpdateSource;
    }

    /**
     * Set the DataSource type used to update the device.
     * @param lastUpdateSource the DataSource type used to update the device.
     */
    public void setLastUpdateSource(DataSource lastUpdateSource) {
        this._lastUpdateSource = lastUpdateSource;
    }

    /**
     * Starts polling the service for property changes.
     *
     * @return true if polling could be started or was already active, false if polling is not
     * permitted on this device.
     */
    public boolean startPolling() {
        if (!isPollingPermitted() || isLanModeActive()) {
            return false;
        }

        if (!_isPollingActive) {
            _isPollingActive = true;
            pollProperties();
        }

        return true;
    }

    /**
     * Stops polling the service for property changes for this device
     */
    public void stopPolling() {
        if (_pollTimerHandler != null) {
            _pollTimerHandler.removeCallbacksAndMessages(null);
        }
        _isPollingActive = false;
    }

    /**
     * Starts a LAN mode session with this device
     *
     * @param httpServer Web server used to receive messages from the device
     */
    public void startLanSession(AylaHttpServer httpServer) {
        startLanSession(httpServer,true);
    }

    /**
     * Start LAN Mode session for OTA. This is just for OTA and we dont check for isLanModePermitted
     * for this session
     * @param httpServer Web server used to receive messages from the device
     */
    protected void startOTALanSession(AylaHttpServer httpServer) {
        startLanSession(httpServer,false);
    }
    /**
     * Start LAN Mode session
     * @param httpServer Web server used to receive messages from the device
     * @param checkLanModePermission if true check if isLanModePermitted
     */
    private void startLanSession(AylaHttpServer httpServer, boolean checkLanModePermission) {
        if (isLanModeActive() || (checkLanModePermission && !isLanModePermitted())) {
            return;
        }
        if(_lanModule == null){
            _lanModule = new AylaLanModule(this, httpServer);
        }
        _lanModule.start();
    }
    /**
     * Stops the current LAN session with this device
     */
    public void stopLanSession() {
        if (_lanModule != null) {
            _lanModule.stop();
            _lanModule = null;
        }
    }

    /**
     * Returns the LAN module used by this device for LAN communication, or null if the device is
     * not in LAN mode
     *
     * @return the device's LAN module, if present
     */
    public AylaLanModule getLanModule() {
        return _lanModule;
    }

    /**
     * Helper method to create a LAN command for creating a datapoint
     *
     * @param property          Property the datapoint should be created for
     * @param value             Value of the datapoint
     * @param metadata          Metadata for the datapoint
     * @param ackEnabledTimeout Time in seconds to wait for acknowledge of the datapoint. Only
     *                          valid for properties that are ack-enabled
     * @return the CreateDatapointCommand to be enqueued for the module
     */
    <T> CreateDatapointCommand<T> getCreateDatapointCommand(AylaProperty<T> property, T value,
                                                         Map<String, String> metadata,
                                                         int ackEnabledTimeout) {
        return new CreateDatapointCommand<>(property, value, metadata, ackEnabledTimeout);
    }

    /**
     * Sets the polling interval, in milliseconds. This determines how often the device polls the
     * service for changes to properties.
     *
     * @param interval Polling interval in milliseconds
     */
    public void setPollIntervalMs(int interval) {
        _pollIntervalMs = interval;
    }

    /**
     * @return the current polling interval, in milliseconds
     */
    public int getPollIntervalMs() {
        return _pollIntervalMs;
    }

    /**
     * Internal method used to poll the properties for the device. This is only called if the
     * device is not in LAN mode.
     */
    private void pollProperties() {

        if (AylaNetworks.sharedInstance() == null
                || (getDeviceManager() != null && getDeviceManager().deviceWithDSN(getDsn()) == null)) {
            // SDK has been shut down or device has been unregistered, just remove callbacks and return.
            if (_pollTimerHandler != null) {
                _pollTimerHandler.removeCallbacksAndMessages(null);
            }
            return;
        }

        if (_pollTimerHandler == null) {
            _pollTimerHandler = new Handler(Looper.getMainLooper());
        }

        AylaLog.v("AylaDevice", "Poll properties for " + getDsn());

        String[] propertyNames = null;
        AylaSystemSettings.DeviceDetailProvider provider = AylaNetworks.sharedInstance()
                .getSystemSettings().deviceDetailProvider;
        if (provider != null) {
            propertyNames = provider.getManagedPropertyNames(this);
        }

        fetchProperties(propertyNames,
                new Response.Listener<AylaProperty[]>() {
                    @Override
                    public void onResponse(AylaProperty[] response) {
                        // Properties have already been updated and notifications have been sent
                        // out in the fetchProperties method.
                        if (isPollingPermitted() && isPollingActive() && !isLanModeActive()) {
                            _pollTimerHandler.postDelayed(new Runnable() {
                                                              @Override
                                                              public void run() {
                                                                  pollProperties();
                                                              }
                                                          },
                                    getPollIntervalMs());
                        }
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        _pollTimerHandler.removeCallbacksAndMessages(null);
                        notifyError(error);
                        if (isPollingPermitted() && isPollingActive() && !isLanModeActive()) {
                            _pollTimerHandler.postDelayed(new Runnable() {
                                                              @Override
                                                              public void run() {
                                                                  pollProperties();
                                                              }
                                                          },
                                    getPollIntervalMs());
                        }
                    }
                });
    }

    /**
     * Fetches the provided property values from the cloud service or LAN.
     * The properties will be fetched directly from the device if the device is in LAN mode,
     * otherwise the method will fetch the properties from the cloud service.
     *
     * @param propertyNames   Array of names of properties to fetch, or null to fetch all properties
     * @param successListener Listener to receive the successful result
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest object used to fetch the properties from the cloud service
     */
    public AylaAPIRequest fetchProperties(String[] propertyNames,
                                          final Response.Listener<AylaProperty[]> successListener,
                                          final ErrorListener errorListener) {
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("Session is not active"));
            return null;
        }

        AylaDeviceManager deviceManager = sessionManager.getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        if (getKey() == null) {
            errorListener.onErrorResponse(new InternalError("Device " + getDsn() +
                    " does not have a key"));
            return null;
        }

        if (canFetchLanProperties(propertyNames)) {
            // Get the properties via LAN mode
            return fetchPropertiesLAN(propertyNames, successListener, errorListener);
        }

        // We need to fetch the properties from the cloud instead.
        return fetchPropertiesCloud(propertyNames, successListener, errorListener);
    }

    /**
     * Helper method to determine whether we can perform a property fetch via the LAN as opposed
     * to the cloud.
     * @param propertyNames Array of property names to be fetched
     * @return true if the properties may be fetched via LAN, false if they must be fetched via
     * the cloud API
     */
    private boolean canFetchLanProperties(String[] propertyNames) {
        // We can only fetch the list of properties via LAN if:
        // - We are in LAN mode
        // - The list of properties is not null
        // - We already have properties for each of the names requested
        if (!isLanModeActive() || propertyNames == null) {
            return false;
        }

        for (String name : propertyNames) {
            if (getProperty(name) == null) {
                AylaLog.i(LOG_TAG, "Unable to fetch " + name + " via LAN, as it is unknown");
                return false;
            }
        }

        return true;
    }

    /**
     * Fetches the provided property values (latest datapoint) from the cloud service.
     *
     * @param propertyNames   Array of names of properties to fetch, or null to fetch all properties
     * @param successListener Listener to receive the successful result
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest object used to fetch the properties from the cloud service
     */
    public AylaAPIRequest fetchPropertiesCloud(String[] propertyNames,
                                               final Response.Listener<AylaProperty[]> successListener,
                                               final ErrorListener errorListener) {

        AylaProperty[] cachedProperties = null;
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                    "No session is active"));
            return null;
        }

        AylaDeviceManager deviceManager = sessionManager.getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                    "No device manager is available"));
            return null;
        }

        if (getKey() == null) {
            errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                    "Device " + getDsn() + " does not have a key"));
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + getDsn() + "/properties.json");

        if (propertyNames != null) {
            url += URLHelper.parameterizeArray("names", propertyNames);
        }

        AylaAPIRequest<AylaProperty.Wrapper[]> request = new
                AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaProperty.Wrapper[].class,
                getSessionManager(),
                new Response.Listener<AylaProperty.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaProperty.Wrapper[] response) {

                        if(getSessionManager().isCachedSession()){
                            getSessionManager().setCachedSession(false);
                        }
                        // Unwrap the response
                        AylaProperty[] properties = AylaProperty.Wrapper.unwrap
                                (response);
                        if(AylaNetworks.sharedInstance().getSystemSettings().
                                allowOfflineUse){
                            Gson gson = AylaNetworks.sharedInstance().getGson();
                            String propertiesJson = gson.toJson(properties,
                                    AylaProperty[].class);
                            AylaCache cache = getSessionManager().getCache();
                            cache.save(AylaCache.CacheType.PROPERTY, dsn, propertiesJson);
                        }
                        properties = mergeProperties(properties);
                        successListener.onResponse(properties);
                    }
                },
                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        notifyError(error);
                        errorListener.onErrorResponse(error);
                    }
                });

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches the provided property values (latest datapoint) from the device via the LAN
     *
     * @param propertyNames   Array of names of properties to fetch, or null to fetch all properties
     * @param successListener Listener to receive the successful result
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest object used to fetch the properties from the cloud service
     */
    public AylaAPIRequest fetchPropertiesLAN(final String[] propertyNames,
                                             final Response.Listener<AylaProperty[]>
                                                     successListener,
                                             final ErrorListener errorListener) {
        AylaLanModule module = getLanModule();
        if (module == null) {
            errorListener.onErrorResponse(new PreconditionError("Device is not in LAN mode"));
            return null;
        }

        final List<LanCommand> commands = new ArrayList<>();
        for (String propertyName : propertyNames) {
            if(!getProperty(propertyName).getBaseType().equals(AylaProperty.BASE_TYPE_FILE)){
                commands.add(AylaLanCommand.newGetPropertyCommand(propertyName));
            }
        }

        AylaLanRequest request = new AylaLanRequest(this, commands, getSessionManager(),
                new Response.Listener<AylaLanRequest.LanResponse>() {
                    @Override
                    public void onResponse(AylaLanRequest.LanResponse response) {
                        AylaLog.d(LOG_TAG, "LAN request response");
                        AylaProperty[] properties = new AylaProperty[propertyNames.length];
                        int currentProperty = 0;
                        for (LanCommand command : commands) {
                            String lanResponse = command.getModuleResponse();
                            if (lanResponse == null) {
                                AylaError error = command.getResponseError();
                                AylaLog.e(LOG_TAG, "Found errors in LAN responses");
                                errorListener.onErrorResponse(error);
                                return;
                            }

                            AylaLog.d(LOG_TAG, "lanResponse[" + currentProperty + "]: " +
                                    lanResponse);
                            String propertyName;
                            Object propertyValue;
                            Map<String, String> metadata = null;
                            try {
                                JSONObject data = new JSONObject(lanResponse);
                                propertyName = data.getString("name");

                                AylaProperty myProperty = getProperty(propertyName);
                                if (myProperty != null) {
                                    propertyValue = TypeUtils.getTypeConvertedValue(myProperty
                                            .getBaseType(), data.getString("value"));
                                    try{
                                        String metadataJson = data.get("metadata").toString();
                                        metadata = new HashMap<>();
                                        metadata = AylaNetworks.sharedInstance().getGson().
                                                fromJson(metadataJson, metadata.getClass());
                                    } catch(JSONException e){
                                        AylaLog.d(LOG_TAG, "No metadata associated with the " +
                                                "datapoint");
                                    }

                                    myProperty.updateFrom(propertyValue, metadata, DataSource.LAN);
                                    properties[currentProperty++] = myProperty;
                                }  else {
                                    errorListener.onErrorResponse(new InvalidArgumentError("Unknown " +
                                            "property " + propertyName + " received"));
                                    return;
                                }

                            } catch (JSONException e) {
                                errorListener.onErrorResponse(new JsonError(lanResponse, "Could " +
                                        "not parse LAN response: " + lanResponse, e));
                                return;
                            }
                        }

                        if(AylaNetworks.sharedInstance().getSystemSettings().allowOfflineUse &&
                                properties.length != 0){
                            AylaCache cache = getSessionManager().getCache();
                            String key = cache.getKey(AylaCache.CacheType.PROPERTY, dsn);
                            Gson gson = AylaNetworks.sharedInstance().getGson();
                            String propertiesToCache = gson.toJson(properties, AylaProperty[]
                                    .class);
                            cache.save(key, propertiesToCache);
                        }
                        successListener.onResponse(properties);
                    }
                }, errorListener);

        // Base the timeout for this request on the number of properties, as we need to fetch
        // them one by one.
        int timeout = Math.max(AylaNetworks.sharedInstance().getSystemSettings()
                .defaultNetworkTimeoutMs, (1500 * propertyNames.length));
        request.setRetryPolicy(new DefaultRetryPolicy(timeout, 0, 1f));

        module.sendRequest(request);
        return request;
    }

    /**
     * Returns a list of all known properties for this device
     *
     * @return the list of properties known to be associated with this device
     */
    public List<AylaProperty> getProperties() {
        synchronized (_propertyMap) {
            return new ArrayList<>(_propertyMap.values());
        }
    }

    public AylaProperty getProperty(String propertyName) {
        synchronized (_propertyMap) {
            return _propertyMap.get(propertyName);
        }
    }

    public AylaLanConfig getLanConfig() {
        return _lanConfig;
    }

    /**
     * Fetches the LAN configuration information for this device. This information is needed to
     * enter LAN mode.
     *
     * @param successListener Listener to receive the AylaLanConfig object on success
     * @param errorListener   Listener to receive an AylaError should one occur.
     * @return the AylaAPIRequest sent to the Device service
     */
    AylaAPIRequest fetchLanConfig(final Response.Listener<AylaLanConfig> successListener,
                                  final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        AylaLanConfig cachedConfig = null;
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + getDsn() + "/lan.json");
        final AylaLanConfig finalLoadingcachedConfig = cachedConfig;
        AylaAPIRequest<AylaLanConfig.Wrapper> request =
                new AylaAPIRequest<>(Request.Method.GET,
                        url,
                        null,
                        AylaLanConfig.Wrapper.class,
                        getSessionManager(),
                        new Response.Listener<AylaLanConfig.Wrapper>() {
                            @Override
                            public void onResponse(AylaLanConfig.Wrapper response) {
                                if (response == null) {
                                    errorListener.onErrorResponse(
                                            new AylaError(AylaError.ErrorType.NetworkError,
                                            "LAN config is null for device: " + getDsn()));
                                    return;
                                }

                                if(getSessionManager().isCachedSession()){
                                    getSessionManager().setCachedSession(false);
                                    getDeviceManager().setIsCachedDeviceList(false);
                                }

                                if (_lanConfig != null && response.lanip != null &&
                                    !ObjectUtils.equals(_lanConfig.lanipKeyId,
                                            response.lanip.lanipKeyId)) {
                                    // Our LAN key changed. We can attempt LAN mode again if we
                                    // were temporarily disabled.
                                    _lanModeTempDisabled = false;
                                }

                                _lanConfig = response.lanip;

                                //If offline use is supported, save the lan config to cache
                                if (AylaNetworks.sharedInstance().getSystemSettings().
                                        allowOfflineUse) {
                                    AylaCache cache = getSessionManager().getCache();
                                    String key = cache.getKey(AylaCache.CacheType.LAN_CONFIG,
                                            dsn);
                                    Gson gson = AylaNetworks.sharedInstance().getGson();
                                    String lanConfigJson = gson.toJson(_lanConfig,
                                            AylaLanConfig.class);
                                    cache.save(key, lanConfigJson);
                                }
                                successListener.onResponse(_lanConfig);
                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError error) {
                                //Return error only if cached values were not returned in the
                                // successListener
                                errorListener.onErrorResponse(error);
                            }
                        });

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Internal method called to notify listeners that this device has changed
     *
     * @param change the Change object to be delivered to listeners
     * @param dataSource the datasource for this change
     */
    public void notifyDeviceChanged(final Change change, DataSource dataSource ) {
        AylaLog.v(LOG_TAG, "Device " + this.toString() + " changed: " + change);
        _lastUpdateSource = dataSource;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceChangeListeners) {
                    for (DeviceChangeListener listener : _deviceChangeListeners) {
                        listener.deviceChanged(AylaDevice.this, change);
                    }
                }
            }
        });
    }

    /**
     * Internal method called to notify listeners that this device has encountered an error
     *
     * @param error the AylaError object to be delivered to listeners
     */
    public void notifyError(final AylaError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (_deviceChangeListeners) {
                    for (DeviceChangeListener listener : _deviceChangeListeners) {
                        listener.deviceError(AylaDevice.this, error);
                    }
                }
            }
        });
    }

    /**
     * Internal method called to notify listeners that the LAN state for this device has changed.
     *
     * @param lanEnabled true if LAN mode is active on the device, false otherwise
     */
    public void notifyLanStateChange(final boolean lanEnabled) {
        notifyLanStateChange(lanEnabled, null);
    }

    /**
     * Internal method called to notify listeners that the LAN state for this device has changed.
     *
     * @param lanEnabled true if LAN mode is active on the device, false otherwise
     * @param error meaningful only when lanEnabled is false, providing a supplementary reason to
     *              explain when lanmode is disabled.
     */
    public void notifyLanStateChange(final boolean lanEnabled, final AylaError error) {

        AylaDeviceManager deviceManager = getDeviceManager();
        if(deviceManager != null && deviceManager.deviceWithDSN(dsn) == null){
            AylaLog.d(LOG_TAG, "notifyLanStateChange: device not found in deviceManager");
            return;
        }

        if (lanEnabled && getSessionManager() != null && !getSessionManager().isCachedSession()) {
            // We will notify listeners after we fetch the properties within this method
            dataSourceChanged(DataSource.LAN);
        }
        else {
            if(!lanEnabled){
                dataSourceChanged(DataSource.CLOUD);
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    synchronized (_deviceChangeListeners) {
                        for (DeviceChangeListener listener : _deviceChangeListeners) {
                            listener.deviceLanStateChanged(AylaDevice.this, lanEnabled, error);
                        }
                    }
                }
            });
        }
    }

    /**
     * Internal method used to merge properties fetched from the cloud with our current set of
     * properties for this device. This method will notify listeners of changes if the operation
     * causes properties to change.
     *
     * @param fetchedProperties An array of properties fetched from the cloud to merge
     * @return The device's copy of the properties merged. These should be returned to
     * callers requesting properties rather than ones serialized from JSON.
     */
    protected AylaProperty[] mergeProperties(AylaProperty[] fetchedProperties) {
        List<Change> propertyChanges = new ArrayList<>();

        AylaProperty[] myProperties = new AylaProperty[fetchedProperties.length];
        synchronized (_propertyMap) {
            // Add or update our properties with what was passed in
            for (int index = 0; index < fetchedProperties.length; index++) {
                AylaProperty fetchedProperty = fetchedProperties[index];
                AylaProperty existingProperty = _propertyMap.get(fetchedProperty.getName());
                if (existingProperty != null) {
                    myProperties[index] = existingProperty;
                    // Update the property and save any changes
                    Change change = existingProperty.updateFrom(fetchedProperty, DataSource.CLOUD);
                    if (change != null) {
                        propertyChanges.add(change);
                    }
                } else {
                    // Add the property
                    String propertyName = fetchedProperty.getName();
                    fetchedProperty.setOwner(this);
                    _propertyMap.put(propertyName, fetchedProperty);
                    propertyChanges.add(new PropertyChange(propertyName, null));
                    myProperties[index] = fetchedProperty;
                }
            }
        }

        // Notify of changes, if any happened.
        for (Change change : propertyChanges) {
            notifyDeviceChanged(change, DataSource.CLOUD);
        }

        return myProperties;
    }

    /**
     * @return the AylaDeviceManager that manages this device
     */
    public AylaDeviceManager getDeviceManager() {
        if (_deviceManagerRef == null) {
            return null;
        }
        return _deviceManagerRef.get();
    }

    /**
     * Sets the AylaDeviceManager for this device
     *
     * @param deviceManager the AylaDeviceManager that manages this device
     */
    public void setDeviceManager(AylaDeviceManager deviceManager) {
        _deviceManagerRef = new WeakReference<>(deviceManager);
    }

    /**
     * @return the AylaSessionManager responsible for the session we are running under
     */
    public AylaSessionManager getSessionManager() {
        AylaDeviceManager dm = getDeviceManager();
        if (dm != null) {
            return dm.getSessionManager();
        }
        return null;
    }

    /**
     * Updates this device with values from another device. If the update operation causes this
     * device to change at all, notifications will be sent out to listeners from this method.
     *
     * @param other Device to copy values from
     * @return an AylaChange object representing the changes this operation made, or null if no
     * changes occurred.
     */
    public Change updateFrom(AylaDevice other, DataSource source) {
        _lastUpdateSource = source;

        // See if any fields have changed
        Set<String> changedFields = new HashSet<>();
        if (!ObjectUtils.equals(other.connectionStatus, connectionStatus)) {
            connectionStatus = other.connectionStatus;
            changedFields.add("connectionStatus");
        }
        if (!ObjectUtils.equals(other.connectedAt, connectedAt)) {
            connectedAt = other.connectedAt;
            changedFields.add("connectedAt");
        }
        if (!ObjectUtils.equals(other.grant, grant)) {
            grant = other.grant;
            changedFields.add("grant");
        }
        if (!ObjectUtils.equals(other.swVersion, swVersion)) {
            swVersion = other.swVersion;
            changedFields.add("swVersion");
        }
        if (!ObjectUtils.equals(other.productName, productName)) {
            productName = other.productName;
            changedFields.add("productName");
        }
        if (!ObjectUtils.equals(other.moduleUpdatedAt, moduleUpdatedAt)) {
            moduleUpdatedAt = other.moduleUpdatedAt;
            changedFields.add("moduleUpdatedAt");
        }
        if (!ObjectUtils.equals(other.lanIp, lanIp)) {
            lanIp = other.lanIp;
            changedFields.add("lanIp");
        }
        if (lanEnabled != other.lanEnabled) {
            lanEnabled = other.lanEnabled;
            changedFields.add("lanEnabled");
        }
        if (!ObjectUtils.equals(other.registrationType, registrationType)) {
            registrationType = other.registrationType;
            changedFields.add("registrationType");
        }
        if (!ObjectUtils.equals(other.hasProperties, hasProperties)) {
            hasProperties = other.hasProperties;
            changedFields.add("hasProperties");
        }
        if (!ObjectUtils.equals(other.templateId, templateId)) {
            templateId = other.templateId;
            changedFields.add("templateId");
        }
        if (!ObjectUtils.equals(other.mac, mac)) {
            mac = other.mac;
            changedFields.add("mac");
        }
        if (!ObjectUtils.equals(other.model, model)) {
            model = other.model;
            changedFields.add("model");
        }
        if (!ObjectUtils.equals(other.oemModel, oemModel)) {
            oemModel = other.oemModel;
            changedFields.add("oemModel");
        }

        if (!ObjectUtils.equals(other.dsn, dsn) && other.dsn != null) {
            dsn = other.dsn;
            changedFields.add("dsn");
        }

        Change change = null;
        if (changedFields.size() > 0) {
            change = new FieldChange(changedFields);
            notifyDeviceChanged(change, source);
        }

        return change;
    }

    /**
     * Updates connection status of this device. If the update
     * operation causes this device to change at all, notifications will be sent out to listeners
     * from this method.
     *
     * @param connectionStatus Updated value of connection status. Value is either "Online" or
     *                         "Offline".
     * @return an AylaChange object representing the changes this operation made, or null if no
     * changes occurred.
     */
    public Change updateFrom(ConnectionStatus connectionStatus, DataSource source) {
        Set<String> changedFields = new HashSet<>();
        if (!ObjectUtils.equals(connectionStatus._value, this.connectionStatus)) {
            this.connectionStatus = connectionStatus._value;
            changedFields.add("connectionStatus");
        }
        Change change = null;
        if (changedFields.size() > 0) {
            change = new FieldChange(changedFields);
            notifyDeviceChanged(change, source);
        }
        return change;
    }

    /**
     * Updates this device with new values of latitude and longitude. If the update operation
     * causes this device to change at all, notifications will be sent out to listeners from this
     * method.
     *
     * @param latitude Latitude of the device
     * @param longitude Longitude of the device
     * @param source Data source of this update
     * @return an AylaChange object representing the changes this operation made, or null if no
     * changes occurred.
     */
    public Change updateFrom(String latitude, String longitude, DataSource source) {
        _lastUpdateSource = source;
        Set<String> changedFields = new HashSet<>();
        if(!ObjectUtils.equals(lat, latitude)){
            changedFields.add("lat");
            lat = latitude;
        }
        if(!ObjectUtils.equals(lng, longitude)){
            changedFields.add("lng");
            lng = longitude;
        }

        Change change = null;
        if(changedFields.size() > 0){
            change = new FieldChange(changedFields);
            notifyDeviceChanged(change, source);
        }
        return change;
    }


    /**
     * Shuts down polling and LAN operations
     */
    void shutDown() {
        stopPolling();
        stopLanSession();
        _lanModule = null;
    }

    @Override
    public String toString() {
        return "Device[" + getDsn() + "]";
    }

    /**
     * Wrapper class used to parse device list JSON. The server returns the device list as an
     * array of objects called "device" rather than an array of Device objects at the top-level.
     * This class can be used to aid in constructing an array of devices from the JSON returned
     * from the server.
     */
    public static class Wrapper {
        @Expose
        public AylaDevice device;

        /**
         * Unwraps an array of AylaDeviceWrapper objects to an array of AylaDevice objects
         *
         * @param wrappedDevices Array of AylaDeviceWrapper objects
         * @return Array of AylaDevice objects
         */
        public static AylaDevice[] unwrap(Wrapper[] wrappedDevices) {
            int size = 0;
            if (wrappedDevices != null) {
                size = wrappedDevices.length;
            }

            AylaDevice[] devices = new AylaDevice[size];
            for (int i = 0; i < size; i++) {
                devices[i] = wrappedDevices[i].device;
            }
            return devices;
        }

    }

    /**
     * Creates a datum for this device on the Ayla service.
     *
     * @param key             Key of the datum to be stored in Ayla service.
     * @param value           Value of datum object to be stored in Ayla service.
     * @param successListener Listener to receive the newly created datum object on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest createDatum(String key, String value,
                                      final Response.Listener<AylaDatum> successListener,
                                      ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        if (key == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + dsn + "/data.json");
        final JSONObject datumObject = new JSONObject();
        final JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("key", key);
            bodyObject.put("value", value);
            datumObject.put("datum", bodyObject);
        } catch (JSONException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "datum json " + "message", e));
            return null;
        }
        final String bodyText = datumObject.toString();
        AylaAPIRequest<AylaDatum.Wrapper> request = new AylaJsonRequest<AylaDatum.Wrapper>(
                Request.Method.POST, url, bodyText, null, AylaDatum.Wrapper.class, sessionManager,
                new Response.Listener<AylaDatum.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper response) {
                        successListener.onResponse(response.datum);
                    }
                }, errorListener) {
        };

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches a single datum owned by this device from Ayla service.
     *
     * @param key             Key of the datum to be fetched
     * @param successListener Listener to receive the fetched datum object on success.
     * @param errorListener   Listener to receive error information
     * @return The AylaAPIRequest object queued to send for this request
     */
    public AylaAPIRequest fetchAylaDatum(String key,
                                         final Response.Listener<AylaDatum> successListener,
                                         ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        if (key == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + dsn + "/data/" + key + ".json");
        AylaAPIRequest<AylaDatum.Wrapper> request = new AylaAPIRequest<>(
                Method.GET, url, null, AylaDatum.Wrapper.class, sessionManager,
                new Response.Listener<AylaDatum.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper response) {
                        successListener.onResponse(response.datum);
                    }
                }, errorListener);
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches a list of device data from Ayla service.
     *
     * @param keys            A list of one or more keys for which datum needs to be fetched. All datum
     *                        objects for this device will be fetched if this parameter is null.
     * @param successListener Listener to receive the datum object list on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchAylaDatums(String[] keys,
                                          final Response.Listener<AylaDatum[]> successListener,
                                          ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + dsn + "/data.json");
        if (keys != null) {
            url += URLHelper.parameterizeArray("keys", keys);
        }
        AylaAPIRequest<AylaDatum.Wrapper[]> request = new AylaAPIRequest<>(Method.GET, url, null,
                AylaDatum.Wrapper[].class, sessionManager,
                new Response.Listener<AylaDatum.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper[] response) {
                        successListener.onResponse(AylaDatum.Wrapper.unwrap(response));
                    }
                }, errorListener);
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches a list of device data with keys that match the pattern passed to this method.
     *
     * @param wildcardedString A pattern string. "%" sign is used to define wildcards in the pattern.
     *                         eg. Use "%s" to select all data with keys ending in letter s, and use
     *                         "%input%" to select all data with keys containing the string "input".
     * @param successListener  Listener to receive the datum object list on success.
     * @param errorListener    Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchAylaDatums(String wildcardedString,
                                          final Response.Listener<AylaDatum[]> successListener,
                                          ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + dsn + "/data.json");
        Map<String, String> params = new HashMap<>();
        params.put("keys", wildcardedString);
        url = URLHelper.appendParameters(url, params);
        AylaAPIRequest<AylaDatum.Wrapper[]> request = new AylaAPIRequest<>(Method.GET, url, null,
                AylaDatum.Wrapper[].class, sessionManager,
                new Response.Listener<AylaDatum.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper[] response) {
                        successListener.onResponse(AylaDatum.Wrapper.unwrap(response));
                    }
                }, errorListener);
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches list of all datum objects for this device. To fetch a filtered datum list use
     * {@link AylaDevice#fetchAylaDatums(String[], Response.Listener, ErrorListener)}
     *
     * @param successListener Listener to receive datum object list on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchAylaDatums(final Response.Listener<AylaDatum[]> successListener,
                                          ErrorListener errorListener) {
        return fetchAylaDatums((String[]) null, successListener, errorListener);
    }

    /**
     * Updates value of a device datum in Ayla service.
     *
     * @param key             Key of the datum to be updated.
     * @param value           Value of the datum.
     * @param successListener Listener to receive the updated datum object
     *                        on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest updateDatum(String key, String value,
                                      final Response.Listener<AylaDatum> successListener,
                                      ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        if (key == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            return null;
        }
        final JSONObject datumObject = new JSONObject();
        final JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("key", key);
            bodyObject.put("value", value);
            datumObject.put("datum", bodyObject);
        } catch (JSONException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "datum json " +
                    "message", e));
            return null;
        }
        final String bodyText = datumObject.toString();
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + dsn + "/data/" + key + ".json");
        AylaAPIRequest<AylaDatum.Wrapper> request = new AylaJsonRequest<>(
                Request.Method.PUT, url, bodyText, null, AylaDatum.Wrapper.class, sessionManager,
                new Response.Listener<AylaDatum.Wrapper>() {
                    @Override
                    public void onResponse(AylaDatum.Wrapper response) {
                        successListener.onResponse(response.datum);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes a device datum from Ayla service.
     *
     * @param key             Key of the datum to be deleted.
     * @param successListener Listener to receive result on success
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest deleteDatum(String key,
                                      Response.Listener<EmptyResponse> successListener,
                                      ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        if (key == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Datum key is required"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + dsn + "/data/" + key + ".json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, EmptyResponse.class, sessionManager,
                successListener, errorListener) {
            @Override
            protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                return Response.success(new EmptyResponse(),
                        HttpHeaderParser.parseCacheHeaders(response));
            }
        };
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }


    /**
     * This method is used to unregister a Device. It unregisters the owner device from the user
     * account.
     *
     * @param successListener Listener to receive on successful UnRegistration of the device
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return AylaAPIRequest object used to UnRegister Ayla Device
     */
    public AylaAPIRequest unregister(final Response.Listener<EmptyResponse> successListener,
                                     final ErrorListener errorListener) {

        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No session is active"));
            }
            return null;
        }

        AylaDeviceManager deviceManager = sessionManager.getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No device manager is available"));
            }
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + getKey() + ".json");

        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, EmptyResponse.class, sessionManager,
                successListener, errorListener) {
            @Override
            protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                // Remove ourselves from device manager
                getDeviceManager().removeDevice(AylaDevice.this);
                return Response.success(new EmptyResponse(),
                        HttpHeaderParser.parseCacheHeaders(response));
            }

            @Override
            protected void deliverResponse(EmptyResponse response) {
                super.deliverResponse(response);
                AylaRegistrationMetric registrationMetric = new AylaRegistrationMetric(
                        AylaMetric.LogLevel.INFO,
                        AylaRegistrationMetric.MetricType.UNREGISTER_SUCCESS, "unregister",
                        registrationType, dsn, AylaMetric.Result.SUCCESS, null);
                registrationMetric.setRequestTotalTime(this.getNetworkTimeMs());
                sendToMetricsManager(registrationMetric);
            }

            @Override
            public void deliverError(VolleyError error) {
                super.deliverError(error);
                AylaRegistrationMetric registrationMetric = new AylaRegistrationMetric(
                        AylaMetric.LogLevel.INFO,
                        AylaRegistrationMetric.MetricType.UNREGISTER_FAILURE, "unregister",
                        registrationType, dsn, AylaMetric.Result.FAILURE, error.getMessage());
                sendToMetricsManager(registrationMetric);
            }
        };
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Requests a factory reset of the device. If successful, the device will be reset.
     * @param successListener Listener called upon a successful reset operation
     * @param errorListener Listener called with an error should one occur
     * @return the AylaAPIRequest for this command
     */
    public AylaAPIRequest factoryReset(final Response.Listener<EmptyResponse> successListener,
                                       ErrorListener errorListener) {
        AylaDeviceManager dm = getDeviceManager();
        if (dm == null) {
            errorListener.onErrorResponse(new PreconditionError("An active session is required"));
            return null;
        }

        // TODO: Update this URL to use the "dsns/:DSN/" format instead of the key format once
        // the cloud API is updated
        // https://aylanetworks.atlassian.net/browse/SVC-3117
        String url = dm.deviceServiceUrl("apiv1/devices/" + getKey() +
                "/cmds/factory_reset.json");
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<>(
                Method.PUT,
                url,
                null,
                EmptyResponse.class,
                getSessionManager(),
                new Response.Listener<EmptyResponse>() {
                    @Override
                    public void onResponse(EmptyResponse response) {
                        // Fetch the new device list right now to see if anything chagned.
                        // Virtual nodes will become unregistered by this call, where other
                        // devices will not.
                        AylaDeviceManager dm = getDeviceManager();
                        if (dm != null) {
                            dm.fetchDevices();
                        }
                        successListener.onResponse(response);
                    }
                },
                errorListener);

        dm.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * This method is used to delete a WiFi profile. It deletes the WiFi profile with the given
     * SSID from the device.
     *
     * @param profileSsid     Name of the WiFi profile to be deleted
     * @param successListener Listener to receive on successful deletion of the WiFi profile
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return AylaAPIRequest object used to UnRegister Ayla Device
     */
    public AylaAPIRequest deleteWifiProfile(String profileSsid,
                                            final Response.Listener<EmptyResponse> successListener,
                                            ErrorListener errorListener) {

        if (!isLanModeActive()) {
            errorListener.onErrorResponse(new PreconditionError("Device must be in LAN mode"));
            return null;
        }

        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }

        String url = String.format("http://%s/wifi_profile.json?ssid=%s", getLanIp(),
                Uri.encode(profileSsid));

        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<>
                (Method.DELETE, url, null, EmptyResponse.class,
                        sessionManager,
                        successListener,
                        errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Update the timezone for this device. DST attributes are updated based on whether the
     * timezone has DST or not.
     *
     * @param tzId            Standard time zone identifier string eg."America/Los_Angeles".
     * @param successListener Listener to receive updated timezone on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest updateTimeZone(String tzId,
                                         final Response.Listener<AylaTimeZone> successListener,
                                         ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + getKey() + "/time_zones.json");
        JSONObject timeZoneObj = new JSONObject();
        try {
            timeZoneObj.put("tz_id", tzId);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "timezone json " + "message", e));
            return null;
        }
        final String bodyText = timeZoneObj.toString();
        final byte[] requestBody = bodyText.getBytes();
        AylaAPIRequest<AylaTimeZone.Wrapper> request = new AylaJsonRequest<AylaTimeZone.Wrapper>
                (Method.PUT, url, bodyText, null, AylaTimeZone.Wrapper.class, sessionManager,
                        new Response.Listener<AylaTimeZone.Wrapper>() {
                            @Override
                            public void onResponse(AylaTimeZone.Wrapper response) {
                                successListener.onResponse(response.timeZone);
                            }
                        }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetch the timezone set for this device.
     *
     * @param successListener Listener to receive result on success.
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest fetchTimeZone(final Response.Listener<AylaTimeZone> successListener,
                                        ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + getKey() + "/time_zones.json");
        AylaAPIRequest<AylaTimeZone.Wrapper> request = new AylaAPIRequest<>
                (Method.GET, url, null, AylaTimeZone.Wrapper.class, sessionManager,
                        new Response.Listener<AylaTimeZone.Wrapper>() {
                            @Override
                            public void onResponse(AylaTimeZone.Wrapper response) {
                                successListener.onResponse(response.timeZone);
                            }
                        }, errorListener);
        deviceManager.sendDeviceServiceRequest(request);
        return request;

    }

    /**
     * This method updates the display name of the device
     *
     * @param displayName     The New display name for the Device
     * @param successListener Listener to receive on successful update of Display Name
     * @param errorListener   Listener to receive error information.
     * @return The AylaAPIRequest object queued to send for this request.
     */
    public AylaAPIRequest updateProductName(final String displayName,
                                            final Response.Listener<EmptyResponse> successListener,
                                            ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No session is active"));
            return null;
        }
        if (displayName == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Device Display Name is " +
                    "required"));
            return null;
        }
        AylaDevice device = new AylaDevice();
        device.productName= displayName;

        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + getKey() + ".json");
        AylaDevice.Wrapper deviceWrapper = new AylaDevice.Wrapper();
        deviceWrapper.device = device;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (deviceWrapper, AylaDevice.Wrapper.class);
        final byte[] bodyData = postBodyString.getBytes();

        AylaAPIRequest<EmptyResponse> request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaAPIRequest.EmptyResponse.class,
                sessionManager,
                new Response.Listener<AylaAPIRequest.EmptyResponse>(){
                    @Override
                    public void onResponse(AylaAPIRequest.EmptyResponse response) {
                        AylaDevice.this.productName=displayName;
                        successListener.onResponse(response);
                    }
                },
                errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Creates an AylaDeviceNotification in the cloud
     *
     * @param deviceNotification AylaDeviceNotification
     * @param successListener    Listener to receive on successful creation of Notification
     * @param errorListener      Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Create DeviceNotification
     */
    public AylaAPIRequest createNotification(final AylaDeviceNotification deviceNotification,
                                             final Response.Listener<AylaDeviceNotification>
                                                     successListener,
                                             final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number deviceKey = getKey();
        if (deviceKey == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("deviceKey is required"));
            return null;
        }

        AylaDeviceNotification.Wrapper notifWrapper = new AylaDeviceNotification.Wrapper();
        notifWrapper.notification = deviceNotification;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (notifWrapper, AylaDeviceNotification.Wrapper.class);
        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + deviceKey +
                "/notifications.json");

        final AylaAPIRequest request = new AylaJsonRequest<AylaDeviceNotification.Wrapper>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaDeviceNotification.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaDeviceNotification.Wrapper>() {
                    @Override
                    public void onResponse(AylaDeviceNotification.Wrapper response) {
                        AylaDeviceNotification deviceNotification = response.notification;
                        if (deviceNotification != null) {
                            deviceNotification.setDevice(AylaDevice.this);
                            successListener.onResponse(deviceNotification);
                        } else {
                            errorListener.onErrorResponse(new AylaError
                                    (AylaError.ErrorType.AylaError, "Response does not have " +
                                            "Device Notification"));
                        }
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Updates an existing AylaDeviceNotification in the cloud
     *
     * @param deviceNotification AylaDeviceNotification that needs to be updated
     * @param successListener    Listener to receive on successful update of Notification
     * @param errorListener      Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to update DeviceNotification
     */
    public AylaAPIRequest updateNotification(final AylaDeviceNotification deviceNotification,
                                             final Response.Listener<AylaDeviceNotification>
                                                     successListener,
                                             final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number notificationId = deviceNotification.getId();
        if (notificationId == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Updating a device notification "
                    + "requires that the notification was first fetched from the service"));
            return null;
        }

        AylaDeviceNotification.Wrapper notifWrapper = new AylaDeviceNotification.Wrapper();
        notifWrapper.notification = deviceNotification;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (notifWrapper, AylaDeviceNotification.Wrapper.class);
        final byte[] bodyData = postBodyString.getBytes();
        String url = deviceManager.deviceServiceUrl("apiv1/notifications/" + notificationId + ".json");

        AylaAPIRequest request = new AylaJsonRequest<AylaDeviceNotification.Wrapper>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaDeviceNotification.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaDeviceNotification.Wrapper>() {
                    @Override
                    public void onResponse(AylaDeviceNotification.Wrapper response) {
                        AylaDeviceNotification deviceNotification = response.notification;
                        deviceNotification.setDevice(AylaDevice.this);
                        successListener.onResponse(deviceNotification);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches an Array of existing AylaDeviceNotifications from the cloud
     *
     * @param successListener Listener to receive on successful fetch of DeviceNotificationApps
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Fetch DeviceNotifications
     */
    public AylaAPIRequest fetchNotifications(final Response.Listener<AylaDeviceNotification[]>
                                                     successListener,
                                             final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number deviceKey = getKey();
        if (deviceKey == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("deviceKey is required"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + deviceKey +
                "/notifications.json");

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaDeviceNotification.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaDeviceNotification.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaDeviceNotification.Wrapper[] response) {
                        AylaDeviceNotification[] notifications
                                = AylaDeviceNotification.Wrapper.unwrap(response);
                        if ((notifications != null) && (notifications.length > 0)) {
                            for (AylaDeviceNotification  notification: notifications) {
                                notification.setDevice(AylaDevice.this);
                            }
                        }
                        successListener.onResponse(notifications);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes an existing AylaDeviceNotification from the cloud
     *
     * @param deviceNotification AylaDeviceNotification that needs to be deleted. This is the
     *                           existing NotificationApp that has been fetched from service
     * @param successListener    Listener to receive on successful deletion of DeviceNotification
     * @param errorListener      Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to delete DeviceNotification
     */
    public AylaAPIRequest deleteNotification(final AylaDeviceNotification deviceNotification,
                                             final Response.Listener<EmptyResponse> successListener,
                                             final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number notificationId = deviceNotification.getId();
        if (notificationId == null) {
            errorListener.onErrorResponse(new InvalidArgumentError("Deleting a notification "
                    + "requires that the notification was first fetched from the service"));
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/notifications/" + notificationId + ".json");

        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<>(
                Request.Method.DELETE, url, null, AylaAPIRequest.EmptyResponse.class,
                sessionManager, successListener, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Method to be called when a device's data source changes.
     * Handles start/stop polling and fetches latest properties before polling is stopped.
     *
     * @param dataSource Source of the change. May be one of
     *                   <ul>
     *                   <li>{@link DataSource#DSS} for Device Stream Service updates</li>
     *                   <li>{@link DataSource#CLOUD} for AylaCloud updates</li>
     *                   <li>{@link DataSource#LAN} for LAN mode updates</li>
     *                   </ul>
     */
    public void dataSourceChanged(final DataSource dataSource) {
        boolean fetch = false;
        if (dataSource == DataSource.DSS) {
            if (_lanModule != null && _lanModule.isActive()) { //device was already in LAN mode. No
                // fetch needed
                fetch = false;
            } else if (getSessionManager().getDSManager() != null &&
                    getSessionManager().getDSManager().isConnected()) {
                fetch = true;
            }

        } else if (dataSource == DataSource.LAN) {
            if (_lanModule != null && _lanModule.isActive()) {
                fetch = true;
            }
        }
        Log.d("DSS_LOGS", "dataSourceChanged() fetch " + fetch);
        if (fetch) {
            fetchProperties(AylaNetworks.sharedInstance().getSystemSettings().deviceDetailProvider
                            .getManagedPropertyNames(this),
                    new Response.Listener<AylaProperty[]>() {
                        @Override
                        public void onResponse(AylaProperty[] response) {
                            // Properties fetched and updated for this device
                            stopPolling();
                            if (dataSource == DataSource.LAN) {
                                // Now we can notify our listeners that LAN mode is active
                                synchronized (_deviceChangeListeners) {
                                    for (DeviceChangeListener listener : _deviceChangeListeners) {
                                        listener.deviceLanStateChanged(AylaDevice.this, lanEnabled, null);
                                    }
                                }
                            }
                        }
                    },
                    new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError error) {
                            stopPolling();
                            notifyError(error);
                        }
                    });
        } else {
            startPolling();
        }
    }


    /**
     * Create a share object for this device that can be added to user service using
     * {@link AylaSessionManager#createShare}
     *
     * @param operation operation allowed for the shared user. read or write
     * @param roleName  role-name for the shared user.
     * @param email     email of the user to which device is to be shared.
     * @param roleName  role name of user
     * @param startDate UTC DateTime at which the share begins. Format is YYYY-MM-DDTHH:MM:SSZ .
     *                  If startDate is empty, device is shared immediately.
     * @param endDate   UTC DateTime at which the share ends. Format is YYYY-MM-DDTHH:MM:SSZ .
     *                  If endDate is empty, device is shared indefinitely.
     *
     * @return a new AylaShare object initialized with the supplied values
     */
    public AylaShare shareWithEmail(String email, String operation, String roleName,
                                    String startDate, String endDate) {
        AylaShare share = new AylaShare(email, operation, dsn, "device", roleName,
                startDate, endDate);
        return share;
    }

    /**
     * Updates an AylaSchedule in the cloud
     *
     * @param schedule        AylaSchedule that needs to be updated
     * @param successListener Listener to receive on successful update of Schedule
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Update Schedule
     */
    public AylaAPIRequest updateSchedule(final AylaSchedule schedule,
                                         final Response.Listener<AylaSchedule> successListener,
                                         final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        Number deviceKey = getKey();
        if (deviceKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("deviceKey is required"));
            }
            return null;
        }
        if (schedule == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Ayla Schedule"));
            }
            return null;
        }

        Number scheduleKey = schedule.getKey();
        if (scheduleKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Updating a Schedule"
                        + "requires that the Schedule was first fetched from the service"));
            }
            return null;
        }
        if (schedule.getName() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Name is " +
                        "required"));
            }
            return null;
        }
        if (schedule.getStartTimeEachDay() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Start Time for each Day " +
                        "is required"));
            }
            return null;
        }
        if (schedule.getDirection() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Direction is " +
                        "required"));
            }
            return null;
        }

        final String url = deviceManager.deviceServiceUrl("apiv1/devices/" + deviceKey + "/schedules/" +
                scheduleKey + ".json");
        AylaSchedule.Wrapper scheduleWrapper = new AylaSchedule.Wrapper();
        scheduleWrapper.schedule = schedule;

        final String postBodyString = getGsonNullable().toJson
                (scheduleWrapper, AylaSchedule.Wrapper.class);
        AylaAPIRequest request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                postBodyString,
                null,
                AylaSchedule.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaSchedule.Wrapper>() {
                    @Override
                    public void onResponse(AylaSchedule.Wrapper response) {
                        AylaSchedule schedule = response.schedule;
                        schedule.setDevice(AylaDevice.this);
                        successListener.onResponse(schedule);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches an Array of existing AylaSchedules from the cloud for this device
     *
     * @param successListener Listener to receive on successful fetch of AylaSchedules
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Fetch an array of Schedules
     */
    public AylaAPIRequest fetchSchedules(final Response.Listener<AylaSchedule[]> successListener,
                                         final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        Number deviceKey = getKey();
        if (deviceKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("deviceKey is required"));
            }
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + deviceKey + "/schedules.json");
        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaSchedule.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaSchedule.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaSchedule.Wrapper[] response) {
                        AylaSchedule[] schedules
                                = AylaSchedule.Wrapper.unwrap(response);
                        for (AylaSchedule schedule : schedules) {
                            schedule.setDevice(AylaDevice.this);
                        }
                        successListener.onResponse(schedules);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Creates an AylaSchedule in the cloud
     *
     * @param schedule        AylaSchedule
     * @param successListener Listener to receive on successful creation of Schedule
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to Create Schedule
     */
    @Deprecated
    public AylaAPIRequest createSchedule(final AylaSchedule schedule,
                                         final Response.Listener<AylaSchedule> successListener,
                                         final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }
        Number deviceKey = getKey();
        if (deviceKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("deviceKey is required"));
            }
            return null;
        }
        if (schedule == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Ayla Schedule"));
            }
            return null;
        }
        if (schedule.getName() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Name is " +
                        "required"));
            }
            return null;
        }
        if (schedule.getStartTimeEachDay() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Start Time for each Day " +
                        "is required"));
            }
            return null;
        }
        if (schedule.getDirection() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Direction is " +
                        "required"));
            }
            return null;
        }

        AylaSchedule.Wrapper scheduleWrapper = new AylaSchedule.Wrapper();
        scheduleWrapper.schedule = schedule;

        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (scheduleWrapper, AylaSchedule.Wrapper.class);
        String url = deviceManager.deviceServiceUrl("apiv1/devices/" + deviceKey + "/schedules.json");

        AylaAPIRequest request = new AylaJsonRequest<>(
                Request.Method.POST,
                url,
                postBodyString,
                null,
                AylaSchedule.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaSchedule.Wrapper>() {
                    @Override
                    public void onResponse(AylaSchedule.Wrapper response) {
                        AylaSchedule schedule = response.schedule;
                        schedule.setDevice(AylaDevice.this);
                        successListener.onResponse(schedule);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Deletes an existing AylaSchedule from the cloud
     *
     * @param schedule        AylaSchedule that needs to be deleted. This is the
     *                        existing AylaSchedule that has been fetched from service
     * @param successListener Listener to receive on successful deletion of AylaSchedule
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to delete a Schedule
     */
    @Deprecated
    public AylaAPIRequest deleteSchedule(final AylaSchedule schedule,
                                         final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                 successListener,
                                         final ErrorListener errorListener) {
        AylaDeviceManager deviceManager = getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return null;
        }

        if (schedule == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid Schedule"));
            }
            return null;
        }
        Number scheduleKey = schedule.getKey();
        if (scheduleKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Schedule Key is required"));
            }
            return null;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/schedules/" + scheduleKey + ".json");
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaAPIRequest<>(
                Request.Method.DELETE, url, null, AylaAPIRequest.EmptyResponse.class,
                sessionManager, successListener, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Enables an existing AylaSchedule
     *
     * @param schedule        AylaSchedule that needs to be Enabled. This is the
     *                        existing AylaSchedule that has been fetched from service
     * @param successListener Listener to receive on successful enabling of AylaSchedule
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to enable a Schedule
     */
    public AylaAPIRequest enableSchedule(final AylaSchedule schedule,
                                         final Response.Listener<AylaSchedule> successListener,
                                         final ErrorListener errorListener) {
        AylaSchedule aylaSchedule = null;
        try {
            aylaSchedule = (AylaSchedule) schedule.clone();
        } catch (CloneNotSupportedException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError(e.getMessage()));
            }
            return null;
        }
        aylaSchedule.setActive(true);
        return updateSchedule(aylaSchedule, successListener, errorListener);
    }

    /**
     * Disables an existing AylaSchedule
     *
     * @param schedule        AylaSchedule that needs to be Disabled. This is the
     *                        existing AylaSchedule that has been fetched from service
     * @param successListener Listener to receive on successful enabling of AylaSchedule
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to disable a Schedule
     */
    public AylaAPIRequest disableSchedule(final AylaSchedule schedule,
                                          final Response.Listener<AylaSchedule> successListener,
                                          final ErrorListener errorListener) {
        AylaSchedule aylaSchedule = null;
        try {
            aylaSchedule = (AylaSchedule) schedule.clone();
        } catch (CloneNotSupportedException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError(e.getMessage()));
            }
            return null;
        }
        aylaSchedule.setActive(false);
        return updateSchedule(aylaSchedule, successListener, errorListener);
    }

    /**
     * Refresh the LAN configuration for this device.
     */
    public void refreshLanConfig(){
        fetchLanConfig(new Response.Listener<AylaLanConfig>() {
            @Override
            public void onResponse(AylaLanConfig response) {
                AylaLog.d(LOG_TAG, "refreshed LanConfig");
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "refresh LanConfig failed "+error);
            }
        });
    }

    //Todo: Make required changes after https://aylanetworks.atlassian.net/browse/SVC-4025 is fixed
    /**
     * Update device location in the Ayla service
     * @param latitude Latitude of the new location
     * @param longitude Longitude of the new location
     * @param locationProvider Location Provider type for this location update
     * @param successListener Listener to receive results on success
     * @param errorListener Listener to receive error information on failure
     */
    public AylaAPIRequest updateLocation(final String latitude,
                                         final String longitude,
                                         LocationProvider locationProvider,
                                         final Response.Listener<AylaDevice> successListener,
                                         final ErrorListener errorListener ){
        AylaDeviceManager deviceManager = getDeviceManager();
        final AylaDevice device = this;
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return null;
        }
        Number deviceKey = getKey();
        if (deviceKey == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("deviceKey not found"));
            }
            return null;
        }
        String url = deviceManager.deviceServiceUrl("apiv1/devices/"+deviceKey+"/locations.json");
        final JSONObject locationObject = new JSONObject();
        final JSONObject bodyObject = new JSONObject();
        try {
            locationObject.put("lat", Double.valueOf(latitude));
            locationObject.put("long", Double.valueOf(longitude));
            locationObject.put("provider", locationProvider._value);
            bodyObject.put("location", locationObject);
        } catch (JSONException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create " +
                    "datum json message", e));
            return null;
        }
        AylaJsonRequest<EmptyResponse> request = new AylaJsonRequest<>(Method.POST, url,
                bodyObject.toString(), null, EmptyResponse.class, getSessionManager(),
                new Response.Listener<EmptyResponse>() {
                    @Override
                    public void onResponse(EmptyResponse response) {
                        updateFrom(String.valueOf(latitude), String.valueOf(longitude),
                                DataSource.CLOUD);
                        successListener.onResponse(device);
                    }
                }, errorListener);
        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    private void sendToMetricsManager(AylaMetric metric){
        AylaMetricsManager metricsManager = AylaNetworks.sharedInstance().getMetricsManager();
        if(metricsManager != null){
            metricsManager.addMessageToUploadsQueue(metric);
        } else{
            AylaLog.d(LOG_TAG, "Metrics manager is null in AylaDevice. Upload failed");
        }
    }

    /**
     * This gson can serializeNulls,hence different from AylaNetworks getGson() method.
     * @return Gson object
     */
    protected Gson getGsonNullable() {
        if (_gson == null) {
            // Create the Gson object
            final GsonBuilder builder = new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .excludeFieldsWithoutExposeAnnotation()
                    .serializeNulls()
                    .registerTypeAdapterFactory(new AylaTypeAdapterFactory());
            _gson = builder.create();
        }
        return _gson;
    }

    /**
     * A device is online means it's working in LAN mode, or connected to cloud through
     * either mobile or Wi-Fi networks. For BLE devices, it also means that the device
     * has been paired with the mobile phone or the BLE gateway.
     */
    public boolean isOnline() {
        return ConnectionStatus.Online.equals(getConnectionStatus()) || isLanModeActive();
    }

    /**
     * Check if the device properties are available. For some reasons, network problems e.g.,
     * device properties might not have been fetched from the cloud, so property related
     * operations, such as updating UI and reading property value, should ensure that the
     * corresponding properties are in place.
     *
     * <p>Note that this method is different from {@link #getHasProperties()} which
     * represents if there are properties defined for this device.</p>
     *
     */
    public boolean hasProperties() {
        return getProperties() != null && getProperties().size() > 0;
    }

    /**
     * Check if a specific property is available. For some reasons, network problems e.g.,
     * device properties might not have been fetched from the cloud, so property related
     * operations, such as updating UI and reading property value, should ensure that the
     * corresponding property is in place.
     * @param propertyName the property name to be checked.
     * @return true is the property is available, otherwise returns false.
     */
    public boolean hasProperty(String propertyName) {
        return hasProperties() && getProperty(propertyName) != null;
    }

}