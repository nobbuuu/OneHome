package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HurlStack;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.AylaDeviceNode;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.error.AuthError;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.NetworkError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.error.ServerError;
import com.aylanetworks.aylasdk.error.TimeoutError;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;
import com.aylanetworks.aylasdk.util.NetworkUtils;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.cafbit.netlib.dns.NetThread;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * The AylaLanModule is responsible for LAN communication with an AylaDevice object.
 * The AylaDevice creates an AylaLanModule when it wishes to enter LAN communication with the
 * device. The AylaLanModule is responsible for maintaining the LAN session with the device as well
 * as processing commands to or from the device.
 */
// We often return AylaErrors from methods but don't want to throw them
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class AylaLanModule {
    private static final String LOG_TAG = "LanModule";

    private static final String LOCAL_LAN_URI = "/local_lan";

    /**
     * Protocol used. 1 = CBC AES256 / SHA256, nothing else is supported now
     */
    private static final int CRYPTO_PROTO_CBC_AES256 = 1;

    /**
     * Message version, currently must be 1
     */
    private static final int CRYPTO_MESSAGE_VER = 1;

    /**
     * Default keep-alive interval is 10 seconds
     */
    private static final int DEFAULT_KEEP_ALIVE_INTERVAL = 10000;
    /**
     * Default MDNS query interval is 1 second
     */
    private static final int DEFAULT_MDNS_INTERVAL = 1000;
    private final ArrayDeque<LanCommand> _pendingLanCommands;
    private final ArrayList<LanCommand> _commandsPendingResponses;

    private RequestQueue _lanRequestQueue;
    private WeakReference<AylaDevice> _deviceRef;
    private AylaEncryption _encryption;
    private AylaLocalNetwork _aylaLocalNetwork;
    private boolean _isActive;
    private boolean _processingCommandBlock;
    private NetThread _netThread;

    // Keep-alive variables
    private Runnable _keepAliveRunnable;
    private Handler _keepAliveHandler;
    private Handler _ackTimeoutHandler;
    private int _keepAliveInterval = DEFAULT_KEEP_ALIVE_INTERVAL;
    //handler to submit mdns queries in offline mode
    private Runnable _mdnsQueryRunnable;
    private Handler _mdnsQueryHandler;
    private int _mdnsQueryInterval = DEFAULT_MDNS_INTERVAL;
    private WeakReference<AylaHttpServer> _httpServerRef;

    private MDNSListener _mdnsListener;

    /**
     * A number of reasons may prevent device from getting into LAN mode, such as
     * time-out during LAN session, or errors while generating session keys, etc.
     * The last error is saved for the purpose to get to know why device didn't
     * get into LAN mode.
     */
    private AylaError _lastError;

    public AylaLanModule(final AylaDevice device, AylaHttpServer httpServer) {
        _deviceRef = new WeakReference<>(device);
        _pendingLanCommands = new ArrayDeque<>();
        _commandsPendingResponses = new ArrayList<>();

        _encryption = new AylaEncryption(device);
        _httpServerRef = new WeakReference<>(httpServer);

        _mdnsListener = new MDNSListener() {
            @Override
            public void ready() {
                startMDNSQuery();
            }

            @Override
            public void success(String deviceIpAddress) {
                if(_deviceRef.get() != null){
                    _deviceRef.get().setLanIp(deviceIpAddress);
                }
                AylaLog.d(LOG_TAG, "IpAddress found through MDNS "+deviceIpAddress);
                stopMDNSQuery();
                startKeepalive();
                if(_netThread != null){
                    _netThread.submitQuit();
                    _netThread = null;
                }
            }

            @Override
            public void failed(AylaError error) {
                stopMDNSQuery();
                if (_isActive) {
                    AylaLog.e(LOG_TAG, "MDNS failed " + error.getMessage());
                    _isActive = false;
                    _lastError = error;
                    if(_deviceRef.get() != null){
                        _deviceRef.get().notifyLanStateChange(false, error);
                    }
                    if(_netThread != null){
                        _netThread.submitQuit();
                        _netThread = null;
                    }
                }
            }
        };

        // Create our LAN request queue
        Context context = AylaNetworks.sharedInstance().getContext();
        Cache cache = new DiskBasedCache(context.getCacheDir(), 1024 * 1024);
        _aylaLocalNetwork = new AylaLocalNetwork(new BasicNetwork(new HurlStack()));
        _lanRequestQueue = new RequestQueue(cache, _aylaLocalNetwork);
        AylaLanConfig config = device.getLanConfig();
        if (config != null && config.keepAlive != null) {
            // Use the config's LAN timeout (which is in seconds) / 3 for our keep-alive interval
            _keepAliveInterval = (config.keepAlive.intValue() * 1000) / 3;
        }
        _keepAliveHandler = new Handler(Looper.getMainLooper());
        _keepAliveRunnable = new Runnable() {
            @Override
            public void run() {
                sendLocalRegistration();
                _keepAliveHandler.removeCallbacksAndMessages(null);
                _keepAliveHandler.postDelayed(this, getKeepAliveInterval());
            }
        };

        _mdnsQueryHandler = new Handler(Looper.getMainLooper());
        _mdnsQueryRunnable = new Runnable() {
            @Override
            public void run() {
                if(_netThread != null){
                    _netThread.submitQuery();
                }
                _mdnsQueryHandler.removeCallbacksAndMessages(null);
                _mdnsQueryHandler.postDelayed(this, getMdnsQueryInterval());
            }
        };

        _ackTimeoutHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        _lanRequestQueue.start();
        sendLocalRegistration();
        startKeepalive();
    }

    public void stop() {
        stopKeepalive();
        stopMDNSQuery();
        _lanRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });

        _ackTimeoutHandler.removeCallbacksAndMessages(null);

        synchronized (_pendingLanCommands) {
            _pendingLanCommands.clear();
        }

        synchronized (_commandsPendingResponses) {
            _commandsPendingResponses.clear();
        }

        _processingCommandBlock = false;
        _lanRequestQueue.stop();
        _isActive = false;
        AylaDevice d = _deviceRef.get();
        if (d != null) {
            _deviceRef.get().notifyLanStateChange(false);
        }
    }

    public boolean isActive() {
        return _isActive;
    }

    /**
     *
     * @return current value of LAN mode keep alive interval in milliseconds
     */
    public int getKeepAliveInterval() {
        return _keepAliveInterval;
    }

    /**
     * @return current value of MDNS query interval in milliseconds
     */
    public int getMdnsQueryInterval() {
        return _mdnsQueryInterval;
    }

    /**
     * Set MDNS query interval.
     * @param interval in millisconds
     */
    public void setMdnsQueryInterval(int interval) {
        this._mdnsQueryInterval = interval;
    }

    /**
     * Set LAN mode keep alive interval.
     * @param interval in milliseconds
     */
    public void setKeepAliveInterval(int interval) {
        _keepAliveInterval = interval;
    }

    /**
     * Handles requests from the device module for a key exchange.
     *
     * @param keyExchange The KeyExchange object submitted by the module
     * @return the HTTP response for the "key exchange" request
     */
    public NanoHTTPD.Response handleKeyExchangeRequest(KeyExchange keyExchange) {
        // Is this a LAN mode key exchange or a secure setup key exchange?
        if (TextUtils.isEmpty(keyExchange.sec)) {
            return processLanModeKeyExchange(keyExchange);
        } else {
            return processSecureSetupKeyExchange(keyExchange);
        }
    }

    /**
     * Handles incoming requests from the device module for commands. If we have a command queued
     * up, we will return it to the module here.
     *
     * @param uriResource URI of the requested resource
     * @param urlParams   Map of URL parameters substituted by the router
     * @param session     HTTP session information
     * @return the HTTP response for the "get command" request
     */
    @SuppressWarnings("UnusedParameters")
    public NanoHTTPD.Response handleLanCommandRequest(RouterNanoHTTPD.UriResource uriResource,
                                                      Map<String, String> urlParams,
                                                      NanoHTTPD.IHTTPSession session) {
        LanCommand command;
        synchronized (_pendingLanCommands) {
            command = _pendingLanCommands.peek();
        }

        if (command == null) {
            AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Lan command queue is empty");
            // Return an empty response string
            String response = _encryption.encryptEncapsulateSign("{}");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                    AylaHttpServer.MIME_JSON, response);
        }

        String responseString = command.getPayload();
        String encryptedResponse = _encryption.encryptEncapsulateSign(responseString);
        AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Returning command payload: " +
                responseString);

        // If we don't expect a subsequent request to come back for this command, we can
        // remove the command from our queue.
        if (!command.expectsModuleRequest()) {
            command.setModuleResponse("");
            synchronized (_pendingLanCommands) {
                _pendingLanCommands.remove(command);
            }
        }

        // For commands that are ack-enabled, we need to save the command in a separate queue
        // and set a timeout timer in case we don't get the ack message back in time.
        if (command.needsAck()) {
            // We are expecting a response for this command. Move it into the other queue.
            final LanCommand finalCommand = command;
            synchronized (_commandsPendingResponses) {
                _commandsPendingResponses.add(command);
            }

            int timeout = command.getRequestTimeout();
            if (command instanceof CreateDatapointCommand) {
                CreateDatapointCommand c = (CreateDatapointCommand)command;
                timeout = c.getAckTimeout();
            }

            // Set the timeout timer
            _ackTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // See if we are still in the queue
                    LanCommand foundCommand = null;
                    synchronized (_commandsPendingResponses) {
                        for (LanCommand lanCommand : _commandsPendingResponses) {
                            if (lanCommand == finalCommand) {
                                foundCommand = lanCommand;
                                break;
                            }
                        }

                        if (foundCommand != null) {
                            // Remove it from our list of pending commands
                            _commandsPendingResponses.remove(foundCommand);
                        }
                    }

                    if (foundCommand instanceof CreateDatapointCommand) {
                        CreateDatapointCommand cdc = (CreateDatapointCommand)
                                foundCommand;
                        if (cdc.getErrorListener() != null) {
                            cdc.getErrorListener().onErrorResponse(new TimeoutError
                                    ("Timed out waiting for datapoint ack"));
                        }
                    }
                }
            }, timeout * 1000);
        }

        // Since we're responding to the module, we can reset our keepalive timer now.
        startKeepalive();
        return NanoHTTPD.newFixedLengthResponse(getResponseCode(), AylaHttpServer.MIME_JSON,
                encryptedResponse);
    }

    /**
     * Processes a request from a device in LAN mode to update one of our properties. This
     * request can come in as a result of a new datapoint being set on a property, whether we set
     * the datapoint ourselves, or if it was set externally.
     * <p>
     * This request may come as a response to a command we sent previously. If so, this method
     * will match up the request with the appropriate command, save the decrypted data from this
     * request as the command's response and remove the command from the pending queue.
     *
     * @param uriResource Resource of the request, not used here
     * @param urlParams   Parsed router parameters, not used here.
     * @param session     Session for the request, contains request details
     * @return a Response object for this request to be returned to the device
     */
    @SuppressWarnings("UnusedParameters")
    public NanoHTTPD.Response handlePropertyUpdateRequest(RouterNanoHTTPD.UriResource uriResource,
                                                          Map<String, String> urlParams,
                                                          NanoHTTPD.IHTTPSession session) {
        LanCommand command = getCommand(session);

        // Get the LAN message from the session body
        AylaLanMessage lanMessage;
        try {
            lanMessage = AylaLanMessage.fromSession(session, _deviceRef.get());
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getParseFailedError(command, aylaError);
        }

        AylaLanMessage.Payload payload;
        try {
            payload = lanMessage.getPayload(_encryption);
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getDecryptionFailedError(command, aylaError);
        }

        AylaDevice device = _deviceRef.get();
        if (device == null) {
            return getDeviceError(command);
        }

        // This is a property update message. The message JSON looks like this:
        // {"seq_no":0,"data":{"name":"Blue_LED","value":0, "metadata":"key1":"value1","key2":
        // "value2"}}

        //  Extract the property name, value and metadata from the JSON
        String propertyName;
        Object propertyValue;
        Map<String, String> metadata = null;
        String dsn;
        int devTimeMs;

        try {
            JSONObject dataObject = new JSONObject(payload.data);
            propertyName = dataObject.getString("name");
            propertyValue = dataObject.get("value");
            try{
                String metadataJson = dataObject.get("metadata").toString();
                metadata = new HashMap<>();
                metadata = AylaNetworks.sharedInstance().getGson().fromJson(metadataJson,
                        metadata.getClass());
            } catch (JSONException e){
                AylaLog.d(LOG_TAG, "No metadata associated with this datapoint");
            }
            dsn = dataObject.optString("dsn", null);
            //noinspection UnusedAssignment
            devTimeMs = dataObject.optInt("dev_time_ms", 0);
        } catch (JSONException e) {
            e.printStackTrace();
            if (command != null) {
                command.setErrorResponse(new JsonError(payload.data, "Failed parsing command JSON",
                        e));
            }
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Bad message JSON");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        // Is this update for a different device (e.g. one of our nodes if we're a gateway)?
        if (dsn != null) {
            // This is a node update. We need to update the property value on the node.
            AylaHttpServer server = _httpServerRef.get();
            if (server != null) {
                device = server.deviceWithDsn(dsn);
                if (device == null) {
                    String jsonErrorBody = AylaLanModule.getJSONErrorBody("No device with dsn "
                            + dsn);
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                            AylaHttpServer.MIME_JSON, jsonErrorBody);
                }
            } else {
                String jsonErrorBody = AylaLanModule.getJSONErrorBody("No webserver available");
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        AylaHttpServer.MIME_JSON, jsonErrorBody);
            }
        }

        // Find our property that matches the extracted name
        AylaProperty deviceProperty = device.getProperty(propertyName);
        if (deviceProperty == null) {
            if (command != null) {
                command.setErrorResponse(new PreconditionError("Property " + propertyName + " not" +
                        " found"));
            }
            AylaLog.e(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Could not find property named" +
                    " " + propertyName);

            // We need to return success from this, or else the module will not pick up any
            // commands we might have waiting in the queue. Another option would be to send an
            // additional notify request to the module, but this is less risky.
            return NanoHTTPD.newFixedLengthResponse(getResponseCode(),
                    AylaHttpServer.MIME_JSON, "");
        }

        // Update the value to the new value, and notify listeners that something changed
        deviceProperty.updateFrom(propertyValue, metadata, AylaDevice.DataSource.LAN);

        if (command != null) {
            // We've matched up the command and its response. Set the response on the command
            // which will notify waiters that we have a response
            AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Setting command module " +
                    "response");
            command.setModuleResponse(payload.data);
        }

        // All good. Return an OK status to the device to let it know we accepted the update.
        return NanoHTTPD.newFixedLengthResponse(getResponseCode(), AylaHttpServer.MIME_JSON, "");
    }


    /**
     * Processes data from a device in LAN mode to update setup device details. This
     * request comes in as a result of fetchStatus() command sent to the device from the mobile.
     * <p>
     * This request may come as a response to a command we sent previously. If so, this method
     * will match up the request with the appropriate command, save the decrypted data from this
     * request as the command's response and remove the command from the pending queue.
     *
     * @param uriResource Resource of the request, not used here
     * @param urlParams   Parsed router parameters, not used here.
     * @param session     Session for the request, contains request details
     * @return a Response object for this request to be returned to the device
     */

    public NanoHTTPD.Response handleGetStatusRequest(RouterNanoHTTPD.UriResource uriResource,
                                                          Map<String, String> urlParams,
                                                          NanoHTTPD.IHTTPSession session) {

        AylaSetupDevice device = (AylaSetupDevice) _deviceRef.get();

        LanCommand command = getCommand(session);
        if (device == null) {
          return getDeviceError(command);
        }



        // Get the LAN message from the session body
        AylaLanMessage lanMessage;
        try {
            lanMessage = AylaLanMessage.fromSession(session, _deviceRef.get());
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getParseFailedError(command, aylaError);
        }

        AylaLanMessage.Payload payload;
        try {
            payload = lanMessage.getPayload(_encryption);
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getDecryptionFailedError(command, aylaError);
        }

        AylaLog.d(LOG_TAG, "status.json payload = "+payload.data);
        AylaSetupDevice setupDevice = AylaNetworks.sharedInstance().getGson().fromJson
                (payload.data, AylaSetupDevice.class);
        if (command != null) {
            // We've matched up the command and its response. Set the response on the command
            // which will notify waiters that we have a response
            command.setModuleResponse(payload.data);
        }
        device.updateFrom(setupDevice, AylaDevice.DataSource.LAN);

        // All good. Return an OK status to the device to let it know we accepted the update.
        return NanoHTTPD.newFixedLengthResponse(getResponseCode(), AylaHttpServer.MIME_JSON, "");
    }

    /**
     * This request is a response to a command we sent previously to the device.
     * This method will match up the request with the appropriate command and remove the command
     * from the pending queue.
     *
     * @param uriResource Resource of the request, not used here
     * @param urlParams   Parsed router parameters, not used here.
     * @param session     Session for the request, contains request details
     * @return a Response object for this request to be returned to the device
     */
    public NanoHTTPD.Response handleModuleRequest(RouterNanoHTTPD.UriResource uriResource,
                                                  Map<String, String> urlParams,
                                                  NanoHTTPD.IHTTPSession session) {

        AylaSetupDevice device = (AylaSetupDevice) _deviceRef.get();

        LanCommand command = getCommand(session);
        if (device == null) {
         return getDeviceError(command);
        }

        // Get the LAN message from the session body
        AylaLanMessage lanMessage;
        try {
            lanMessage = AylaLanMessage.fromSession(session, _deviceRef.get());
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getParseFailedError(command, aylaError);
        }

        AylaLanMessage.Payload payload;
        try {
            payload = lanMessage.getPayload(_encryption);
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getDecryptionFailedError(command, aylaError);
        }


        AylaLog.d(LOG_TAG, "handleModuleRequest = "+payload.data);
        if (command != null) {
            // We've matched up the command and its response. Set the response on the command
            // which will notify waiters that we have a response
            AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Setting command module " +
                    "response");
            command.setModuleResponse(payload.data);

        }

        // All good. Return an OK status to the device to let it know we accepted the update.
        return NanoHTTPD.newFixedLengthResponse(getResponseCode(), AylaHttpServer.MIME_JSON, "");
    }

    /**
     * Called by the webserver in response to datapoint/ack.json request
     * @param uriResource UriResource for this request. We can use this to get the setup device.
     * @param urlParams   URL parameters, unused
     * @param session     IHTTPSession, provides query string, etc.
     * @return the HTTP response to this request
     */
    public NanoHTTPD.Response handleDatapointAck(RouterNanoHTTPD.UriResource uriResource,
                                                 Map<String, String> urlParams,
                                                 final NanoHTTPD.IHTTPSession session) {
        // Get the LAN message from the session body
        AylaLanMessage lanMessage;
        try {
            lanMessage = AylaLanMessage.fromSession(session, _deviceRef.get());
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getParseFailedError(null, aylaError);
        }

        AylaLanMessage.Payload payload;
        try {
            payload = lanMessage.getPayload(_encryption);
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            return getDecryptionFailedError(null, aylaError);
        }

        final CreateDatapointCommand.CreateDatapointAck ack = AylaNetworks.sharedInstance()
                .getGson().fromJson(payload.data, CreateDatapointCommand.CreateDatapointAck.class);

        AylaLog.d(LOG_TAG, "Received datapoint ack: " + ack);

        // Find the matching command in our queue
        CreateDatapointCommand command = null;
        synchronized (_commandsPendingResponses) {
            for (LanCommand cmd : _commandsPendingResponses) {
                if (cmd instanceof CreateDatapointCommand) {
                    CreateDatapointCommand found = (CreateDatapointCommand) cmd;
                    if (TextUtils.equals(found.getId(), ack.id)) {
                        command = found;
                        _commandsPendingResponses.remove(command);
                        break;
                    }
                }
            }
        }

        if (command == null) {
            // We don't have a matching datapoint waiting for this ack
            AylaLog.w(LOG_TAG, "No matching datapoint found for ack");

            // Send an error to device listeners so they know the property is stale
            _deviceRef.get().notifyError(new PreconditionError("Received ack for this device " +
                    "without a matching command"));
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("No matching ID found");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        AylaLog.i(LOG_TAG, "Found command matching datapoint ack");
        final CreateDatapointCommand finalCommand = command;
        if (ack.ack_status == NanoHTTPD.Response.Status.OK.getRequestStatus()) {
            // We have the ack, and can call the listener's success handler

            final AylaDatapoint dp = new AylaDatapoint(command);
            // Get the device from the DSN contained in the command.
            // This may or may not be the same device that sent the command. Gateways will
            // handle LAN commands for nodes, in which case the node will be a different
            // device than the gateway that handled this command.
            AylaDevice device = _deviceRef.get().getDeviceManager().deviceWithDSN(command.getDsn());
            AylaProperty commandProperty = device.getProperty(command.getName());

            if (commandProperty != null) {
                commandProperty.updateFrom(dp, AylaDevice.DataSource.LAN);
            }

            // Listeners always need to be called on the main thread
            if (command.getSuccessListener() != null) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        finalCommand.getSuccessListener().onResponse(dp);
                    }
                });
            }
        } else {
            // We got the ack, but it reported failure
            if (command.getErrorListener() != null) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        finalCommand.getErrorListener().onErrorResponse(
                                new ServerError(ack.ack_status, null, "Datapoint NAK", null));
                    }
                });
            }
        }

        return NanoHTTPD.newFixedLengthResponse(getResponseCode(),
                AylaHttpServer.MIME_JSON, "");
    }

    /**
     * Handles the response to the request to join the setup device to an AP
     *
     * @param uriResource UriResource for this request. We can use this to get the setup device.
     * @param urlParams   URL parameters, unused
     * @param session     IHTTPSession, provides query string, etc.
     * @return the response for this request
     */
    public NanoHTTPD.Response
    handleSetupConnectStatus(RouterNanoHTTPD.UriResource uriResource,
                             Map<String, String> urlParams,
                             NanoHTTPD.IHTTPSession session) {

        LanCommand command = getCommand(session);

        // Get the LAN message from the session body
        AylaLanMessage lanMessage;
        AylaLanMessage.Payload messagePayload = null;
        try {
            lanMessage = AylaLanMessage.fromSession(session, _deviceRef.get());
            if (lanMessage != null) {
                messagePayload = lanMessage.getPayload(_encryption);
            }
            AylaLog.d(LOG_TAG, "Setup connection response payload: " + messagePayload);
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            if (command != null) {
                command.setErrorResponse(aylaError);
            }
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Decryption failed");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        // We don't get data back, but we do get an empty response from the module.
        if (command != null) {
            command.setModuleResponse("");
        }

        return NanoHTTPD.newFixedLengthResponse(getResponseCode(), AylaHttpServer.MIME_JSON,
                "{}");
    }

    public NanoHTTPD.Response
    handleConnectionStatusUpdateRequest(RouterNanoHTTPD.UriResource uriResource,
                                        Map<String, String> urlParams,
                                        NanoHTTPD.IHTTPSession session) {
        LanCommand command = getCommand(session);
        // Get the LAN message from the session body
        AylaLanMessage lanMessage;
        AylaLanMessage.Payload messagePayload;
        try {
            lanMessage = AylaLanMessage.fromSession(session, _deviceRef.get());
            messagePayload = lanMessage.getPayload(_encryption);
        } catch (AylaError aylaError) {
            aylaError.printStackTrace();
            if (command != null) {
                command.setErrorResponse(aylaError);
            }
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Decryption failed");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Decrypted connection status " +
                "message: " + messagePayload.data);
        AylaDeviceNode.NodeConnectionStatus.Wrapper wrapper = AylaNetworks.sharedInstance()
                .getGson().fromJson(messagePayload.data,
                        AylaDeviceNode.NodeConnectionStatus.Wrapper.class);
        if (wrapper == null) {
            AylaLog.e(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Unable to parse JSON: " +
                    messagePayload.data);
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Unable to parse request JSON");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        AylaHttpServer server = _httpServerRef.get();
        if (server == null) {
            AylaLog.e(LOG_TAG, "No LAN mode server available");
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("No webserver available");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        for (AylaDeviceNode.NodeConnectionStatus status : wrapper.connection) {
            AylaDevice device = server.deviceWithDsn(status.dsn);
            if (device instanceof AylaDeviceNode) {
                AylaDeviceNode node = (AylaDeviceNode) device;
                node.updateConnectionStatus(status.status, AylaDevice.DataSource.LAN);
            }
            AylaLog.d(LOG_TAG, "Status of " + status.dsn + " is " + status.status);
        }

        return NanoHTTPD.newFixedLengthResponse("");
    }

    /**
     * Sends a Request to the LAN request queue. All LAN mode requests should be sent via this
     * method.
     *
     * @param request Request to send
     */
    public void sendRequest(AylaAPIRequest request) {
        request.setShouldCache(false);
        request.logResponse();
        _lanRequestQueue.add(request);
    }

    /**
     * Returns either PARTIAL_CONTENT or OK, depending on the size of our queue of pending LAN
     * commands. The device will make a new request for commands if we return PARTIAL_CONTENT as
     * the status code.
     *
     * @return OK OR PARTIAL_CONTENT IStatus object, depending on the command queue size
     */
    private NanoHTTPD.Response.IStatus getResponseCode() {
        return _pendingLanCommands.size() > 0 ? NanoHTTPD.Response.Status.PARTIAL_CONTENT :
                NanoHTTPD.Response.Status.OK;
    }

    private NanoHTTPD.Response processLanModeKeyExchange(KeyExchange keyExchange) {
        long start = System.nanoTime();

        boolean wasActive = _isActive;
        _isActive = false;

        // Make sure we're speaking the same version
        if (keyExchange.proto != CRYPTO_PROTO_CBC_AES256 || keyExchange.ver != CRYPTO_MESSAGE_VER) {
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Unsupported crypto version");
            return NanoHTTPD.newFixedLengthResponse(AylaHttpServer.Status.UPGRADE_REQUIRED,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        // Make sure we have a device to work with
        final AylaDevice device = _deviceRef.get();
        if (device == null) {
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("No device associated with LAN module");
            return NanoHTTPD.newFixedLengthResponse(AylaHttpServer.Status.PRECONDITION_FAILED,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        // Make sure the device has a LanConfig
        AylaLanConfig lanConfig = device.getLanConfig();
        if (lanConfig == null || lanConfig.lanipKeyId == null) {
            if (wasActive) {
                _lastError = new InternalError("Device has no LAN key",
                        new Exception(lanConfig == null ? "lanConfig is null" : "lanipKeyId is null"));
                device.notifyLanStateChange(false, _lastError);
            }
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Device has no LAN key");
            return NanoHTTPD.newFixedLengthResponse(AylaHttpServer.Status.PRECONDITION_FAILED,
                    AylaHttpServer.MIME_JSON,jsonErrorBody);
        }

        // Make sure our keys match
        int keyId = lanConfig.lanipKeyId.intValue();
        if (keyExchange.key_id != keyId) {
            if (wasActive) {
                _lastError = new InternalError("Lan keys do not match");
                device.notifyLanStateChange(false, _lastError);
            }
            // If keys do not match, LAN IP KEY on the device might have changed. Refresh our key.
            device.disableLANUntilNetworkChanges(true);
            device.refreshLanConfig();

            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Keys do not match");
            return NanoHTTPD.newFixedLengthResponse(AylaHttpServer.Status.PRECONDITION_FAILED,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        // Copy the module's values to our structure
        _encryption.version = keyExchange.ver;
        _encryption.proto_1 = keyExchange.proto;
        _encryption.key_id_1 = keyExchange.key_id;
        _encryption.sRnd_1 = keyExchange.random_1;
        _encryption.nTime_1 = keyExchange.time_1;

        // Generate our crypto values
        _encryption.sRnd_2 = AylaEncryption.randomToken(16);
        _encryption.nTime_2 = System.nanoTime();

        AylaLog.d(LOG_TAG, "Pre-generate keys: " + (System.nanoTime() - start));

        // Generate seed values and new session keys
        AylaError error = _encryption.generateSessionKeys(null, null);
        if (error != null) {
            if (wasActive) {
                _lastError = new InternalError("Error generating session keys", error);
                device.notifyLanStateChange(false, _lastError);
            }
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Could not generate session keys");
            return NanoHTTPD.newFixedLengthResponse(AylaHttpServer.Status.CERT_ERROR,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        // Create our response JSON
        KeyResponse keyResponse = new KeyResponse();
        keyResponse.random_2 = _encryption.sRnd_2;
        keyResponse.time_2 = _encryption.nTime_2.longValue();

        String responseJson = AylaNetworks.sharedInstance().getGson().toJson(keyResponse,
                KeyResponse.class);

        AylaLog.d(LOG_TAG, "Response JSON: " + responseJson);

        if (!isActive()) {
            _isActive = true;
            device.stopPolling();
            startKeepalive();
            device.notifyLanStateChange(true);
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                AylaHttpServer.MIME_JSON, responseJson);
    }

    private void startKeepalive() {
        stopKeepalive();
        AylaDevice device = _deviceRef.get();
        if(device != null){
            AylaDeviceManager dm = device.getDeviceManager();
            if(dm != null && dm.getState() == AylaDeviceManager.DeviceManagerState.Paused) {
                AylaLog.w(LOG_TAG, "Not starting device keepalive for " + device.getDsn() +
                        " as device manager is not there or is paused (dm == " + dm + ")");
                return;
            }
        }
        _keepAliveHandler.postDelayed(_keepAliveRunnable, getKeepAliveInterval());
    }

    private void stopKeepalive() {
        _keepAliveHandler.removeCallbacksAndMessages(null);
    }

    private void startMDNSQuery() {
        stopMDNSQuery();
        _mdnsQueryHandler.postDelayed(_mdnsQueryRunnable, getMdnsQueryInterval());
    }

    private void stopMDNSQuery() {
        _mdnsQueryHandler.removeCallbacksAndMessages(null);
    }

    private NanoHTTPD.Response processSecureSetupKeyExchange(KeyExchange keyExchange) {
        AylaSetupDevice setupDevice = _httpServerRef.get().getSetupDevice();
        if (setupDevice == null) {
            AylaLog.e(LOG_TAG, "No setup device found for secure setup key exchange");
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("No device found");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        _encryption.version = keyExchange.ver;
        _encryption.proto_1 = keyExchange.proto;
        _encryption.sRnd_1 = keyExchange.random_1;
        _encryption.nTime_1 = keyExchange.time_1;

        _encryption.sRnd_2 = ObjectUtils.generateRandomToken(16);
        _encryption.nTime_2 = System.nanoTime();

        byte[] encBytes = Base64.decode(keyExchange.sec.getBytes(), Base64.NO_WRAP);
        byte[] secData = setupDevice.getLanConfig().getSetupCrypto().decrypt(encBytes);
        if (secData == null) {
            AylaLog.e(LOG_TAG, "Unable to decrypt key exchange data");
            String jsonErrorBody = AylaLanModule.getJSONErrorBody("Decryption failure");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED,
                    AylaHttpServer.MIME_JSON, jsonErrorBody);
        }

        _encryption.generateSessionKeys(AylaEncryption.TYPE_SETUP_RSA, secData);

        KeyResponse keyResponse = new KeyResponse();
        keyResponse.random_2 = _encryption.sRnd_2;
        keyResponse.time_2 = _encryption.nTime_2.longValue();

        String responseJson = AylaNetworks.sharedInstance().getGson().toJson(keyResponse,
                KeyResponse.class);

        AylaLog.d(LOG_TAG, "Response JSON: " + responseJson);

        if (!isActive()) {
            _isActive = true;
            startKeepalive();
            setupDevice.notifyLanStateChange(true);
        }

        return NanoHTTPD.newFixedLengthResponse(getResponseCode(),
                AylaHttpServer.MIME_JSON, responseJson);
    }

    /**
     * Iniitiates or continues a LAN session with the device.
     */
    void sendLocalRegistration() {
        if (_processingCommandBlock) {
            // Our send queue is busy processing command blocks. We don't need to send local
            // registration here.
            return;
        }

        final AylaDevice device = _deviceRef.get();
        if (device == null) {
            handleKeyExchangeError(new AylaError(AylaError.ErrorType.InvalidArgument, "No device " +
                    "in LAN module!"));
            return;
        }

        // Set up the local_reg structure to pass to the module in our POST request
        String url = lanURL("local_reg.json");
        LocalReg localReg = new LocalReg();
        AylaHttpServer server = _httpServerRef.get();
        if (server == null) {
            handleKeyExchangeError(new PreconditionError("HTTP server is not running"));
            return;
        }

        Context context = AylaNetworks.sharedInstance().getContext();
        localReg.ip = NetworkUtils.getWifiIpAddress(context);
        localReg.port = server.getListeningPort();
        localReg.uri = LOCAL_LAN_URI;
        localReg.notify = _pendingLanCommands.size() > 0 ? 1 : 0;

        // If this is a setup device, we should set the key field if it is not set.
        if (device instanceof AylaSetupDevice) {
            AylaSetupDevice setupDevice = (AylaSetupDevice) device;
            byte[] key;
            try {
                key = setupDevice.getLanConfig().getPublicKey();
            } catch (IOException e) {
                AylaLog.e(LOG_TAG, "Could not get public key from setup device:");
                e.printStackTrace();
                stop();
                return;
            }

            AylaLog.i(LOG_TAG, "Using public key for LAN session");
            localReg.key = Base64.encodeToString(key, Base64.NO_WRAP);
        }

        AylaLog.v(LOG_TAG, device + " sending local_reg. Notify: " + localReg.notify);

        // Wrap it up and turn it into a string
        LocalReg.Wrapper localRegWrapper = new LocalReg.Wrapper();
        localRegWrapper.local_reg = localReg;
        final String postBodyString = AylaNetworks.sharedInstance().getGson().toJson
                (localRegWrapper, LocalReg.Wrapper.class);

        final boolean newSession = !_isActive;

        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request =
                new AylaJsonRequest<AylaAPIRequest.EmptyResponse>(
                        newSession ? Request.Method.POST : Request.Method.PUT,
                        url,
                        postBodyString,
                        null,
                        AylaAPIRequest.EmptyResponse.class,
                        null,
                        new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                            @Override
                            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                                // Do nothing.
                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError error) {
                                AylaLog.i(LOG_TAG, "[" + device.getDsn() + "] " +
                                        "+ " + (newSession ? "POST" : "PUT") +  "local_reg: " + error);
                                handleKeyExchangeError(error);
                            }
                        }) {
                    @Override
                    protected Response<EmptyResponse> parseNetworkResponse(NetworkResponse response) {
                        return Response.success(new EmptyResponse(),
                                HttpHeaderParser.parseCacheHeaders(response));
                    }
                };
        AylaLog.d(LOG_TAG, device.getDsn() + (newSession? "POST local_reg": "PUT local_reg") );
        sendRequest(request);
    }

    /**
     * Deletes a LAN session
     *
     */
    public void deleteLANSession(){
        AylaDevice device = _deviceRef.get();
        if(device == null){
            return;
        }
        final DeleteSessionCommand cmd = new DeleteSessionCommand();
        AylaLanRequest request = new AylaLanRequest(device, cmd, device.getSessionManager(),
                new Response.Listener<AylaLanRequest.LanResponse>() {
                    @Override
                    public void onResponse(AylaLanRequest.LanResponse response) {
                        AylaError error = cmd.getResponseError();
                        if (error != null) {
                            AylaLog.d(LOG_TAG, "delete session command returned error "+
                                    error.getMessage());
                            return;
                        }
                        AylaLog.d(LOG_TAG, "Lan session deleted");
                        stop();
                    }
                }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.e(LOG_TAG, "Error in delete session "+error.getMessage());
                stop();
            }
        });


        sendRequest(request);
    }

    private String lanURL(String path) {
        String deviceIP = _deviceRef.get().getLanIp();
        if (deviceIP == null) {
            return null;
        }
        return "http://" + deviceIP + "/" + path;
    }

    private void handleKeyExchangeError(AylaError error) {
        AylaDevice d = _deviceRef.get();
        if (d == null) {
            return;
        }

        if (_isActive) {
            AylaLog.e(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] Key exchange failure: " + error);
            _isActive = false;
            _lastError = new AuthError("Key exchange failure", error);
            d.notifyLanStateChange(false, _lastError);
        } // We don't want to call the error listeners if this fails when we're not active.
          // This is the expected result for devices that are not on the same local network.

        // If device is not reachable in offline mode, its LAN IP address might have changed.
        // In this case, use Multicast DNS to find the changed LAN IP address.
        AylaDeviceManager dm = d.getDeviceManager();
        if(dm != null){
            if(dm.getState() == AylaDeviceManager.DeviceManagerState.Paused){
                return;
            }
        }
        if (d.getSessionManager() != null && d.getSessionManager().isCachedSession()) {
            if(error.getClass().equals(NetworkError.class) ||
                    error.getClass().equals(TimeoutError.class)){
                if(_netThread == null){
                    _netThread = new NetThread(_mdnsListener, d.getDsn()+".local");
                    _netThread.start();
                    stopKeepalive();
                }
            }
        }
    }

    public void registerCommands(List<LanCommand> lanCommands) {
        synchronized (_pendingLanCommands) {
            _pendingLanCommands.addAll(lanCommands);
        }
    }

    public void unregisterCommands(List<LanCommand> lanCommands) {
        synchronized (_pendingLanCommands) {
            _pendingLanCommands.removeAll(lanCommands);
        }
    }

    /**
     * Returns an AylaLanCommand with the matching command ID, or null if not found in the queue
     *
     * @param commandId Command ID to match
     * @return The AylaLanCommand with the matching ID, or null if not found
     */
    private AylaLanCommand getQueuedCommand(int commandId) {
        synchronized (_pendingLanCommands) {
            Iterator<LanCommand> iter =
                    _pendingLanCommands.descendingIterator();
            while (iter.hasNext()) {
                LanCommand queuedCommand = iter.next();
                if (queuedCommand instanceof AylaLanCommand) {
                    AylaLanCommand alc = (AylaLanCommand) queuedCommand;
                    if (alc.getCommandId() == commandId) {
                        return alc;
                    }
                }
            }
        }
        return null;
    }

    public void setProcessingCommandBlock(boolean processing) {
        _processingCommandBlock = processing;
    }

    public static class LocalReg {
        @Expose
        String ip;
        @Expose
        int port;
        @Expose
        String uri;
        @Expose
        int notify;
        @Expose
        String key;

        public static class Wrapper {
            @Expose
            public LocalReg local_reg;
        }
    }


    /**
     * Structure for key exchange messages
     */
    public static class KeyExchange {
        @Expose
        public int ver;
        @Expose
        public String random_1;
        @Expose
        public long time_1;
        @Expose
        public int proto;
        @Expose
        public int key_id;
        @Expose
        public String sec;
    }

    /**
     * Wrapper class for KeyExchange messages received from the module
     */
    public static class KeyExchangeWrapper {
        @Expose
        public KeyExchange keyExchange;
    }

    /**
     * Response to a key exchange request. Sent from the mobile device to the module.
     */
    public static class KeyResponse {
        @Expose
        public String random_2;
        @Expose
        public long time_2;
    }

    public static class Base64CryptoContainer {
        @Expose
        String enc;
        @Expose
        String sign;
    }

    public interface MDNSListener{

        /**
         * Called when the multicast socket is initialized and ready to send out MDNS queries.
         */
        public void ready();

        /**
         * Called when device to be connected in LAN mode is found using MDNS.
         * @param deviceIpAddress LAN IP address of the device.
         */
        public void success(String deviceIpAddress);

        /**
         * Called when attempt to find LAN IP address of a device through MDNS fails.
         */
        public void failed(AylaError error);
    }

    public static String getJSONErrorBody(String errText) {
        final JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("error", errText);
            return bodyObject.toString();
        } catch (JSONException e) {
            AylaLog.e(LOG_TAG, "JSON Exception while creating error message " + e.toString());
        }
        return null;
    }

    private NanoHTTPD.Response getDeviceError(LanCommand command){
        if (command != null) {
            command.setErrorResponse(new PreconditionError("No device found for LAN command"));
        }
        String jsonErrorBody = AylaLanModule.getJSONErrorBody("Unable to find device for this request");
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                AylaHttpServer.MIME_JSON, jsonErrorBody);
    }

    private NanoHTTPD.Response getParseFailedError(LanCommand command, AylaError aylaError) {
        if (command != null) {
            command.setErrorResponse(aylaError);
        }
        AylaLog.e(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] LAN message parsing failed");
        String jsonErrorBody = AylaLanModule.getJSONErrorBody("Message parsing failed");
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED,
                AylaHttpServer.MIME_JSON, jsonErrorBody);
    }

    private NanoHTTPD.Response getDecryptionFailedError(LanCommand command, AylaError aylaError){
        if (command != null) {
            command.setErrorResponse(aylaError);
        }
        AylaLog.e(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] LAN payload decryption failed");
        String jsonErrorBody = AylaLanModule.getJSONErrorBody("Decryption failed");
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED,
                AylaHttpServer.MIME_JSON, jsonErrorBody);
    }

    /**
     * Returns the Lan command which matches the command id in this session.
     * @param session IHTTPSession, provides query string, etc.
     * @return  Lan command which matches the command id in this session.
     */
    private LanCommand getCommand( NanoHTTPD.IHTTPSession session){
        LanCommand command = null;
        String commandIdString = session.getParms().get("cmd_id");
        AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] getCommand(): " +
                "cmd_id=" + commandIdString);
        if (commandIdString != null) {
            int commandId = Integer.parseInt(commandIdString);
            // Look for the matching command in our queue
            command = getQueuedCommand(commandId);
        }

        if (command != null) {
            synchronized (_pendingLanCommands) {
                _pendingLanCommands.remove(command);
                AylaLog.d(LOG_TAG, "Pending LAN commands now "+_pendingLanCommands.size());
            }
        } else {
            AylaLog.d(LOG_TAG, "[" + _deviceRef.get().getDsn() + "] No matching command found in " +
                    "the queue");
        }
        return command;
    }

    /**
     * Returns the last error which prevents device from getting into LAN mode.
     * <p>Possible errors are:</p>
     * <ul>
     *     <li>TimeoutError
     *          <ul>
     *              <li>time-out while setting up lan session.</li>
     *          </ul>
     *     </li>
     *
     *     <li>PreconditionError
     *          <ul>
     *              <li>wifi is not enabled, from mDNS</li>
     *          </ul>
     *     </li>
     *
     *     <li>NetworkError from mDNS operations.
     *          <ul>
     *              <li>IOException Cannot open socket</li>
     *              <li>IOException in receive response</li>
     *              <li>IOException during socket reopen</li>
     *              <li>IOException during query command</li>
     *          </ul>
     *     </li>
     *
     *     <li>InternalError
     *         <ul>
     *             <li>Device has no LAN key</li>
     *             <li>Lan keys do not match</li>
     *             <li>Error generating session keys</li>
     *         </ul>
     *     </li>
     *
     *     <li>AuthError
     *         <ul>
     *             <li>Key exchange failure</li>
     *         </ul>
     *     </li>
     * </ul>
     */
    public AylaError getLastError() {
        return _lastError;
    }

}
