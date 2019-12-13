package com.aylanetworks.aylasdk;

import android.content.Context;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.util.ServiceUrls;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Android_Aura
 * <p/>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * AylaLogService class is responsible for sending log messages to Ayla Log service. This class
 * is used to send logs collected in the mobile phone when the device is not connected to the
 * internet.
 */
public class AylaLogService {

    private final static String LOG_TAG = "AYLA_LOG_SERVICE";
    private final static String LOG_URL_PATH="api/v1/app/logs.json";
    private static LinkedList<Map<String, String>> _logList;
    private static String _dsn;

    public static void setDsn(String dsn) {
        AylaLogService._dsn = dsn;
    }

    /**
     * Request queue for log service messages
     */
    private static RequestQueue logserviceRequestQueue;
    private static WeakReference<AylaSessionManager> sessionManagerRef;


    /**
     * Method to initialize AylaLogService.
     * @param sessionManager Active session manager for the current session.
     */
    public static void initLogService(AylaSessionManager sessionManager){
        _logList = new LinkedList<>();
        Context context = AylaNetworks.sharedInstance().getContext();
        Cache cache = new DiskBasedCache(context.getCacheDir(), 1024 * 1024);
        // Set up the HTTPURLConnection network stack
        Network network = new BasicNetwork(new HurlStack());
        logserviceRequestQueue = new RequestQueue(cache, network);
        logserviceRequestQueue.start();
        sessionManagerRef = new WeakReference<>(sessionManager);

    }

    /**
     * Add logs with given parameters to the AylaLogService queue.
     * @param module Mobile library module from which the logs are posted.
     * @param level Log level.
     * @param time Current time
     * @param text Log description.
     */
    public static void addLog(String module, String level, String time,
                              String text){

        Map<String, String> logParams = new HashMap<>(5);
        if(time == null){
            time = String.valueOf(System.currentTimeMillis());
        }
        if(level == null){
            level = "Info";
        }
        logParams.put("module", module);
        logParams.put("level", level);
        logParams.put("time", time);
        logParams.put("text", text);
        _logList.add(logParams);

    }


    private static AylaSessionManager getSessionManager(){
        if(sessionManagerRef.get() != null){
            return AylaNetworks.sharedInstance().getSessionManager(sessionManagerRef.get().
                    getSessionName());
        }
       return null;
    }

    /**
     * Send queued up messages to the Ayla Log service, and clear the queue. This method is to be
     * called after the mobile reconnects to internet connected LAN.
     *
     */
    public static void sendToLogService(){

       Runnable runnable = new Runnable() {
           @Override
           public void run() {
               try {
                   Thread.sleep(3000);
               } catch (InterruptedException e) {
                   e.printStackTrace();
               }
               String logJson = createLogJSON();
               _logList.clear();
               if(logJson == null){
                   AylaLog.d(LOG_TAG, "No logs to send");
                   return;
               }
               AylaLog.d(LOG_TAG, "Sending logs to LogService "+logJson);
               String url = AylaNetworks.sharedInstance().getServiceUrl(ServiceUrls.CloudService.Log, LOG_URL_PATH);
               AylaJsonRequest<AylaAPIRequest.EmptyResponse> request = new AylaJsonRequest<>(
                       Request.Method.POST, url, logJson, null, AylaAPIRequest.EmptyResponse.class,
                       getSessionManager(), new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                   @Override
                   public void onResponse(AylaAPIRequest.EmptyResponse response) {
                       AylaLog.d(LOG_TAG, "Log upload success");
                   }
               }, new ErrorListener() {
                   @Override
                   public void onErrorResponse(AylaError error) {
                       AylaLog.d(LOG_TAG, "Log upload failed "+error.getLocalizedMessage());
                   }
               });
               sendLogServiceRequest(request);
           }
       };

        Thread thread = new Thread(runnable);
        thread.start();

    }

    private static String createLogJSON(){
        if(_dsn == null){
            AylaLog.d(LOG_TAG, "DSN is null in AylaLogService. Logs not sent");
            return null;
        }
        if(_logList.isEmpty()){
            return null;
        }
        JSONObject logsJson = new JSONObject();
        JSONArray logArray = new JSONArray();
        for(Map<String, String> log: _logList){
            try {
                logsJson.put("dsn", _dsn);
                JSONObject logJson = new JSONObject();
                logJson.put("time", log.get("time"));
                logJson.put("mod", log.get("module"));
                logJson.put("text", log.get("text"));
                logJson.put("level", log.get("level"));
                logArray.put(logJson);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            logsJson.put("logs", logArray);
        } catch (JSONException e) {
            AylaLog.d(LOG_TAG, "JSONException in createLogs");
            return null;
        }
        return logsJson.toString();
    }

    /**
     * Enqueues the provided request to the Ayla Log Service.
     *
     * @param request the request to send
     * @return the request, which can be used for cancellation.
     */
    public static void sendLogServiceRequest(AylaAPIRequest request) {
        request.setShouldCache(false);
        request.logResponse();
        logserviceRequestQueue.add(request);
    }

    public static LinkedList<Map<String, String>> getLogList(){
        return _logList;
    }

    /**
     * Discard AylaLogService messages.
     */
    public void cancelLogs(){
        _logList.clear();
        logserviceRequestQueue.stop();
    }

    static void shutDown() {
        if (logserviceRequestQueue != null) {
            logserviceRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
                @Override
                public boolean apply(Request<?> request) {
                    return true;
                }
            });
        }
    }

}
