package com.aylanetworks.aylasdk.metrics;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.aylanetworks.aylasdk.util.ServiceUrls;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Scanner;

/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

/**
 * AylaMetricsManager manages the collection and upload of metrics from the SDK to Ayla Log
 * service. When an event generates some metric, an AylaMetric object is created and added to the
 * metrics queue in AylaMetricsManager. AylaMetricsManager maintains this queue, converts them
 * to required formats, and uploads them to Ayla Log service.
 */
public class AylaMetricsManager {

    private final static String LOG_URL_PATH="api/v1/app/logs.json";
    private final static String METRICS_URL_PATH="api/v1/app/logs.json";
    public static final String AYLA_METRICS_PREFERENCES_KEY = "log_service_preferences";
    public static final String AYLA_TOTAL_CRASH_COUNT = "crash_count";
    public static final String AYLA_LAN_LOGIN = "lan_login_logs";
    public static final String METRICS_FILE_NAME = "pending_metrics_file.txt";
    private static final String LOG_TAG = "AylaMetricsManager";
    private static final int LOGS_STORAGE_TIME_MS = 24 * 3600* 1000;
    private static final int METRICS_RETRY_DELAY = 10000;
    private static final String DELIMITER = "\n\n";
    private Context _context;
    private final ArrayDeque<String> _pendingMetricsQueue;
    private static RequestQueue _logserviceRequestQueue;
    private long _lastLoggedCloudLatencyTime;
    private long _lastCloudLatency;
    private long _lastLoggedLANLatencyTime;
    private long _lastLANLatency;
    private boolean _isUploadPaused;
    private boolean _isUploadFinished;
    private boolean _enabled;

    public enum LogType{
        METRIC("Metric"),
        LOG("Log");

        private String _stringValue;
        LogType(String stringValue){
            _stringValue = stringValue;
        }
        public String stringValue(){
            return _stringValue;
        }
    }

    /**
     * Weak reference to the AylaSessionManager that represents this session.
     */
    private WeakReference<AylaSessionManager> _sessionManagerRef;

    public boolean isEnabled() {
        return _enabled;
    }

    public void enable(boolean enable) {
        this._enabled = enable;
        if(enable){
            startMetricsUpload();
        }
    }

    /**
     * Constructor.
     */
    public AylaMetricsManager(){
        _context = AylaNetworks.sharedInstance().getContext();
        _pendingMetricsQueue = new ArrayDeque<>();
        Cache cache = new DiskBasedCache(_context.getCacheDir(), 1024 * 1024);
        // Set up the HTTPURLConnection network stack
        Network network = new BasicNetwork(new HurlStack());
        _logserviceRequestQueue = new RequestQueue(cache, network);
        _logserviceRequestQueue.start();
        enable(true);
    }

    public void setSessionManagerRef(AylaSessionManager sessionManager) {
        this._sessionManagerRef = new WeakReference<AylaSessionManager>(sessionManager);
    }

