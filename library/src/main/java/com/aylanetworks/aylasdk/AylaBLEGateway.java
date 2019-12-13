package com.aylanetworks.aylasdk;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.PropertyChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.error.TimeoutError;
import com.aylanetworks.aylasdk.setup.AylaRegistration;
import com.aylanetworks.aylasdk.setup.AylaRegistrationCandidate;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BLE gateway device, responsible for node device discovery, registration, as well as
 * disconnecting node from gateway.
 * <p>
 * Copyright 2018 Ayla Networks, all rights reserved
 * </p>
 */
public class AylaBLEGateway extends AylaDeviceGateway {

    private static final String LOG_TAG = "BLEGateway";

    /**
     * Property to start or stop scanning. writing value greater than 0 to start scanning,
     * and writing value 0 to stop scanning. Value greater than 0 also implies a scanning
     * timeout, gateway will automatically stop scanning after that period of time if it was
     * not explicitly asked stop scanning. Note that, client should write a reasonable timeout
     * value to make sure both scanning and pairing can be done within that period of time.
     * Stop scanning in advance would result in pairing failure even though scan results
     * have been fetched, as it will remove scanned node from gateway BLE protocol stack.
     */
    public static final String PROP_ENABLE_SCAN = "bt_scan_enable";

    /**
     * Property for scan results in JSON format, which is defined in {@link ScannedNode}.
     */
    public static final String PROP_SCAN_RESULTS = "bt_scan_results";

    /**
     * Property to enable connecting a node device to the gateway, by writing the MAC
     * address of the BLE device to start the pairing process.
     */
    public static final String PROP_CONNECT_ID = "bt_connect_id";

    /**
     * Property to indicate the status of the node being registered. Before a node device is ready
     * to be registered, it has to be first connected to the gateway, then added to the cloud to
     * become a cloud candidate, and lastly finishes template association.
     * Check {@link #isNodeDeviceReady(String)} for more details.
     * Here is an example of the JSON-formatted connect result:
     * <pre>
     * {
     *    "bd_addr": "CF:66:24:27:E4:B8", // Mac address of the BLE device node.
     *    "status_code":0,                // connect result: 0(connected).
     *    "status_detail":"connected"     // descriptive info of the status code.
     * }
     * </pre>
     */
    public static final String PROP_CONNECT_RESULT = "bt_connect_result";

    /**
     * Property to enable disconnecting a node device from the gateway, by writing the MAC
     * address of the paired BLE device to disconnect it, so that the device can be discovered again.
     */
    public static final String PROP_DISCONNECT_ID = "bt_disconnect_id";

    /**
     * Property to indicate the disconnecting result between a node device and the gateway.
     * It contains disconnection related details such as which node is disconnecting from the
     * gateway and what the result is. The node is uniquely denoted by its MAC address, and
     * the result can be one of disconnecting, disconnected and error, depending on the ongoing
     * state in the gateway. Here is an example of the JSON-formatted disconnection result:
     * <pre>
     * {
     *    "bd_addr": "CF:66:24:27:E4:B8", // Mac address of the BLE device node.
     *    "status_code":0,                // disconnect result: 0(disconnected).
     *    "status_detail":"disconnected"  // descriptive info of the status code.
     * }
     * </pre>
     */
    public static final String PROP_DISCONNECT_RESULT = "bt_disconnect_result";
    public static final int DISCONNECT_RESULT_ERROR_UNKNOWN = -2;
    public static final int DISCONNECT_RESULT_ERROR_NO_DEVICE_FOUND = -1;
    public static final int DISCONNECT_RESULT_DISCONNECTED = 0;
    public static final int DISCONNECT_RESULT_DISCONNECTING = 1;

    private static final String RESULT_KEY_ADDR = "bd_addr";
    private static final String RESULT_KEY_STATUS_CODE = "status_code";
    private static final String RESULT_KEY_STATUS_DETAIL = "status_detail";

    /**
     * Property to indicate the number of devices under the gateway, consisting of number of
     * devices that are registered, aka nodes, and number of devices that are can be registered,
     * aka registration candidates.
     */
    public static final String PROP_NUM_NODES = "num_nodes";

    /**
     * Upon received scan request, BLE gateway immediately starts scanning nearby BLE devices
     * and reports scan result once available. However, to avoid reporting scan result too
     * frequently, gateway will make sure the interval between succeeding and preceding report
     * is no less than {@link #BLE_SCAN_RESULT_REPORT_INTERVAL_SECONDS} seconds, until being
     * explicitly asked stop scanning, or the scan timed out automatically. The report interval
     * may be different on different gateway implementation.
     */
    private static final int BLE_SCAN_RESULT_REPORT_INTERVAL_SECONDS = 5;

    /**
     * The default time the mobile is supposed to be waiting for candidates creation and
     * registration, including the time spent on scanning, pairing and registration.
     * Note that, gateway scanning should not be stopped while pairing is still in process,
     * otherwise the scanned node will be removed from BLE protocol stack, resulting in
     * pairing failure. This value is configurable and the longer the better.
     */
    private static final int BLE_NODE_PREPARATION_TIMEOUT_IN_SECONDS = 3 * 60;

    /**
     * The time mobile should be waiting for scanning results.
     */
    private static final int BLE_SCAN_RESULT_TIMEOUT_SECONDS = 2 * BLE_SCAN_RESULT_REPORT_INTERVAL_SECONDS;

    /**
     * Return an empty JSON array from gateway if no eligible BLE device was discovered.
     */
    private static final String EMPTY_SCAN_RESULTS = "[]";

    /**
     * The connect result of the device being registered. Check definition of the property
     * {@link #PROP_CONNECT_RESULT} for more details.
     */
    public static class ConnectResult {

