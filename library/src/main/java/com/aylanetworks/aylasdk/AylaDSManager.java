package com.aylanetworks.aylasdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.aylanetworks.aylasdk.AylaDevice.DataSource;
import com.aylanetworks.aylasdk.change.ListChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.AylaError.ErrorType;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDevice;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.crossbar.autobahn.websocket.WebSocketConnection;
import io.crossbar.autobahn.websocket.exceptions.WebSocketException;
import io.crossbar.autobahn.websocket.interfaces.IWebSocketConnectionHandler;
import io.crossbar.autobahn.websocket.types.ConnectionResponse;

import static com.aylanetworks.aylasdk.AylaDSSubscription.Wrapper;
import static com.aylanetworks.aylasdk.AylaDeviceManager.DeviceManagerListener;
import static com.aylanetworks.aylasdk.AylaDeviceManager.DeviceManagerState;

/*
 * Android_AylaSDK
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * The AylaDSManager is an internal SDK component used to keep devices updated using the Device
 * Stream service of Ayla Networks.
 */
public class AylaDSManager implements IWebSocketConnectionHandler,
        DeviceManagerListener, AylaConnectivity.AylaConnectivityListener{

    final private static String LOG_TAG = "DSS_LOGS";
    final private static String NO_DEVICES_ERROR="Device List empty";
    final private static int DEFAULT_RETRY_INTERVAL_MS = 3000;
    final private static String DS_NAME = "ANDROID_DSS";
    final private static String DS_DESCRIPTION = "DATAPOINT";
    final private static String CONNECTIVITY_EVENT = "connectivity";
    final private static String DATAPOINT_EVENT = "datapoint";
    final private static String DATAPOINT_ACK_EVENT = "datapointack";
    final private static String KEY_DSS_PREFS = "com.aylanetworks.aylasdk.ayladsmanager";
    final private static String KEY_DSS_SUBSCRIPTION = "key_dss_subscription";
    final private static String KEEP_ALIVE = "1|X";
    final private static String HEARTBEAT = "1|Z";
    final private static int DEFAULT_HEARTBEAT_COUNT = 3;
    final private static int DEFAULT_HEARTBEAT_INTERVAL_MS = 30000;
    private boolean _updateFailed = false; //To handle disconnect() called when update fails.

    public AylaDSSubscription getAylaSubscription() {
        return _aylaSubscription;
    }
    private AylaDSSubscription _aylaSubscription;
    private WebSocketConnection _aylaWebSocketConn;
    private int _subscriptionRetryCount; //retry count for failed subscription CRUD
    private int _connectRetryCount; //retry count for socket connect
    private boolean _pausedState; //To check paused state of DSSManager
    private Handler _heartbeatHandler;
    private Runnable _heartbeatRunnable;
    private int _heartbeatCounter;

    // Handler used to call connect() on the sockets, as they block longer than they should
    private Handler _connectHandler;

    private DSManagerState _state = DSManagerState.Uninitialized;
    /**
     * Handler for retrying websocket connection
     */
    private Handler _retryHandler;

    /**
     * Weak reference to the AylaSessionManager that owns this object.
     */
    private WeakReference<AylaSessionManager> _sessionManagerRef;
    /**
     * Request queue for DSS messages
     */
    private RequestQueue _dssRequestQueue;

    /**
     * Set of DSManagerListeners to be notified of changes
     */
    final private Set<DSManagerListener> _dsManagerListeners;

    /**
     * Subscription types from AylaSystemSettings
     */
    private String[] _subscriptionTypeList;

    public void addListener(DSManagerListener listener){
        _dsManagerListeners.add(listener);
    }

    public void removeListener(DSManagerListener listener){
        _dsManagerListeners.remove(listener);
    }

    public enum DSManagerState {
        Uninitialized,              // Initial state of the DSmanager
        Initialized,                // DSManager is initialized, and subscription is getting
        // created.
        Connecting,                 // DSManager is between start of create subscription and
                                    // connection complete
        Connected,                  // Websocket connected
        Disconnected,               // Websocket disconnected
    }

    public enum AylaDSSubscriptionType {
        AylaDSSubscriptionTypeConnectivity(CONNECTIVITY_EVENT),
        AylaDSSubscriptionTypeDatapoint(DATAPOINT_EVENT),
        AylaDSSubscriptionTypeDatapointAck(DATAPOINT_ACK_EVENT);

        AylaDSSubscriptionType(String value) {
            _stringValue = value;
        }

        public String stringValue() {
            return _stringValue;
        }

        private String _stringValue;
    }


    /**
     * Initialize AylaDSManager.
     * @param sessionManager SessionManager for this session.
     */
    public AylaDSManager(AylaSessionManager sessionManager){
        // Create our handler for connecting to sockets
        HandlerThread ht = new HandlerThread("DSSConnectHandler");
        ht.start();
        _connectHandler = new Handler(ht.getLooper());

        _sessionManagerRef = new WeakReference<>(sessionManager);
        _updateFailed = false;
        enableRetryConnect();
        enableRetrySubscription();
        _retryHandler = new Handler(getContext().getMainLooper());

        // Create and start DSS request queue
        Cache cache = new DiskBasedCache(getContext().getCacheDir(), 1024 * 1024);
        // Set up the HTTPURLConnection network stack
        Network network = new BasicNetwork(new HurlStack());
        _dssRequestQueue = new RequestQueue(cache, network);
        _dssRequestQueue.start();
        _heartbeatHandler = new Handler(Looper.getMainLooper());
        _heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                _heartbeatHandler.removeCallbacksAndMessages(null);
                _heartbeatCounter--;
                AylaLog.d(LOG_TAG, "_heartbeatCounter "+_heartbeatCounter);
                if(_heartbeatCounter < 0){
                   disconnectSocket();
                } else{
                    _heartbeatHandler.postDelayed(this, DEFAULT_HEARTBEAT_INTERVAL_MS);
                }

            }
        };
        getDeviceManager().addListener(this);
        _dsManagerListeners = new HashSet<>();
        _state = DSManagerState.Uninitialized;
        _subscriptionTypeList = AylaNetworks.sharedInstance().getSystemSettings().dssSubscriptionTypes;
        if(_subscriptionTypeList == null || _subscriptionTypeList.length == 0){
            _subscriptionTypeList = new String[]{
                    AylaDSSubscriptionType.AylaDSSubscriptionTypeDatapoint.stringValue(),
                    AylaDSSubscriptionType.AylaDSSubscriptionTypeDatapointAck.stringValue() };
        }
    }

    //Internal methods to enable/diable request retry
    private void enableRetrySubscription(){
        _subscriptionRetryCount = 1;
    }

    private void disableRetrySubscription(){
        _subscriptionRetryCount = 0;
    }

    private void enableRetryConnect(){
        _connectRetryCount = 1;
    }

    private void disableRetryConnect(){
        _connectRetryCount = 0;
    }
    /*
     * @return true if dss is currently connected.
     */

    public boolean isConnected() {
        return (_state == DSManagerState.Connected);
    }

    public boolean isConnecting(){
        return (_state == DSManagerState.Connecting);
    }

    /**
     * @return the SessionManager that owns this object
     */
    public AylaSessionManager getSessionManager() {
        return _sessionManagerRef.get();
    }

    /**
     * @return current deviceManager instance.
     */
    public AylaDeviceManager getDeviceManager() {
        return getSessionManager().getDeviceManager();
    }

    /**
     *
     * @return retry interval for create subscription and connect to websocket.
     */
    public int getRetryInterval() {
        return DEFAULT_RETRY_INTERVAL_MS;
    }

    /**
     * Returns a URL for the Datastream service pointing to the specified path.
     * This URL will vary depending on the AylaSystemSettings provided to the CoreManager
     * during initialization.
     *
     * @param path Path of the URL to be returned, e.g. "api/v1/subscriptions.json"
     * @return Full URL, e.g. "https://stream.aylanetworks.com/api/v1/subscriptions"
     */
    public String datastreamServiceUrl(String path) {
        return AylaNetworks.sharedInstance().getServiceUrl(ServiceUrls.CloudService.Datastream,
                path);
    }

    /**
     * Returns a URL for the MDSS REST service pointing to the specified path.
     * This URL will vary depending on the AylaSystemSettings provided to the CoreManager
     * during initialization.
     *
     * @param path Path of the URL to be returned, e.g. "api/v1/subscriptions.json"
     * @return Full URL, e.g. "https://stream.aylanetworks.com/api/v1/subscriptions"
     */
    public String mdssRESTServiceUrl(String path) {
        return AylaNetworks.sharedInstance().getServiceUrl(ServiceUrls.CloudService.mdssSubscription,
                path);
    }


    /**
     * Internal method used to retry connection to websocket.
     */
    private void retrySocketConnection() {
        Log.d(LOG_TAG, "retrySocketConnection retryCount " + _subscriptionRetryCount);
        if (_connectRetryCount > 0) {
            disableRetryConnect();
            _retryHandler.removeCallbacksAndMessages(null);
            _retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AylaLog.v(LOG_TAG, "Retry websocket connection");
                    if(_aylaSubscription != null){
                        connectToSocket(_aylaSubscription.getStreamKey());
                    }
                }
            }, getRetryInterval());
        }
    }

    /**
     * Internal method used to retry create subscription.
     */
    private void retryCreateSubscription() {
        Log.d(LOG_TAG, "retryCreateSubscription retryCount " + _subscriptionRetryCount);
        if (_subscriptionRetryCount > 0) {
            _retryHandler.removeCallbacksAndMessages(null);
            _retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AylaLog.v(LOG_TAG, "Retry subscription create");
                    _state = DSManagerState.Initialized;
                    createSubscription(DS_NAME, DS_DESCRIPTION, null,
                            new Listener<AylaDSSubscription>() {
                                @Override
                                public void onResponse(AylaDSSubscription response) {
                                    enableRetrySubscription();
                                    _aylaSubscription = response;
                                    connectToSocket(response.getStreamKey());
                                }
                            }, new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    AylaLog.d(LOG_TAG, "Retry createSubscription failed. error" +
                                            error);
                                    disableRetrySubscription();
                                    _state = DSManagerState.Disconnected;
                                    notifyDsChange(false);
                                }
                            });
                }
            }, getRetryInterval());
        }
    }

    /**
     * Returns the current state of the DSManager.
     *
     * @return The current state of the DSManager.
     */
    public DSManagerState getState() {
        return _state;
    }

    /**
     * Enqueues the provided request to DSS service.
     * @param request the request to send to DSS service.
     * @return the request, which can be used for cancellation.
     */
    public AylaAPIRequest sendDSSServiceRequest(AylaAPIRequest request) {
        request.setShouldCache(false);
        request.logResponse();
        _dssRequestQueue.add(request);
        return request;
    }

    /**
     * Create a single subscription for a device list that subscribes to datapoint changes.
     * @param name Name of subscription.
     * @param description Brief description for this subscription.
     * @param deviceList List of devices whose datapoints are to be tracked by this subscription.
     *                   If null, subscription will include all devices that the user has access to.
     * @param successListener Listener to receive results on success.
     * @param errorListener Listener to receive error information.
     * @return AylaAPIRequest request queued up for this request.
     */
    public AylaAPIRequest createSubscription(String name, String description,
                                             List<AylaDevice> deviceList,
                                             final Listener<AylaDSSubscription> successListener,
                                             ErrorListener errorListener){
        String deviceDSNs = null;
        if(deviceList != null && deviceList.size() > 0){
            deviceDSNs = getDeviceDSNs(deviceList);
        }
        String url = mdssRESTServiceUrl("api/v1/subscriptions");
        String subscriptionTypes = ObjectUtils.getDelimitedString(_subscriptionTypeList, ",");
        final JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("description", description);
            jsonObject.put("dsn", deviceDSNs);
            jsonObject.put("property_name", "*");
            jsonObject.put("client_type", "mobile");
            jsonObject.put("batch_size", "1");
            jsonObject.put("subscription_type", subscriptionTypes);
        } catch (JSONException e) {
            errorListener.onErrorResponse(new AylaError(ErrorType.JsonError,
                    "JSONException in createSubscription "));
            return null;
        }
        Log.d(LOG_TAG, "Create subscription url "+url);
        Log.d(LOG_TAG, "Create subscription request body " + jsonObject.toString());
        final byte[] requestBody = jsonObject.toString().getBytes();

        AylaAPIRequest<Wrapper> request = new AylaAPIRequest<Wrapper>(Request.Method.POST, url,
                null, Wrapper.class, getSessionManager(),
                new Listener<Wrapper>(){
                    @Override
                    public void onResponse(Wrapper response) {
                        if(response.subscription != null){
                            // Save subscription key to get mitigate issue -
                            // https://aylanetworks.atlassian.net/browse/JVS-480 . The key will
                            // be deleted once it is used to open to a websocket connection.
                            saveSubscriptionKey(response.subscription.getStreamKey());
                        }
                        successListener.onResponse(response.subscription);
                    }
                }, errorListener){
            @Override
            public byte[] getBody() throws AuthFailureError {
                return requestBody;
            }
            @Override
            public String getBodyContentType() {
                return AylaHttpServer.MIME_JSON;
            }
        };
        sendDSSServiceRequest(request);
        return request;
    }

    /**
     * Updates a single subscription.
     * @param subscription subscription to be updated.
     * @param deviceList List of devices whose datapoints are to be tracked by this subscription.
     * @param successListener Listener to receive results on success.
     * @param errorListener Listener to receive error information.
     * @return AylaAPIRequest queued up for this request.
     */
    public AylaAPIRequest updateSubscription(AylaDSSubscription subscription,
                                             List<AylaDevice> deviceList,
                                             final Listener<AylaDSSubscription> successListener,
                                             ErrorListener errorListener){

        if(subscription.getId() == null || subscription.getId().isEmpty()){
            errorListener.onErrorResponse(new PreconditionError("Subscription Id is required"));
            return null;
        }
        if(deviceList == null || deviceList.size() == 0){
            errorListener.onErrorResponse(new PreconditionError(NO_DEVICES_ERROR));
            return null;
        }
        String deviceDSNs = getDeviceDSNs(deviceList);
        String url = mdssRESTServiceUrl("api/v1/subscriptions");
        String subscriptionTypes = ObjectUtils.getDelimitedString(_subscriptionTypeList, ",");
        final JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("id", subscription.getId());
            jsonObject.put("name", subscription.getName());
            jsonObject.put("description", subscription.getDescription());
            jsonObject.put("property_name", "*");
            jsonObject.put("client_type", "mobile");
            jsonObject.put("batch_size", "1");
            jsonObject.put("subscription_type", subscriptionTypes);
            jsonObject.put("stream_key", subscription.getStreamKey() );
            jsonObject.put("oem_model", subscription.getOemModel());
            jsonObject.put("dsn", deviceDSNs);
            Log.d(LOG_TAG, "Update subscription request body " + jsonObject.toString());
        } catch (JSONException e) {
            errorListener.onErrorResponse(new AylaError(ErrorType.JsonError,
                    "JSONException in updateSubscription "));
            return null;
        }


        AylaAPIRequest request = new AylaAPIRequest<Wrapper>(Request.Method.PUT,
                url, null, Wrapper.class, getSessionManager(),
                new Listener<Wrapper>(){

                    @Override
                    public void onResponse(Wrapper response) {
                        successListener.onResponse(response.subscription);
                    }
                }, errorListener){
            @Override
            public byte[] getBody() throws AuthFailureError {
                return jsonObject.toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return AylaHttpServer.MIME_JSON;
            }
        };
        sendDSSServiceRequest(request);
        return request;
    }

    /**
     * Fetch a single subscription from DSS service.
     * @param subscriptionId Id of the subscription to be fetched.
     * @param successListener Listener to receive results on success.
     * @param errorListener Listener to receive error information.
     * @return AylaAPIRequest queued up for this request.
     */
    public AylaAPIRequest fetchSubscription(String subscriptionId,
                                            final Listener<AylaDSSubscription> successListener,
                                            ErrorListener errorListener) {

        if (subscriptionId == null || subscriptionId.isEmpty()) {
            errorListener.onErrorResponse(new PreconditionError("Subscription Id is required"));
            return null;
        }
        String url = mdssRESTServiceUrl("api/v1/subscriptions/" + subscriptionId);
        AylaAPIRequest request = new AylaAPIRequest<>(Request.Method.GET, url, null, Wrapper.class,
                getSessionManager(), new Listener<Wrapper>() {

            @Override
            public void onResponse(Wrapper response) {
                successListener.onResponse(response.subscription);
            }
        }, errorListener);
        sendDSSServiceRequest(request);
        return request;

    }

    /**
     * Fetch all subscriptions for the user from Datastream service.
     * @param successListener Listener to receive results on success.
     * @param errorListener Listener to receive error information.
     * @return AylaAPIRequest queued up for this request.
     */
    public AylaAPIRequest fetchAllSubscriptions(
            final Listener<AylaDSSubscription[]> successListener,
            ErrorListener errorListener) {
        String url = mdssRESTServiceUrl("api/v1/subscriptions");
        AylaAPIRequest request = new AylaAPIRequest<>(Request.Method.GET, url, null,
                Wrapper[].class, getSessionManager(), new Listener<Wrapper[]>() {

            @Override
            public void onResponse(Wrapper[] response) {
                successListener.onResponse(Wrapper.unwrap(response));
            }
        }, errorListener);
        sendDSSServiceRequest(request);
        return request;
    }

    /**
     * Delete a subscription from Datastream service.
     * @param subscriptionId Id of the subscription to be deleted.
     * @param successListener Listener to receive results on success.
     * @param errorListener Listener to receive error information.
     *
     * @return the AylaAPIRequest for this operation
     */
    public AylaAPIRequest deleteSubscription(String subscriptionId,
                                             Listener<EmptyResponse> successListener,
                                             ErrorListener errorListener){
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            errorListener.onErrorResponse(new PreconditionError("Subscription Id is required"));
            return null;
        }
        String url = mdssRESTServiceUrl("api/v1/subscriptions");
        url += "/"+subscriptionId;
        AylaAPIRequest<EmptyResponse> request = new AylaAPIRequest<EmptyResponse>(
                Request.Method.DELETE, url, null, EmptyResponse.class, getSessionManager(),
                successListener, errorListener);
        sendDSSServiceRequest(request);
        return request;
    }

    /**
     * Internal method to connect to websocket using the stream_key in subscription.
     */
    private void connectToSocket(final String stream_key){
        // Although it might appear asynchronous, the call to connect() is blocking. That means
        // this needs to run on a background thread.
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "connectToSocket()");
                // check if app is not paused in between
                if (getDeviceManager().getState()!= DeviceManagerState.Paused) {
                    _state = DSManagerState.Connecting;
                    _aylaWebSocketConn = new WebSocketConnection();
                    String url = datastreamServiceUrl("");
                    url += "stream?stream_key="+stream_key;
                    url = url.replaceFirst("https", "wss"); //Todo: This will  be changed in the service
                    Log.d(LOG_TAG, "connectToSocket url " + url);
                    try {
                        // connection status is received in onOpen() or onClose() methods in
                        // WebSocketCOnnectionObserver interface. DSSManager state changes are notified to
                        // all listeners in these methods.
                        _aylaWebSocketConn.connect(url, AylaDSManager.this);
                    } catch (WebSocketException e) {
                        Log.d(LOG_TAG, "Exception in connectToSocket() "+e.getMessage());
                        onClose(IWebSocketConnectionHandler.CLOSE_INTERNAL_ERROR, "exception "+e);
                    }
                }
            }
        };

        _connectHandler.post(r);
    }

    /**
     * Internal method to disconnect from websocket
     */
    private void disconnectSocket(){
        AylaLog.d(LOG_TAG, "disconnectSocket()");
        disableRetrySubscription();
        disableRetryConnect();

        if(_aylaWebSocketConn != null){
            _aylaWebSocketConn.sendClose();
        }
        _state = DSManagerState.Disconnected;
        stopHeartBeatRunnable();
        dataSourceChanged();
    }

    /**
     * Fired when the WebSockets connection has been established.
     *
     */
    @Override
    public void onOpen() {
        Log.d(LOG_TAG, "onOpen()");
        deleteSubscriptionKey();
        _state = DSManagerState.Connected;
        // If onOpen is called from a retry method, it should be set back to enabled here.
        enableRetryConnect();
        notifyDsChange(true);
        //Fetch properties and stop polling if not in LAN mode
        dataSourceChanged();
        startHeartBeatRunnable();
    }

    /**
     * Fired when the WebSockets connection has deceased (or could be
     * not established in the first place).
     *
     * @param code       Close code.
     * @param reason     Close reason (human-readable).
     */
    @Override
    public void onClose(int code, String reason) {
        Log.d(LOG_TAG, "onClose() code: "+code);
        Log.d(LOG_TAG, "onClose() reason: "+reason);
        Log.d(LOG_TAG, "onClose() retryCount: "+ _subscriptionRetryCount);
        _aylaWebSocketConn = null;
        _state = DSManagerState.Disconnected;
        notifyDsChange(false);
       _aylaSubscription = null;
    }

    @Override
    public void setConnection(WebSocketConnection connection) {
        Log.d(LOG_TAG, "setConnection");
    }

    @Override
    public void onConnect(ConnectionResponse response) {
        Log.d(LOG_TAG, "onConnect");
    }

    @Override
    public void onPing() {
        Log.d(LOG_TAG, "onPing");
    }

    @Override
    public void onPing(byte[] payload) {
        Log.d(LOG_TAG, "onPing");
    }

    @Override
    public void onPong() {
        Log.d(LOG_TAG, "onPong");
    }

    @Override
    public void onPong(byte[] payload) {
        Log.d(LOG_TAG, "onPong");
    }


    @Override
    public void onMessage(byte[] payload, boolean isBinary) {
        Log.d(LOG_TAG, "onMessage");
    }

    /**
     * Fired when a text message has been received (and text
     * messages are not set to be received raw).
     *
     * @param payload    Text message payload or null (empty payload).
     */
    @Override
    public void onMessage(String payload) {
        AylaLog.d(LOG_TAG, "onTextMessage() " + payload);
        if(_aylaWebSocketConn == null){
            return;
        }
        if(payload != null){
            //update properties here
            String dsMessage;
            if(payload.equals(HEARTBEAT) && isConnected()){
                _aylaWebSocketConn.sendMessage(payload);
                _heartbeatCounter++;
                AylaLog.d(LOG_TAG, "_heartbeatCounter "+_heartbeatCounter);
            } else if(!payload.equals(KEEP_ALIVE)){
                int startIndex = payload.indexOf("|");
                if(startIndex != -1 ){
                    int messageStartIndex = startIndex + 1;
                    dsMessage = payload.substring(messageStartIndex);
                    Log.d(LOG_TAG, "DataStream message "+dsMessage);
                    Gson gson = AylaNetworks.sharedInstance().getGson();
                    AylaDataStream dataStream;
                    try {
                        dataStream = gson.fromJson(dsMessage, AylaDataStream.class);
                    } catch (JsonSyntaxException e) {
                        AylaLog.e(LOG_TAG, "Bad JSON syntax in DSS message: \n" + dsMessage);
                        return;
                    }
                    updateDevices(dataStream);
                }
            }
        }

    }

    public void onPause(){
        Log.d(LOG_TAG, "onPause() getConnectivity(): "+getConnectivity());
        //disable all retries
        disableRetryConnect();
        disableRetrySubscription();
        _updateFailed = false;
        disconnectSocket();
        _pausedState = true;
        _dssRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });
    }

    /**
     * Returns true if the DSManager is paused, otherwise returns false
     * @return true if the DSManager is in the paused state
     */
    public boolean isPaused() {
        return _pausedState;
    }

    private void startHeartBeatRunnable(){
        _heartbeatCounter = DEFAULT_HEARTBEAT_COUNT;
        _heartbeatHandler.postDelayed(_heartbeatRunnable, DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartBeatRunnable(){
        _heartbeatHandler.removeCallbacksAndMessages(null);
    }

    /**
     *
     * @return application context.
     */
    public Context getContext(){
        return AylaNetworks.sharedInstance().getContext();
    }

    private AylaConnectivity getConnectivity(){
        return AylaNetworks.sharedInstance().getConnectivity();
    }

    public void onResume(){
        Log.d(LOG_TAG, "onResume() _aylaSubscription: " + _aylaSubscription + " connectivity: " +
                getConnectivity());
        _pausedState = false;
        if(getConnectivity() != null){
            getConnectivity().registerListener(this);
        }
        if(_aylaSubscription == null){
            if(!getDeviceManager().getDevices().isEmpty() && _state != DSManagerState.Initialized){
                _state = DSManagerState.Initialized;

                String streamKey = getSavedSubscriptionKey();
                if(streamKey != null){
                    if(!isConnected() && !isConnecting()){
                        connectToSocket(streamKey);
                    }
                } else{
                    createSubscription(DS_NAME, DS_DESCRIPTION, null,
                            new Listener<AylaDSSubscription>() {
                                @Override
                                public void onResponse(AylaDSSubscription response) {
                                    _aylaSubscription = response;
                                    Log.d(LOG_TAG, "subscription created "+_aylaSubscription);
                                    if(response != null){
                                        connectToSocket(response.getStreamKey());
                                    } else{
                                        AylaLog.e(LOG_TAG, "Null response received in" +
                                                "createSubscription. Unable to connect to websocket");
                                    }
                                }
                            }, new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    Log.e(LOG_TAG, "Error in createSubscription. Start retry create " +
                                            "subscription "+error.getErrorType() + " , "+error.getMessage());
                                    retryCreateSubscription();


                                }
                            });
                }
            }

        } else if(!isConnected() && !isConnecting()) {
           connectToSocket(_aylaSubscription.getStreamKey());
        }
    }

    @Override
    public void deviceManagerInitComplete(Map<String, AylaError> deviceFailures) {
        Log.d(LOG_TAG, "deviceManagerInitComplete() state " + _state);
        if(!isConnecting() && !isConnected()){
            enableRetryConnect();
            enableRetrySubscription();
            onResume();
        }
    }

    @Override
    public void deviceManagerInitFailure(AylaError error,
                                         DeviceManagerState failureState) {
        onPause();

    }

    @Override
    public void deviceListChanged(ListChange change) {
        Log.d(LOG_TAG, "deviceListChanged aylaSubscription " + _aylaSubscription + "state " +
                _state);
        // To handle case where the first device is registered in the account.
        if(_state == DSManagerState.Disconnected && !_pausedState){
            onResume();
        }
    }

    @Override
    public void deviceManagerError(AylaError error) {
        //Cancel DSS here, as we need device manager up and running for DSS
        if(getDeviceManager().getState() == DeviceManagerState.Paused){
            onPause();
        }
    }

    @Override
    public void deviceManagerStateChanged(DeviceManagerState oldState,
                                          DeviceManagerState newState) {
        if(newState == DeviceManagerState.Error || newState == DeviceManagerState.Paused){
            onPause();
        }

    }

    @Override
    public void connectivityChanged(boolean wifiEnabled, boolean cellularEnabled) {
        Log.d(LOG_TAG, "Connectivity change. _state " + _state);
        if(AylaNetworks.sharedInstance().getSystemSettings().allowDSS){
            if(wifiEnabled || cellularEnabled){
                if(!isConnected() && !_pausedState){
                    //Network connectivity is available. All retries should be set
                    enableRetryConnect();
                    enableRetrySubscription();
                    if(!isConnecting()){
                        onResume();
                    }
                }
            } else{
                _state = DSManagerState.Disconnected;
                notifyDsChange(false);
            }
        }
    }

    /**
     * The DSManagerListener interface is used to provide notifications of changes to the
     * AylaDSManager. Listeners implementing this interface may register for DSManager changes.
     */
    public interface DSManagerListener{
        void dsManagerConnectionChanged(boolean isConnected);

    }

    private void notifyDsChange(final boolean isConnected) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if(_dsManagerListeners != null){
                    List<DSManagerListener> listeners;
                    synchronized (_dsManagerListeners) {
                        listeners = new ArrayList<>(_dsManagerListeners);
                    }
                    for (DSManagerListener listener : listeners) {
                        listener.dsManagerConnectionChanged(isConnected);
                    }
                }
            }
        });
    }

    private void updateDevices(AylaDataStream dataStream){
        String dsns = dataStream.getMetadata().getDsn();
        Log.d(LOG_TAG, "Changed devices "+dsns);
        String[] dsnList = dsns.split(",");
        for (String dsn : dsnList) {
            AylaDevice device = getDeviceManager().deviceWithDSN(dsn);
            if(device != null){
                if(dataStream.getMetadata() != null){
                    String eventType = dataStream.getMetadata().getEventType();
                    if (eventType != null){
                        switch (eventType){
                            case CONNECTIVITY_EVENT:
                                String connecitonStatus = dataStream.getConnection().getStatus();
                                device.updateFrom(connecitonStatus.equals(
                                        AylaDevice.ConnectionStatus.Online.getStringValue())?
                                                AylaDevice.ConnectionStatus.Online:
                                                AylaDevice.ConnectionStatus.Offline,
                                        DataSource.DSS);
                                break;
                            case DATAPOINT_ACK_EVENT:
                            case DATAPOINT_EVENT:
                                if(device instanceof AylaBLEDevice || (!device.isLanModeActive())){
                                    AylaProperty property = device.getProperty(dataStream.getMetadata()
                                            .getPropertyName());
                                    if(property != null) {
                                        // If this property expects an ACK, we cannot update our
                                        // device until the ACK comes in. Just ignore the datapoint
                                        // update here.
                                        if (property.ackEnabled && eventType.equals(DATAPOINT_EVENT)) {
                                            AylaLog.i(LOG_TAG, "Ignoring datapoint event for " +
                                                    "ACK-enabled property " + property.getName());
                                            break;
                                        }
                                        property.updateFrom(dataStream.getDatapoint(), DataSource.DSS);
                                    } else {
                                        AylaLog.e(LOG_TAG, "Event for unknown property " +
                                        dataStream.getMetadata().getPropertyName());
                                    }
                                }
                                break;
                        }
                    }
                }
            } else{
                Log.d(LOG_TAG, "Received DSN that is not in deviceManager "+dsn);
            }
        }
    }

    private String getDeviceDSNs(List<AylaDevice> deviceList){

        int deviceCount = deviceList.size();
        StringBuilder deviceDSNs = new StringBuilder(deviceCount * 16);
        for(int i=0; i < deviceCount; i++){
            if(i != 0) {
                deviceDSNs.append(",");
            }
            deviceDSNs.append(deviceList.get(i).getDsn());
        }
        return deviceDSNs.toString();
    }

    private void dataSourceChanged(){
        if(getDeviceManager().getState() != DeviceManagerState.Paused){
            Log.d(LOG_TAG, "DSManager dataSourceChanged");
            List<AylaDevice> aylaDeviceList =  getDeviceManager().getDevices();
            for(AylaDevice device: aylaDeviceList){
                device.dataSourceChanged(DataSource.DSS);
            }
        }
    }

    private void saveSubscriptionKey(String subscriptionKey){
        Context context = getContext();
        if(context != null){
            SharedPreferences prefs = context.getSharedPreferences(KEY_DSS_PREFS,
                    Context.MODE_PRIVATE);
            if(prefs != null){
                prefs.edit().putString(KEY_DSS_SUBSCRIPTION, subscriptionKey).apply();
            }
        }
    }

    private String getSavedSubscriptionKey(){
        Context context = getContext();
        if(context != null){
            SharedPreferences prefs = context.getSharedPreferences(KEY_DSS_PREFS, Context.MODE_PRIVATE);
            if(prefs != null){
                return prefs.getString(KEY_DSS_SUBSCRIPTION, null);
            }
        }
        return null;
    }

    private void deleteSubscriptionKey(){
        saveSubscriptionKey(null);
    }

}