    /**
     * Adds metric to the pending uploads queue maintained by this AylaMetricsManager
     * @param aylaMetric metric to be sent to Ayla service represented by an object of a subclass
     *                  of {@link AylaMetric}.
     */
    public void addMessageToUploadsQueue(AylaMetric aylaMetric){
        if(_enabled){
            try {
                final JSONObject logsJSON = new JSONObject(AylaNetworks.sharedInstance().getGson()
                        .toJson(aylaMetric));
                final JSONObject logsJsonObj = new JSONObject();
                logsJsonObj.put("logs", logsJSON);
                synchronized (_pendingMetricsQueue){
                    _pendingMetricsQueue.add(logsJsonObj.toString());
                    if(_isUploadFinished){
                        uploadMetrics();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void setCloudLatencyVariables(long latencyLogTime, long latencyInMs){
        this._lastLoggedCloudLatencyTime = latencyLogTime;
        this._lastCloudLatency = latencyInMs;
    }

    public void setLANLatencyVariables(long latencyLogTime, long latencyInMs){
        this._lastLoggedLANLatencyTime = latencyLogTime;
        this._lastLANLatency = latencyInMs;
    }

    /**
     * @return true if time elapsed after previous upload of cloud latency exceeds the latency
     * value.
     */
    public boolean shouldLogCloudLatency() {
        if((System.currentTimeMillis() - _lastLoggedCloudLatencyTime) >= _lastCloudLatency){
            return true;
        }
        return false;
    }

    /**
     * @return true if time elapsed after previous upload of LAN mode latency exceeds the latency
     * value.
     */
    public boolean shouldLogLANLatency() {
        if((System.currentTimeMillis() - _lastLoggedCloudLatencyTime) >= _lastLANLatency){
            return true;
        }
        return false;
    }


    /**
     * Uploads metrics from _pendingMetricsQueue, and saves to preferences if upload fails.
     */
    private void uploadMetrics(){
        if(_sessionManagerRef == null){
            return;
        }
        AylaLog.d(LOG_TAG, "uploadMetrics. queue size now "+ _pendingMetricsQueue.size());
        _isUploadFinished = false;
        final String logMessageString;
        if(_pendingMetricsQueue.isEmpty()){
            return;
        }
        synchronized (_pendingMetricsQueue){
            logMessageString = _pendingMetricsQueue.pollFirst();
        }
        if(logMessageString == null){
            return;
        }
        AylaLog.d(LOG_TAG, "Now uploading "+logMessageString);
        //Get AylaMetricsManager instance and start upload
        String url = AylaNetworks.sharedInstance().getServiceUrl(
                ServiceUrls.CloudService.Metrics, METRICS_URL_PATH);
        AylaSessionManager sessionManager = _sessionManagerRef.get();
        if (sessionManager == null) {
            AylaLog.d(LOG_TAG, "No session manager. Stopping scheduled job");
            return;
        }
        AylaJsonRequest<AylaAPIRequest.EmptyResponse> request = new AylaJsonRequest<>(
                Request.Method.POST, url, logMessageString, null,
                AylaAPIRequest.EmptyResponse.class, sessionManager,
                new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                    @Override
                    public void onResponse(AylaAPIRequest.EmptyResponse response) {
                        // Upload next log.
                        if(_pendingMetricsQueue.isEmpty()){
                            _isUploadFinished = true;
                        } else if(!isUploadPaused()){
                            _isUploadFinished = false;
                            uploadMetrics();
                        }
                    }
                }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.d(LOG_TAG, "Metrics upload failed " + error.getLocalizedMessage());
                try {
                    JSONObject logJsonObj = new JSONObject(logMessageString);
                    JSONObject logMessageObj = logJsonObj.getJSONObject("logs");
                    if((System.currentTimeMillis() - logMessageObj.getLong("time")) >
                            LOGS_STORAGE_TIME_MS){
                        Log.d(LOG_TAG, "Deleting logs older than 1 day");
                    } else{
                        synchronized (_pendingMetricsQueue){
                            _pendingMetricsQueue.addLast(logMessageString);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                //Continue upload
                if(_pendingMetricsQueue.isEmpty()){
                    _isUploadFinished = true;
                } else if(!isUploadPaused()){
                    _isUploadFinished = false;
                    new android.os.Handler(Looper.myLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            uploadMetrics();
                        }
                    }, METRICS_RETRY_DELAY);

                }
            }
        });
        sendLogServiceRequest(request);
    }

    /**
     * Enqueues the provided request to the Ayla Log Service.
     *
     * @param request the request to send
     * @return the request, which can be used for cancellation.
     */
    private static void sendLogServiceRequest(AylaAPIRequest request) {
        request.setShouldCache(false);
        request.logResponse();
        _logserviceRequestQueue.add(request);
    }
    /**
     * Discard AylaLogService messages.
     */
    public void cancelLogs(){
        _logserviceRequestQueue.stop();
    }

    public static void shutDown() {
        if (_logserviceRequestQueue != null) {
            _logserviceRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
                @Override
                public boolean apply(Request<?> request) {
                    return true;
                }
            });
            _logserviceRequestQueue = null;
        }
    }

    /**
     * Start upload to log service. This method also adds stored logs from previous sessions to the
     * upload queue.
     */
    private void startMetricsUpload() {
        AylaLog.d(LOG_TAG, "starting metrics upload");
        addStoredLogsInQueue();
        _isUploadPaused = false;
        uploadMetrics();
        AylaLog.uploadCrashLogsToLogService(new EmptyListener<>(), new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                AylaLog.d(LOG_TAG, "Log upload failed "+error.getMessage());
            }
        });

    }

    /**
     * Stop upload to log service.
     */
    public void stopMetricsUpload() {
        _isUploadPaused = true;
    }

    /**
     * @return true if upload is currently paused.
     */
    public boolean isUploadPaused() {
        return _isUploadPaused;
    }

    /**
     * Adds previously stored logs if any to the pending logs queue. This includes logs from
     * failed uploads and logs generated while we have no valid auth token to authenticate the user
     * with log service.
     */
    private void addStoredLogsInQueue(){
        AylaLog.d(LOG_TAG, " Getting previously saved logs from file");

        String metricsFilePath = getFilePath();
        File file = new File(metricsFilePath);
        if(file.exists()){
            Scanner scanner = null;
            try {
                scanner = new Scanner(file);
                scanner.useDelimiter(DELIMITER);
                while (scanner.hasNext()) {
                    String metric = scanner.next();
                    Log.d(LOG_TAG, "Adding metric from file "+metric);
                    if(metric != null){
                        _pendingMetricsQueue.addLast(metric);
                    }
                }
                scanner.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            file.delete();
        }

    }

    private void saveMetrics(){
        AylaLog.d(LOG_TAG, "Save metrics size "+_pendingMetricsQueue.size());
        String filePath = getFilePath();
        if(filePath == null){
            return;
        }
        File file = new File(filePath);
        if(!file.exists()){
            try {
                file.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                while(!_pendingMetricsQueue.isEmpty()){
                    String str = _pendingMetricsQueue.pollFirst();
                    if(str != null){
                        String formattedstr = getFormattedMetrics(str);
                        fileOutputStream.write(formattedstr.getBytes());
                        fileOutputStream.flush();
                    }
                }
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getFormattedMetrics(String message){
        StringBuilder builder = new StringBuilder(1);
        builder.append(message);
        builder.append(DELIMITER);
        return builder.toString();
    }

    private String getFilePath(){
       return _context.getFilesDir().getAbsolutePath().concat
                (File.pathSeparator+METRICS_FILE_NAME);
    }

    public void onResume(){
        AylaLog.d(LOG_TAG, "METRICS RESUME "+_enabled);
        if(isEnabled()){
            startMetricsUpload();
        }
    }

    public void onPause(){
        _isUploadPaused = true;
        if(isEnabled()){
            saveMetrics();
        }
    }
}