        /**
         * the device can not be connected to gateway for some unknown reasons.
         */
        public static final int ERROR_UNKNOWN = -2;

        /**
         * the device can not be found.
         */
        public static final int ERROR_NO_DEVICE_FOUND = -1;

        /**
         * the devices has connected to the gateway.
         */
        public static final int CONNECTED = 0;

        /**
         * the devices has been added to the cloud and becomes a cloud candidate.
         */
        public static final int ADDED_TO_CLOUD = 1;

        /**
         * the properties of the device are ready on the gateway side, and the gateway
         * has finished requesting cloud to do template association. This state is the
         * most time consuming, compared with the others. The device is supposed to be ready
         * to register when it gets in this state.
         */
        public static final int UPDATED_TEMPLATE = 2;

        /**
         * Listener used to report the connect result changes of the device being registered.
         */
        interface ConnectionResultChangedListener {
            /**
             * the connection result has changed.
             * @param deviceAddress the address of the device.
             * @param status the new reported status.
             * @param detail the description of the reported status in detail.
             */
            void onConnectResultChanged(String deviceAddress, int status, String detail);
        }
    }

    /**
     * Discovered device node returned from the gateway.
     */
    public static class ScannedNode {
        @Expose String rssi;
        @Expose String name;
        @Expose String type;
        @Expose String bd_addr;

        public static class Wrapper {
            @Expose ScannedNode device;

            public static ScannedNode[] unwrap(Wrapper[] wrappedResults) {
                int size = 0;
                if (wrappedResults != null) {
                    size = wrappedResults.length;
                }

                ScannedNode[] nodes = new ScannedNode[size];
                for (int i = 0; i < size; i++) {
                    nodes[i] = wrappedResults[i].device;
                }
                return nodes;
            }
        }
    }

    private static final String ERROR_PROP_NOT_FOUND = "property %s not found";

    public AylaBLEGateway() {
        super();
    }

    /**
     * <p>Prepare node candidates to be registered. The BLE gateway needs to scan for devices to set
     * them up as candidates. Here we will initiate the scan for devices and turn the results into
     * an array of AylaRegistrationCandidate to return to the caller.</p>
     *
     * <p>For each candidate the user wishes to register, we will need to create an actual
     * AylaRegistrationCandidate on the cloud and then register it. This will happen at the time
     * of registration, so we don't end up adding candidates we don't want to register.</p>
     *
     * @param includeCloudCandidates include cloud candidates that are not registered but still remain
     *                               connected to this gateway. Setting this parameter to true enable us
     *                               to get back those candidates that were added to the cloud but didn't
     *                               get registered in the end.
     * @param scanTimeoutInSeconds timeout value during which period of time the scan should not be stopped,
     *                             should include the time spent on device paring, otherwise the
     *                             paring may fail if the scan was stopped in advance.
     * @param successListener success listener for the result candidates.
     * @param errorListener error listener should there is any during the node preparation process.
     */
    public AylaAPIRequest<AylaRegistrationCandidate[]> prepareNodeCandidates(
            final boolean includeCloudCandidates,
            final int scanTimeoutInSeconds,
            final Response.Listener<AylaRegistrationCandidate[]> successListener,
            final ErrorListener errorListener) {

        AylaAPIRequest chainedRequest = AylaAPIRequest.dummyRequest(AylaRegistrationCandidate[].class,
                successListener, errorListener);
        List<AylaRegistrationCandidate> resultCandidates = new ArrayList<>();

        if (includeCloudCandidates) {
            AylaAPIRequest cloudRequest = AylaBLEGateway.super.fetchRegistrationCandidates(
                    new Response.Listener<AylaRegistrationCandidate[]>() {
                        @Override
                        public void onResponse(AylaRegistrationCandidate[] cloudCandidates) {
                            if (chainedRequest.isCanceled()) {
                                return;
                            }
                            AylaLog.d(LOG_TAG, "Got " + cloudCandidates.length + " cloud candidates.");
                            resultCandidates.addAll(Arrays.asList(cloudCandidates));
                        }
                    },
                    new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError error) {
                            // Just ignore this as if we have no candidates in the cloud.
                        }
                    });
            chainedRequest.setChainedRequest(cloudRequest);
        }

        // Check property existence.
        if (getProperty(PROP_ENABLE_SCAN) == null) {
            String propertyNotAvailable = String.format(ERROR_PROP_NOT_FOUND, PROP_ENABLE_SCAN);
            AylaLog.e(LOG_TAG, propertyNotAvailable);
            errorListener.onErrorResponse(new PreconditionError(propertyNotAvailable));
            return null;
        }

        // Initiate BLE Gateway scanning for candidates.
        AylaAPIRequest scanRequest = getProperty(PROP_ENABLE_SCAN).createDatapoint(
                scanTimeoutInSeconds, null,
                new Response.Listener<AylaDatapoint<Integer>>() {
                    @Override
                    public void onResponse(AylaDatapoint response) {
                        AylaLog.d(LOG_TAG, "Scan enabled");
                        if (!chainedRequest.isCanceled()) {
                            pollScanResult(chainedRequest, BLE_SCAN_RESULT_TIMEOUT_SECONDS,
                                    new Response.Listener<AylaRegistrationCandidate[]>() {
                                        @Override
                                        public void onResponse(AylaRegistrationCandidate[] scannedCandidates) {
                                            AylaLog.d(LOG_TAG, "Got " + scannedCandidates.length + " scanned candidates.");
                                            // collate cloud candidates and local scan result.
                                            for (AylaRegistrationCandidate candidate : scannedCandidates) {
                                                resultCandidates.add(candidate);
                                            }
                                            successListener.onResponse(resultCandidates.toArray(
                                                    new AylaRegistrationCandidate[resultCandidates.size()]));
                                        }
                                    }, errorListener);
                        }

                    }
                }, errorListener);

        chainedRequest.setChainedRequest(scanRequest);

        return chainedRequest;
    }

    /**
     * Waiting for the gateway scan result in a listening way. Internally, will set a device change
     * listener listening on the change of the scan result property. In this way, we are able to
     * can get the scan result immediately once it's available. However, the drawback of this
     * solution is we may not get the full result than it's supposed to be. For example, suppose
     * we have three BLE devices nearby, but the scan result may only contain two of them,
     * because the gateway will report the scan result in a cascading way at a fixed interval.
     * see {@link #BLE_SCAN_RESULT_REPORT_INTERVAL_SECONDS} for more details.
     * also see {@link #pollScanResult(AylaAPIRequest, int, Response.Listener, ErrorListener)}
     * for the polling way to fetch scan result.
     * @param request chained request (for canceling).
     * @param timeoutInSeconds the maximum allowed time for retrieving the scan result.
     * @param successListener Listener called with successful results.
     * @param errorListener Listener called with error results.
     */
    private void listenScanResult(final AylaAPIRequest request,
                                  final int timeoutInSeconds,
                                  final Response.Listener<AylaRegistrationCandidate[]> successListener,
                                  final ErrorListener errorListener) {

        final Handler handler = new Handler(Looper.getMainLooper());
        final DeviceChangeListener scanResultListener = new DeviceChangeListener() {
            @Override
            public void deviceChanged(AylaDevice device, Change change) {

                if (request.isCanceled()) {
                    return;
                }

                if (change instanceof PropertyChange &&
                        PROP_SCAN_RESULTS.equals(((PropertyChange)change).getPropertyName())) {
                    String results = (String) getProperty(PROP_SCAN_RESULTS).getValue();
                    AylaLog.d(LOG_TAG, "Got scan results:" + results);
                    if (results != null && results.length() > EMPTY_SCAN_RESULTS.length()) {
                        removeListener(this);
                        handler.removeCallbacksAndMessages(null);

                        try {
                            AylaRegistrationCandidate[] candidates = parseScanResult(results);
                            successListener.onResponse(candidates);
                        } catch (JsonSyntaxException e) {
                            String jsonExceptionMessage = "Invalid JSON result." + e;
                            AylaLog.d(LOG_TAG, jsonExceptionMessage);
                            errorListener.onErrorResponse(new JsonError(results, jsonExceptionMessage, e));
                        }
                    }
                }

            }

            @Override
            public void deviceError(AylaDevice device, AylaError error) {

            }

            @Override
            public void deviceLanStateChanged(AylaDevice device, boolean lanModeEnabled, AylaError error) {

            }
        };

        addListener(scanResultListener);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                removeListener(scanResultListener);
                String timeoutMessage = "Timed out waiting for scan result, No device found.";
                AylaLog.d(LOG_TAG, timeoutMessage);
                errorListener.onErrorResponse(new TimeoutError(timeoutMessage));
            }
        }, timeoutInSeconds * 1000);

    }

    /**
     * Waiting for the gateway scan result in a polling way. Internally, will check against the
     * gateway scan result property until a better scan result is available or the polling timed out.
     * In this way, we can get full scan result but may have to wait some extra time, which may end
     * up unnecessary. check another more efficient listening way in
     * {@link #listenScanResult(AylaAPIRequest, int, Response.Listener, ErrorListener)}.
     * @param request chained request (for canceling).
     * @param timeoutInSeconds the maximum allowed time for retrieving the scan results.
     * @param successListener Listener called with successful results.
     * @param errorListener Listener called with error results.
     */
    private void pollScanResult(final AylaAPIRequest request,
                                final int timeoutInSeconds,
                                final Response.Listener<AylaRegistrationCandidate[]> successListener,
                                final ErrorListener errorListener) {

        final Handler handler = new Handler(Looper.getMainLooper());
        final long targetTime = System.currentTimeMillis() + timeoutInSeconds * 1000;
        final long interval = 1000;

        handler.postDelayed(new Runnable() {

            private String previousNoneEmptyResult;

            @Override
            public void run() {
                if (request.isCanceled()) {
                    return;
                }

                String result = (String) getProperty(PROP_SCAN_RESULTS).getValue();
                AylaLog.i(LOG_TAG, "gateway scan result:" + result);

                if (result != null && result.length() > EMPTY_SCAN_RESULTS.length()) {
                    if (previousNoneEmptyResult != null) {
                        boolean gotBetterResult = result.length() > previousNoneEmptyResult.length();
                        if (gotBetterResult || System.currentTimeMillis() > targetTime) {
                            try {
                                AylaRegistrationCandidate[] candidates = parseScanResult(result);
                                successListener.onResponse(candidates);
                            } catch (JsonSyntaxException e) {
                                String jsonExceptionMessage = "Invalid JSON result." + e;
                                AylaLog.d(LOG_TAG, jsonExceptionMessage);
                                errorListener.onErrorResponse(new JsonError(result, jsonExceptionMessage, e));
                            }
                        } else {
                            handler.postDelayed(this, interval);
                        }
                    } else {
                        previousNoneEmptyResult = result;
                        handler.postDelayed(this, interval);
                    }
                } else {
                    if (System.currentTimeMillis() > targetTime) {
                        AylaLog.d(LOG_TAG, "No device found");
                        successListener.onResponse(new AylaRegistrationCandidate[]{});
                    } else {
                        handler.postDelayed(this, interval);
                    }
                }
            }
        }, interval);

    }

    /**
     * Parse the scan result, which is supposed to have a JSON format.
     * @param result scan result returned from gateway.
     * @return returns an array of registration candidates.
     */
    private AylaRegistrationCandidate[] parseScanResult(String result) {
        //  Here is an example JSON result.
        //[
        //        {
        //            "device": {
        //                    "bd_addr": "CF:66:24:27:E4:B8",
        //                    "rssi": "-66",
        //                    "name": "IDTQ133A",
        //                    "type": "Grillright"
        //            }
        //        },
        //        {
        //            "device": {
        //                    "bd_addr": "F8:1D:78:60:2F:C1",
        //                    "rssi": "-55",
        //                    "name": "LEDBLE-78602FC1",
        //                    "type": "MagicBlue"
        //            }
        //        },
        //        {
        //            "device": {
        //                    "bd_addr": "C5:5D:4E:EE:54:21",
        //                    "rssi": "-69",
        //                    "name": "Ayla",
        //                    "type": "AylaPowered"
        //            }
        //        }
        //]
        Gson gson = AylaNetworks.sharedInstance().getGson();
        ScannedNode.Wrapper[] wrappedNodes = gson.fromJson(result, ScannedNode.Wrapper[].class);

        int size = wrappedNodes == null ? 0 : wrappedNodes.length;
        List<AylaRegistrationCandidate> candidates = new ArrayList(size);

        if (size > 0) {
            ScannedNode[] scanResults = ScannedNode.Wrapper.unwrap(wrappedNodes);
            for (ScannedNode node : scanResults) {
                AylaRegistrationCandidate candidate = new AylaRegistrationCandidate();
                candidate.setProductName(node.name);
                candidate.setHardwareAddress(node.bd_addr);
                candidate.setOemModel(node.type);
                candidate.setRegistrationType(RegistrationType.Node);
                candidates.add(candidate);
            }
        }

        return candidates.toArray(new AylaRegistrationCandidate[size]);
    }


    /**
     * This method fetches an array of registration candidates for nodes visible to this gateway.
     *
     * @param successListener Listener to receive registration candidates
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to get registration candidates
     */
    public AylaAPIRequest fetchBleCandidates(
            final Response.Listener<AylaRegistrationCandidate[]> successListener,
            final ErrorListener errorListener) {

        return prepareNodeCandidates(true,
                BLE_NODE_PREPARATION_TIMEOUT_IN_SECONDS, successListener, errorListener);

    }

    /**
     * Register the specified BLE candidates. The BLE gateway does not inform the cloud about
     * devices when they are discovered. Instead, a candidate on the cloud for each BLE device
     * should be created before it is able to be registered. This is done by writing the
     * address of each candidate to the bt_connect_id property and waiting for the result returned
     * in bt_connect_result property. 'register' here especially means writing the bt_connect_id
     * property to make a registration candidate, the actual registration work is done
     * in {@link #registerNextCandidate(List, List, AylaAPIRequest, long, Response.Listener, ErrorListener)}.
     * <p>
     * <b>Note:</b> Should error happens when registering a candidate, the registration process
     * should continue so the next candidate have to chance to register, no matter
     * current registration result is success or failure.
     * </p>
     * @param candidates      Candidates to register
     * @param timeoutInSeconds the maximum allowed time for registering the candidates.
     * @param successListener Listener to receive the registered device nodes.
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest, which may be used to cancel the operation
     */
    public AylaAPIRequest registerBleCandidates(final AylaRegistrationCandidate[] candidates,
                                                final int timeoutInSeconds,
                                                final Response.Listener<AylaDeviceNode[]> successListener,
                                                final ErrorListener errorListener) {
        AylaAPIRequest chainedRequest = AylaAPIRequest.dummyRequest(
                AylaRegistrationCandidate[].class, successListener, errorListener);

        if (getProperty(PROP_CONNECT_ID) == null) {
            String error = String.format(ERROR_PROP_NOT_FOUND, PROP_CONNECT_ID);
            AylaLog.d(LOG_TAG, error);
            errorListener.onErrorResponse(new PreconditionError(error));
            return chainedRequest;
        }

        if (getProperty(PROP_CONNECT_RESULT) == null) {
            String error = String.format(ERROR_PROP_NOT_FOUND, PROP_CONNECT_RESULT);
            AylaLog.d(LOG_TAG, error);
            errorListener.onErrorResponse(new PreconditionError(error));
            return chainedRequest;
        }

        return registerCandidatesInParallel(chainedRequest,
                candidates, timeoutInSeconds, successListener, errorListener);
    }

    /**
     * Registering a node generally goes through three stages on the gateway side.
     * <ol>
     *     <li>Connect the node to the gateway.</li>
     *     <li>Add the connected node to the cloud.</li>
     *     <li>Update node template info to the cloud so as to complete the template association
     *     process, provided all node properties are discovered and registered in the gateway.</li>
     * </ol>
     *
     * The three stages internally correspond to three node states in the gateway: connected,
     * added and updated. Only when the node is found updated in the cloud then it's safely
     * to register it, otherwise, the registration may end up with following potential issues:
     *
     * <ul>
     *     <li>fetching the cloud candidate got 404 response, which means no candidate available
     *     because the node is not ready in the cloud, it may just be connected but not added
     *     to the cloud.</li>
     *     <li>fetching the properties of the registered node got an empty response, because
     *     the node may just be added to the cloud but not get updated yet, which means template
     *     association may not have been done in the cloud.</li>
     *
     * The gateway can partially parallelize the three stages, which can greatly
     * increase the efficiency of registering a bunch of nodes in a parallel way rather than
     * in a serial way. However, due to the restrictions on the gateway, the Bluetooth pairing
     * process can't be parallelized, which means the pairing process has to go in a serial way.
     * Keep this in mind, the best way to register nodes is to serialize the connecting process
     * and parallelize the adding and updating process.
     *
     * <pre>
     * Serial way:
     * -> start node1 registration -> node1 connected -> node1 added -> node1 updated
     * -> node1 registered -> start node2 registration -> node2 connected -> node2 added
     * -> node2 updated -> node2 registered -> start node3 registration ...
     *</pre>
     *
     * <pre>
     * Parallel way:
     * start node1 registration -> node1 connected -> node1 added -> node1 updated -> node1 registered;
     *                                                start node2 registration -> node2 connected -> node2 added -> node2 updated -> node2 registered
     *                                                                                               -> start node3 registration ...
     * </pre>
     *
     * Specifically, the algorithm of parallel registration nodes is:
     *
     * 1. request pair node1;
     * 2. wait until node1 becomes connected then repeat step 1 to request again to pair
     * next available node, until all added nodes become updated;
     * 3. register all updated nodes.
     */
    private AylaAPIRequest registerCandidatesInParallel(AylaAPIRequest chainedRequest,
                                                        AylaRegistrationCandidate[] candidates,
                                                        int timeoutInSeconds, Response.Listener<AylaDeviceNode[]> successListener,
                                                        ErrorListener errorListener) {
        List<AylaDeviceNode> registeredCandidates = new ArrayList<>(candidates.length);
        List<AylaRegistrationCandidate> cloudCandidates = new ArrayList<>();
        List<AylaRegistrationCandidate> localCandidates = new ArrayList<>();

        for (AylaRegistrationCandidate candidate : candidates) {
            if (candidate.getDsn() != null) {
                cloudCandidates.add(candidate);
            } else {
                localCandidates.add(candidate);
            }
        }

        if (cloudCandidates.size() > 0) {
            registerCandidatesCloud(cloudCandidates, registeredCandidates, chainedRequest,
                    new Response.Listener<AylaDeviceNode[]>() {
                        @Override
                        public void onResponse(AylaDeviceNode[] response) {
                            AylaLog.d(LOG_TAG, "registered " + response.length + " existing cloud candidates.");
                            if (response.length == candidates.length) {
                                successListener.onResponse(response);
                            }
                        }
                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError error) {
                            AylaLog.d(LOG_TAG, "failed to register cloud candidates.");
                        }
                    });
        }

        if (localCandidates.size() > 0) {
            Handler timeoutHandler = new Handler(Looper.getMainLooper());
            Map<String, AylaRegistrationCandidate> pendingUpdatedCandidates = new HashMap<>();
            final AylaRegistrationCandidate firstLocalCandidate = localCandidates.remove(0);
            final String address = firstLocalCandidate.getHardwareAddress();

            AylaLog.d(LOG_TAG, "Prepare to connect first local device " + address);
            getProperty(PROP_CONNECT_ID).createDatapoint(address, null,
                    new Response.Listener<AylaDatapoint>() {

                        @Override
                        public void onResponse(AylaDatapoint response) {
                            AylaLog.d(LOG_TAG, "Done setting BLE device " + address);
                            pendingUpdatedCandidates.put(address, firstLocalCandidate);
                        }

                    }, new ErrorListener() {

                        @Override
                        public void onErrorResponse(AylaError error) {
                            AylaLog.e(LOG_TAG, "Error connecting device " + address + ", " + error.getMessage());
                        }

                    });

            DeviceChangeListener deviceChangeListener = new DeviceChangeListener() {

                private DeviceChangeListener deviceChangeListener = this;
                private ConnectResult.ConnectionResultChangedListener connectionStatusChangedListener =
                        new ConnectResult.ConnectionResultChangedListener() {

                            @Override
                            public void onConnectResultChanged(String deviceAddress, int status, String detail) {
                                if (status == ConnectResult.CONNECTED && localCandidates.size() > 0) {
                                    AylaRegistrationCandidate nextLocalCandidate = localCandidates.remove(0);
                                    final String nextCandidateAddress = nextLocalCandidate.getHardwareAddress();
                                    AylaLog.d(LOG_TAG, "Prepare to connect next local device " + nextCandidateAddress);
                                    getProperty(PROP_CONNECT_ID).createDatapoint(nextCandidateAddress, null,
                                            new Response.Listener<AylaDatapoint>() {

                                                @Override
                                                public void onResponse(AylaDatapoint response) {
                                                    AylaLog.d(LOG_TAG, "Done setting local device " + nextCandidateAddress);
                                                    pendingUpdatedCandidates.put(nextCandidateAddress, nextLocalCandidate);
                                                }
                                            }, new ErrorListener() {

                                                @Override
                                                public void onErrorResponse(AylaError error) {
                                                    AylaLog.e(LOG_TAG, "Error connecting device "
                                                            + nextCandidateAddress + ", " + error.getMessage());
                                                }
                                            });
                                } else if (status == ConnectResult.UPDATED_TEMPLATE
                                        || status == ConnectResult.ERROR_NO_DEVICE_FOUND
                                        || status == ConnectResult.ERROR_UNKNOWN) {
                                    if (status < 0) {
                                        AylaLog.d(LOG_TAG, deviceAddress + " failed to connect gateway due to " + detail);
                                    } else {
                                        AylaLog.d(LOG_TAG, deviceAddress + " successfully updated in the cloud");
                                    }

                                    pendingUpdatedCandidates.remove(deviceAddress);

                                    if (pendingUpdatedCandidates.size() == 0) {

                                        timeoutHandler.removeCallbacksAndMessages(null);
                                        removeListener(deviceChangeListener);
                                        stopScanning();

                                        AylaBLEGateway.super.fetchRegistrationCandidates(new Response.Listener<AylaRegistrationCandidate[]>() {

                                            @Override
                                            public void onResponse(AylaRegistrationCandidate[] response) {
                                                List<AylaRegistrationCandidate> candidatesToRegister = new ArrayList<>(Arrays.asList(response));
                                                registerCandidatesCloud(candidatesToRegister, registeredCandidates, chainedRequest, successListener, errorListener);
                                            }
                                        }, new ErrorListener() {

                                            @Override
                                            public void onErrorResponse(AylaError error) {
                                                AylaLog.e(LOG_TAG, "fetch candidates error: " + error);
                                                errorListener.onErrorResponse(error);
                                            }
                                        });
                                    }
                                }
                            }
                        };

                @Override
                public void deviceChanged(AylaDevice device, Change change) {
                    if (change instanceof PropertyChange) {
                        PropertyChange propertyChange = (PropertyChange) change;
                        if (PROP_CONNECT_RESULT.equals(propertyChange.getPropertyName())) {
                            String result = (String) getProperty(PROP_CONNECT_RESULT).getValue();
                            if (result != null && result.length() > 0) {
                                try {
                                    JSONObject resultJson = new JSONObject(result);
                                    String address = resultJson.getString(RESULT_KEY_ADDR);
                                    int status = resultJson.getInt(RESULT_KEY_STATUS_CODE);
                                    String detail = resultJson.getString(RESULT_KEY_STATUS_DETAIL);
                                    AylaLog.i(LOG_TAG, address + " connect status:" + status + ", detail:" + detail);
                                    connectionStatusChangedListener.onConnectResultChanged(address, status, detail);
                                } catch (JSONException e) {
                                    AylaLog.e(LOG_TAG, "connect result parse error:" + e);
                                }
                            }
                        }
                    }
                }

                @Override
                public void deviceError(AylaDevice device, AylaError error) {
                    AylaLog.e(LOG_TAG, "got deviceError " + error);
                }

                @Override
                public void deviceLanStateChanged(AylaDevice device, boolean lanModeEnabled, AylaError error) {
                    AylaLog.d(LOG_TAG, "deviceLanStateChanged, " + lanModeEnabled + ", " + error);
                }
            };

            addListener(deviceChangeListener);
            timeoutHandler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    removeListener(deviceChangeListener);
                    if (registeredCandidates.size() > 0) {
                        successListener.onResponse(registeredCandidates.toArray(
                                new AylaDeviceNode[registeredCandidates.size()]));
                    } else {
                        errorListener.onErrorResponse(new TimeoutError("Register candidates timed out."));
                    }
                }
            }, timeoutInSeconds * 1000);
        }

        return chainedRequest;
    }

    /**
     * registering the specified nodes in a serial way. Check {@link #registerCandidatesInParallel(
     * AylaAPIRequest, AylaRegistrationCandidate[], int, Response.Listener, ErrorListener)}
     * for more details about the serial and parallel ways to register a set of nodes.
     */
    private AylaAPIRequest registerCandidatesRecursively(AylaAPIRequest chainedRequest,
                                                         AylaRegistrationCandidate[] candidates,
                                                         int timeoutInSeconds, Response.Listener<AylaDeviceNode[]> successListener,
                                                         ErrorListener errorListener) {
        List<AylaRegistrationCandidate> candidatesToRegister = new ArrayList<>(Arrays.asList(candidates));
        List<AylaDeviceNode> registeredCandidates = new ArrayList<>(candidates.length);
        registerNextCandidate(candidatesToRegister, registeredCandidates,
                chainedRequest, timeoutInSeconds * 1000, successListener, errorListener);

        return chainedRequest;
    }

    /**
     * Performs the actual registration on the cloud of the provided candidates. This step happens
     * after the candidates have been added and updated on the cloud.
     *
     * @param candidates Candidates returned from the superclass'
     * @param chainedRequest
     * @param successListener
     * @param errorListener
     */
    protected void registerCandidatesCloud(final List<AylaRegistrationCandidate> candidates,
                                           final List<AylaDeviceNode> registeredNodes,
                                           final AylaAPIRequest chainedRequest,
                                           final Response.Listener<AylaDeviceNode[]> successListener,
                                           final ErrorListener errorListener) {
        if (chainedRequest.isCanceled()) {
            return;
        }

        if (candidates.isEmpty()) {
            successListener.onResponse(registeredNodes.toArray(new AylaDeviceNode[registeredNodes.size()]));
            return;
        }

        AylaRegistrationCandidate candidate = candidates.remove(0);
        final AylaRegistration reg = getDeviceManager().getAylaRegistration();
        reg.registerCandidate(candidate, new Response.Listener<AylaDevice>() {
            @Override
            public void onResponse(AylaDevice response) {
                AylaDeviceNode node = (AylaDeviceNode)response;
                AylaLog.d(LOG_TAG, candidate.getMacAddress() + " registered.");
                registeredNodes.add(node);
                registerCandidatesCloud(candidates, registeredNodes, chainedRequest,
                        successListener, errorListener);
            }
        }, errorListener);
    }

    /**
     * <p> Registers the specified candidates in a recursive way. </p>
     *
     * <p>
     * <b>Note:</b> Should error happens when registering a candidate, the registration process
     * should continue so the next candidate have to chance to register, no matter
     * current registration result is success or failure.
     * </p>
     * @param candidates      Candidates to be registered.
     * @param timeoutInMilliseconds the maximum allowed time for registering the candidates.
     * @param successListener Listener to receive the successfully registered candidates. the size
     *                        may equal or less than the candidates to be registered.
     * @param errorListener   Listener to receive an error should one occur
     * @return the AylaAPIRequest, which may be canceled.
     */
    protected void registerNextCandidate(final List<AylaRegistrationCandidate> candidates,
                                         final List<AylaDeviceNode> registeredNodes,
                                         final AylaAPIRequest chainedRequest,
                                         final long timeoutInMilliseconds,
                                         final Response.Listener<AylaDeviceNode[]> successListener,
                                         final ErrorListener errorListener) {
        if (chainedRequest.isCanceled()) {
            return;
        }

        AylaLog.d(LOG_TAG, "register candidate timeout in " + timeoutInMilliseconds + " ms");
        final long targetTime = System.currentTimeMillis() + timeoutInMilliseconds;

        if (candidates.isEmpty() || System.currentTimeMillis() >= targetTime) {
            // explicitly stop scanning.
            stopScanning();

            if (registeredNodes.size() > 0) {
                successListener.onResponse(registeredNodes.toArray(
                        new AylaDeviceNode[registeredNodes.size()]));
            } else {
                String timeoutMessage = "Timed out registering the candidates.";
                AylaLog.d(LOG_TAG, timeoutMessage);
                errorListener.onErrorResponse(new TimeoutError(timeoutMessage));
            }
            return;
        }

        final AylaRegistrationCandidate candidate = candidates.remove(0);
        final AylaRegistration registration = getDeviceManager().getAylaRegistration();
        final String candidateAddress = candidate.getHardwareAddress();

        final Response.Listener<AylaDevice> innerSuccessListener = new Response.Listener<AylaDevice>() {
            @Override
            public void onResponse(AylaDevice response) {
                AylaDeviceNode newNode = (AylaDeviceNode) response;
                AylaLog.d(LOG_TAG, "registered device " + newNode.getDsn());
                registeredNodes.add(newNode);
                long remainingTime = targetTime - System.currentTimeMillis();
                registerNextCandidate(candidates, registeredNodes, chainedRequest,
                        remainingTime, successListener, errorListener);
            }
        };
        final ErrorListener innerErrorListener = new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                long remainingTime = targetTime - System.currentTimeMillis();
                registerNextCandidate(candidates, registeredNodes, chainedRequest,
                        remainingTime, successListener, errorListener);
            }
        };

        if (!TextUtils.isEmpty(candidate.getDsn())) {
            AylaLog.d(LOG_TAG, "register cloud candidate " + candidate.getDsn());
            registration.registerCandidate(candidate, innerSuccessListener, innerErrorListener);
        } else {
            AylaLog.d(LOG_TAG, "prepare to pair device " + candidateAddress);
            // Write bt_connect_id property with device address to start pairing between
            // gateway and node. we would also need to write the passkey to the
            // bt_connect_passkey property if we had devices that required passkeys.
            getProperty(PROP_CONNECT_ID).createDatapoint(candidateAddress, null,
                    new Response.Listener<AylaDatapoint>() {
                        @Override
                        public void onResponse(AylaDatapoint response) {
                            AylaLog.d(LOG_TAG, "Done setting BLE addr " + candidateAddress);

                            DeviceChangeListener connectResultChangeListener = new DeviceChangeListener() {

                                @Override
                                public void deviceChanged(AylaDevice device, Change change) {
                                    if (change instanceof PropertyChange
                                            && PROP_CONNECT_RESULT.equals(((PropertyChange)change).getPropertyName())
                                            && isNodeDeviceReady(candidateAddress)) {

                                        removeListener(this);

                                        Handler handler = new Handler(Looper.getMainLooper());
                                        final long candidatePollingInterval = 1 * 1000;
                                        final Runnable candidatePoller = new Runnable() {

                                            private final Runnable candidatePoller = this;

                                            @Override
                                            public void run() {

                                                AylaBLEGateway.super.fetchRegistrationCandidates(new Response.Listener<AylaRegistrationCandidate[]>() {
                                                    @Override
                                                    public void onResponse(AylaRegistrationCandidate[] cloudCandidates) {
                                                        for (AylaRegistrationCandidate updatedCandidate : cloudCandidates) {
                                                            if (TextUtils.equals(updatedCandidate.getMacAddress(), candidateAddress)) {
                                                                registration.registerCandidate(updatedCandidate, innerSuccessListener, innerErrorListener);
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }, new ErrorListener() {
                                                    @Override
                                                    public void onErrorResponse(AylaError error) {
                                                        AylaLog.d(LOG_TAG, "poll candidate " + candidateAddress + " error:" + error);
                                                        if (System.currentTimeMillis() < targetTime) {
                                                            handler.postDelayed(candidatePoller, candidatePollingInterval);
                                                        } else {
                                                            String message = "Timed out polling candidate " + candidateAddress;
                                                            TimeoutError timeoutError = new TimeoutError(message);
                                                            innerErrorListener.onErrorResponse(timeoutError);
                                                        }
                                                    }
                                                });
                                            }
                                        };
                                        handler.post(candidatePoller);
                                    }
                                }

                                @Override
                                public void deviceError(AylaDevice device, AylaError error) {

                                }

                                @Override
                                public void deviceLanStateChanged(AylaDevice device, boolean lanModeEnabled, AylaError error) {

                                }
                            };
                            addListener(connectResultChangeListener);
                        }

                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError error) {
                            AylaLog.d(LOG_TAG, "Error setting hardware address");
                            innerErrorListener.onErrorResponse(error);
                        }
                    });
        }
    }

    /**
     * Stop scanning for devices. Note that, when pairing is in process, gateway scanning
     * should not be stopped, otherwise it will remove scanned node from BLE protocol
     * stack, resulting in pairing failure.
     */
    protected void stopScanning() {
        EmptyListener<AylaAPIRequest.EmptyResponse> emptyListener = new EmptyListener<>();
        getProperty(PROP_ENABLE_SCAN).createDatapoint(0, null, emptyListener, emptyListener);
    }

    /**
     * Check against the bt_connect_result property to see if the specified node
     * is ready to register. Generally, the node device will go through a series of
     * states changes before it's ready to register.
     * <ol>
     *     <li>connected, means the device has (bluetooth) connected to the gateway.</li>
     *     <li>added, means the node has been added to cloud and becomes a cloud candidate.</li>
     *     <li>updated, means node properties have been ready on the gateway side, and gateway
     *         has finished requesting cloud to do template association. This state is the
     *         most time-consuming one, compared with others. The node is supposed to be ready
     *         to register when it gets in this state.</li>
     * </ol>
     * @param deviceAddress address of the node device.
     * @return true if the node devices was ready, otherwise returns false.
     */
    private boolean isNodeDeviceReady(String deviceAddress) {
        String result = (String) getProperty(PROP_CONNECT_RESULT).getValue();
        if (result != null && result.length() > 0) {
            try {
                JSONObject resultJson = new JSONObject(result);
                String address = resultJson.getString(RESULT_KEY_ADDR);
                int status = resultJson.getInt(RESULT_KEY_STATUS_CODE);
                String detail = resultJson.getString(RESULT_KEY_STATUS_DETAIL);
                if (TextUtils.equals(address, deviceAddress)) {
                    AylaLog.i(LOG_TAG, address + " connect status:" + status + ", detail:" + detail);
                }
                return (TextUtils.equals(address, deviceAddress))
                        && (status == ConnectResult.UPDATED_TEMPLATE);
            } catch (JSONException e) {
                AylaLog.e(LOG_TAG, "connect result parse error:" + e);
                return false;
            }
        }

        return false;
    }

    public void disconnectCandidate(final long timeoutInMilliseconds,
                                    final AylaAPIRequest chainedRequest,
                                    final AylaRegistrationCandidate candidate,
                                    final Response.Listener<AylaRegistrationCandidate> successListener,
                                    final ErrorListener errorListener) {

        if (getProperty(PROP_DISCONNECT_ID) == null) {
            errorListener.onErrorResponse(new PreconditionError(
                    String.format(ERROR_PROP_NOT_FOUND, PROP_DISCONNECT_ID)));
        } else if (getProperty(PROP_DISCONNECT_RESULT) == null) {
            errorListener.onErrorResponse(new PreconditionError(
                    String.format(ERROR_PROP_NOT_FOUND, PROP_DISCONNECT_RESULT)));
        }

        final String candidateAddress = candidate.getHardwareAddress();

        final DeviceChangeListener deviceChangeListener = new DeviceChangeListener() {
            @Override
            public void deviceChanged(AylaDevice device, Change change) {

                if (chainedRequest.isCanceled()) {
                    return;
                }

                if (change instanceof PropertyChange
                        && PROP_DISCONNECT_RESULT.equals(((PropertyChange)change).getPropertyName())
                        && isNodeDeviceDisconnected(candidateAddress)) {
                    removeListener(this);
                    successListener.onResponse(candidate);
                }
            }

            @Override
            public void deviceError(AylaDevice device, AylaError error) {

            }

            @Override
            public void deviceLanStateChanged(AylaDevice device, boolean lanModeEnabled, AylaError error) {

            }
        };
        addListener(deviceChangeListener);

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable timeoutChecker = new Runnable() {

            @Override
            public void run() {
                removeListener(deviceChangeListener);
                String error = "Timed out waiting for disconnecting result";
                AylaLog.e(LOG_TAG, error);
                errorListener.onErrorResponse(new TimeoutError(error));
            }
        };
        handler.postDelayed(timeoutChecker, timeoutInMilliseconds);

        getProperty(PROP_DISCONNECT_ID).createDatapoint(candidateAddress, null,
                new Response.Listener<AylaDatapoint>() {
                    @Override
                    public void onResponse(AylaDatapoint response) {
                        AylaLog.d(LOG_TAG,  "disconnect datapoint created.");
                    }
                },

                new ErrorListener() {
                    @Override
                    public void onErrorResponse(AylaError error) {
                        removeListener(deviceChangeListener);
                        handler.removeCallbacksAndMessages(null);
                        errorListener.onErrorResponse(error);
                    }
                }
        );
    }

    private boolean isNodeDeviceDisconnected(String deviceAddress) {
        boolean disconnectResult = false;
        String result = (String) getProperty(PROP_DISCONNECT_RESULT).getValue();
        AylaLog.d(LOG_TAG, "disconnect result:" + result);

        if (result != null && result.length() > 0) {

            try {
                JSONObject resultJson = new JSONObject(result);
                String addr = resultJson.getString(RESULT_KEY_ADDR);
                String detail = resultJson.getString(RESULT_KEY_STATUS_DETAIL);
                int statusCode = resultJson.getInt(RESULT_KEY_STATUS_CODE);

                AylaLog.d(LOG_TAG, "disconnect "  + deviceAddress +
                        ", result:[" + statusCode + ", detail" + detail + "]");

                if (TextUtils.equals(deviceAddress, addr)) {
                    if (DISCONNECT_RESULT_DISCONNECTED == statusCode) {
                        disconnectResult = true;
                        AylaLog.i(LOG_TAG, "disconnected node " + addr);
                    } else if (DISCONNECT_RESULT_DISCONNECTING == statusCode) {
                        AylaLog.i(LOG_TAG, "disconnecting node " + addr);
                    } else if (statusCode < 0) {
                        AylaLog.i(LOG_TAG, "disconnect node error" + addr);
                    } else {
                        AylaLog.i(LOG_TAG, "undefined status code:" + statusCode);
                    }
                }
            } catch (JSONException e) {
                AylaLog.e(LOG_TAG, "invalid JSON result" + e);
            }
        } else {
            AylaLog.i(LOG_TAG, "waiting for disconnect result.");
        }

        return disconnectResult;
    }
}
