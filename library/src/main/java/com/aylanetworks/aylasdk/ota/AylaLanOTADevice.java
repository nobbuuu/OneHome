package com.aylanetworks.aylasdk.ota;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.MultipartProgressListener;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanModule;
import com.aylanetworks.aylasdk.setup.AylaSetup;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;
import com.aylanetworks.aylasdk.util.AylaPredicate;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import fi.iki.elonen.NanoHTTPD;

/**
 * AylaLanOTADevice is used for updating an OTA Image. Three steps are needed to update an OTA
 * Image over LAN
 * 1. Fetch the OTA Image Info from the Cloud service by passing the dsn.
 * 2. Fetch the OTA Image file from the Cloud Service and store in the Android phone/tablet.
 * 3. Push the OTA Image file from the Android phone/tablet to Ayla Device over LAN.
 */
public class AylaLanOTADevice extends AylaDevice {
    private static final String LOG_TAG = "AylaLanOTADevice";
    private final WeakReference<AylaDeviceManager> _deviceManagerRef;
    private final String _dsn;
    private String _lanIP;
    private String _localFilePath;
    public final static int HEADER_FILE_SIZE = 256;
    private final static int OTA_KEEP_ALIVE_INTERVAL = 300000;//5 minutes
    private final static int SCAN_TIME_OUT_SEC = 10;//10 seconds
    private final static int WAIT_TO_JOIN_SEC = 30;//30 seconds
    private LanOTAListener _lanOTAListener;
    private AylaSetup _aylaSetup;
    private AylaOTAImageInfo _otaImageInfo;
    private final String _deviceSsid;
    private AylaAPIRequest _origRequest;
    private int _statusCode =-1;
    private String _otaImageError;
    private static final String LOCATION_LOCAL="local";

    /**
     * Constructor for this class.
     * @param deviceManager the AylaDeviceManager that manages this device
     * @param dsn The DSN of the device
     * @param ssid SSID of the device. This is needed in case the Ayla Device is in AP Mode. If the
     *             device is already registered to the user and connected in STA Mode then it is
     *             optional.
     */
    public AylaLanOTADevice(AylaDeviceManager deviceManager, String dsn, String ssid) {
        _deviceManagerRef = new WeakReference<>(deviceManager);
        _dsn = dsn;
        _deviceSsid = ssid;
    }

    /**
     * Checks for OTA Image in the cloud for this device
     *
     * @param successListener Listener to receive the AylaLanOTA object on success
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to check OTA Image
     */
    public AylaAPIRequest fetchOTAImageInfo(final Response.Listener<AylaOTAImageInfo> successListener,
                                            final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = _deviceManagerRef.get();
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
        String url = deviceManager.deviceServiceUrl("apiv1/" + getDsn() + "/lan_ota.json");

        AylaAPIRequest<AylaOTAImageInfo.Wrapper> request = new
                AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaOTAImageInfo.Wrapper.class,
                sessionManager,
                new Response.Listener<AylaOTAImageInfo.Wrapper>() {
                    @Override
                    public void onResponse(AylaOTAImageInfo.Wrapper response) {
                        AylaOTAImageInfo aylaLanOTA = response.lanota;
                        successListener.onResponse(aylaLanOTA);
                    }
                }, errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Fetches an OTA Image from the cloud and stores in the Android phone/tablet at the
     * specified localFilePath location
     * @param otaImageInfo OTA Image info fetched from cloud
     * @param localFilePath  path to the local file to download the OTA Image
     * @param progressListener Listener for download progress
     * @param successListener  Listener to receive on successful download
     * @param errorListener    Listener to receive an Error should one occur
     */
    public void fetchOTAImageFile(final AylaOTAImageInfo otaImageInfo,
                                  final String localFilePath,
                                  final MultipartProgressListener progressListener,
                                  final Response.Listener<AylaAPIRequest.EmptyResponse>
                                          successListener,
                                  final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = _deviceManagerRef.get();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No device manager is " +
                        "available"));
            }
            return;
        }

        AylaSessionManager sessionManager = deviceManager.getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("No session is active"));
            }
            return;
        }
        if (otaImageInfo == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("otaImageInfo cannot be null" +
                        " call fetchOTAImageInfo method first."));
            }
            return;
        }
        final String authHeaderValue = sessionManager.getAuthHeaderValue();
        new OTATask(this,
                otaImageInfo,
                authHeaderValue,
                localFilePath,
                progressListener ,
                successListener,
                errorListener).execute();
    }

    /**
     * Pushes the OTA Image file from Android phone/tablet to the Ayla device
     * @param successListener Listener to receive on Success of Image update
     * @param errorListener Listener to receive an Error should one occur
     * @return the AylaAPIRequest object used to update Image
     */
    public AylaAPIRequest pushOTAImageToDevice(
            final Response.Listener<AylaAPIRequest.EmptyResponse> successListener,
            final ErrorListener errorListener) {
        final AylaDeviceManager deviceManager = _deviceManagerRef.get();
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
        if (_otaImageInfo == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("otaImageInfo cannot be null" +
                        " call fetchOTAImageFile method to get otaImageInfo."));
            }
            return null;
        }
        //here we need to check if the device is in device list if in device list just proceed
        // normal. Else do Use AylaSetup calls to connect to device and issue lan calls
        AylaDevice aylaDevice = deviceManager.deviceWithDSN(_dsn);
        if (aylaDevice != null) {
            _lanIP = aylaDevice.getLanIp();
        }
        _aylaSetup = null;
        if (_lanIP == null) {
            //Make sure that device SSID is not null
            if (_deviceSsid == null) {
                if (errorListener != null) {
                    errorListener.onErrorResponse(new PreconditionError("Device SSID cannot be " +
                            "null"));
                }
                return null;
            }
            return scanDevice(_otaImageInfo, sessionManager, successListener,
                    errorListener);
        } else {
            return doPushOTAImage(_otaImageInfo, successListener, errorListener, null);
        }
    }

    /**
     * Internal method to scan the device
     * @param otaImageInfo OTA Image info fetched from cloud
     * @param sessionManager AylaSessionManager
     * @param successListener Listener to receive on Success of scan
     * @param errorListener Listener to receive an Error should one occur
     * @return AylaAPIRequest object used to scan access points
     */
    private AylaAPIRequest scanDevice(final AylaOTAImageInfo otaImageInfo,
                                      final AylaSessionManager sessionManager,
                                      final Response.Listener<AylaAPIRequest.
                                              EmptyResponse> successListener,
                                      final ErrorListener errorListener) {
        try {
            Context ctx = AylaNetworks.sharedInstance().getContext();

            _aylaSetup = new AylaSetup(ctx, sessionManager);

            _origRequest = _aylaSetup.scanForAccessPoints(SCAN_TIME_OUT_SEC,
                    new AylaPredicate<ScanResult>() {
                        @Override
                        public boolean test(ScanResult scanResult) {
                            return scanResult.SSID.matches(_deviceSsid);
                        }
                    }, new Response.Listener<ScanResult[]>() {
                        @Override
                        public void onResponse(ScanResult[] scanResults) {
                            for (ScanResult result : scanResults) {
                                if (TextUtils.equals(result.SSID, _deviceSsid)) {
                                    connectAndPushImageToDevice(otaImageInfo, successListener,
                                            errorListener);
                                }
                            }
                        }
                    },
                    errorListener);

        } catch (AylaError e) {
            AylaLog.e(LOG_TAG,"scanDevice error:" +e.getMessage());
            errorListener.onErrorResponse(e);
            return null;
        }
        return _origRequest;
    }

    /**
     * Internal method to connect to the device and on success push the Image in LAN Mode
     * @param otaImageInfo otaImageInfo OTA Image info fetched from cloud
     * @param successListener successListener Listener to receive on Success of connect
     * @param errorListener Listener to receive an Error should one occur
     */
    private void connectAndPushImageToDevice(final AylaOTAImageInfo otaImageInfo,
                                             final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                     successListener,
                                             final ErrorListener errorListener) {
        if (!_origRequest.isCanceled()) {
            _aylaSetup.connectToNewDevice(_deviceSsid, WAIT_TO_JOIN_SEC, new Response
                    .Listener<AylaSetupDevice>() {
                @Override
                public void onResponse(AylaSetupDevice response) {
                    _lanIP = response.getLanIp();
                    doPushOTAImage(otaImageInfo, successListener, errorListener,
                            AylaLanOTADevice.this._origRequest);
                }

            }, errorListener);
        }
    }

    /**
     * Internal method to push OTA Image. We First try to push the Image assuming it is version 1
     * and where we pass the 256 header bytes inside json body. If this fails we push the OTA Image
     * as version 0 where the header bytes are appended to the JSON Body. Very few devices will
     * be of version 0.
     * @param otaImageInfo otaImageInfo OTA Image info fetched from cloud
     * @param successListener Listener to receive on Success of Image update
     * @param errorListener Listener to receive an Error should one occur
     * @param originalRequest original AylaAPIRequest
     * @return AylaAPIRequest to update the image
     */
    private AylaAPIRequest doPushOTAImage(final AylaOTAImageInfo otaImageInfo,
                                          final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                  successListener,
                                          final ErrorListener errorListener,
                                          final AylaAPIRequest originalRequest) {
        ErrorListener errListener =  new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                doPushOTAImageVersion0(otaImageInfo, successListener,errorListener,originalRequest);
            }
        };
        return doPushOTAImageVersion1(otaImageInfo, successListener,errListener,originalRequest);
    }
    /**
     * Internal method to push OTA Image. Most of the devices are of version 1 and we will pass the
     * 256 bytes of header data(base64 encoded) in the JSON body.
     * @param otaImageInfo otaImageInfo OTA Image info fetched from cloud
     * @param successListener Listener to receive on Success of Image update
     * @param errorListener Listener to receive an Error should one occur
     * @param originalRequest original AylaAPIRequest
     * @return AylaAPIRequest to update the image
     */
    private AylaAPIRequest doPushOTAImageVersion1(final AylaOTAImageInfo otaImageInfo,
                                                  final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                          successListener,
                                                  final ErrorListener errorListener,
                                                  final AylaAPIRequest originalRequest) {

        final AylaDeviceManager deviceManager = _deviceManagerRef.get();
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
        File file = new File(_localFilePath);
        if (!file.exists()) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("LAN OTA download file does not " +
                        "exist"));
            }
            return null;
        }
        //The downloaded file consists of 256 bytes of header data followed by the actual image
        // Get the header bytes first
        String headerBase64 = getHeaderBase64(file, errorListener);
        if (headerBase64 == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("LAN OTA image Header is empty"));
            }
            return null;
        }

        String mobileServerURL = mobileFileURL();
        if (mobileServerURL == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("Mobile LAN Server IP is null"));
            }
            return null;
        }

        int lanServerPort = lanServerPort();
        long fileSize = otaImageInfo.getSize();
        String url = lanOTAURL();
        if (url == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("device LAN IP is null"));
            }
            return null;
        }

        AylaLanModule lanModule = getLanModule();
        if (lanModule == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError(AylaSetup.LAN_PRECONDITION_ERROR));
            }
            return null;
        }

//            "ota": { "url": "http://<mobile_server_ip_addr>/bc-0.18.1.patch",
//                    "ver": "0.18.1", "size": 12300, "type": "module",
//                    "port": 8888,"head":"base64-header-from-first-256B-of-image"}

        final JSONObject jsonBody = new JSONObject();
        final JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("url", mobileServerURL);
            bodyObject.put("ver", otaImageInfo.getVersion());
            bodyObject.put("size", fileSize);
            bodyObject.put("type", otaImageInfo.getType());
            bodyObject.put("port", lanServerPort);
            bodyObject.put("head", headerBase64);
            jsonBody.put("ota", bodyObject);
        } catch (JSONException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create "
                        + "lanota json message", e));
            }
            return null;
        }
        AylaAPIRequest request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                jsonBody.toString(),
                null,
                AylaAPIRequest.EmptyResponse.class,
                sessionManager,
                successListener,
                errorListener);

        startOTALanSession(deviceManager.getLanServer());
        sessionManager.getDeviceManager().getLanServer().setOTADevice(this);
        lanModule.setKeepAliveInterval(OTA_KEEP_ALIVE_INTERVAL);

        if (originalRequest != null) {
            if (originalRequest.isCanceled()) {
                request.cancel();
            } else {
                originalRequest.setChainedRequest(request);
            }
        }
        lanModule.sendRequest(request);
        return request;
    }
    /**
     * Internal method to push OTA Image. Very few devices will be Version 0. We call this only
     * if version 1 fails. In version 0 the header bytes are appended to the JSON Body.
     * @param otaImageInfo otaImageInfo OTA Image info fetched from cloud
     * @param successListener Listener to receive on Success of Image update
     * @param errorListener Listener to receive an Error should one occur
     * @param originalRequest original AylaAPIRequest
     * @return AylaAPIRequest to update the image
     */
    private AylaAPIRequest doPushOTAImageVersion0(final AylaOTAImageInfo otaImageInfo,
                                                  final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                          successListener,
                                                  final ErrorListener errorListener,
                                                  final AylaAPIRequest originalRequest) {

        final AylaDeviceManager deviceManager = _deviceManagerRef.get();
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
        File file = new File(_localFilePath);
        if (!file.exists()) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("LAN OTA download file does not " +
                        "exist"));
            }
            return null;
        }
        //The downloaded file consists of 256 bytes of header data followed by the actual image
        // Get the header bytes first
        byte[] headerBytes = getHeader(file, errorListener);
        if (headerBytes == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("LAN OTA image Header is empty"));
            }
            return null;
        }

        String mobileServerURL = mobileFileURL();
        if (mobileServerURL == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("Mobile LAN Server IP is null"));
            }
            return null;
        }

        int lanServerPort = lanServerPort();
        long fileSize = otaImageInfo.getSize();
        String url = lanOTAURL();
        if (url == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("device LAN IP is null"));
            }
            return null;
        }

        if (getLanModule() == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError(AylaSetup.LAN_PRECONDITION_ERROR));
            }
            return null;
        }

//            "ota": { "url": "http://<mobile_server_ip_addr>/bc-0.18.1.patch",
//                    "ver": "0.18.1", "size": 12300, "type": "module",
//                    "port": 8888
        final JSONObject jsonBody = new JSONObject();
        final JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("url", mobileServerURL);
            bodyObject.put("ver", otaImageInfo.getVersion());
            bodyObject.put("size", fileSize);
            bodyObject.put("type", otaImageInfo.getType());
            bodyObject.put("port", lanServerPort);

            jsonBody.put("ota", bodyObject);
        } catch (JSONException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create "
                        + "lanota json message", e));
            }
            return null;
        }
        /**
         * We need to append null terminating character and also 256 bytes of header data to the
         * JSON Data. This is a hack and very inelegant way of doing this but this is the only way
         * the device update works currently.
         */
        StringBuilder sb = new StringBuilder(jsonBody.toString());
        final byte[] jsonBytes = sb.toString().getBytes();
        final byte[] postBodyBytes = new byte[jsonBytes.length + 1 + headerBytes.length];

        System.arraycopy(jsonBytes, 0, postBodyBytes, 0, jsonBytes.length);
        //Append null terminating character
        postBodyBytes[jsonBytes.length] = 0;
        //Finally add header bytes
        System.arraycopy(headerBytes, 0, postBodyBytes, jsonBytes.length+1, headerBytes.length);

        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaAPIRequest
                <AylaAPIRequest.EmptyResponse>(
                Request.Method.PUT,
                url,
                null,
                AylaAPIRequest.EmptyResponse.class,
                sessionManager,
                successListener,
                errorListener) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                return postBodyBytes;
            }

            @Override
            public String getBodyContentType() {
                return AylaHttpServer.MIME_JSON;
            }
        };
        startOTALanSession(deviceManager.getLanServer());
        sessionManager.getDeviceManager().getLanServer().setOTADevice(this);
        getLanModule().setKeepAliveInterval(OTA_KEEP_ALIVE_INTERVAL);

        if (originalRequest != null) {
            if (originalRequest.isCanceled()) {
                request.cancel();
            } else {
                originalRequest.setChainedRequest(request);
            }
        }
        getLanModule().sendRequest(request);
        return request;
    }

    public void setLanOTAListener(LanOTAListener listener) {
        _lanOTAListener = listener;
    }

    public String getDsn() {
        return _dsn;
    }

    public String getOTAPath() {
        return _localFilePath;
    }

    /**
     * This is called by the OTA Handler object for updating the status of the image process
     * @param statusCode status code like standard HTTP response codes. 200 means it succeeded
     * @param error error if any updated from module
     */
    public void setOTAUpdateStatus(int statusCode, String error) {
        _statusCode = statusCode;
        _otaImageError = error;
        if (_lanOTAListener != null) {
            _lanOTAListener.updateStatus(statusCode, error);
        }
    }

    /**
     * Once the Image is updated delete the OTA File
     * @return true if file is deleted else false
     */

    public boolean deleteOTAFile() {
        if (_localFilePath != null) {
            File file = new File(_localFilePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    AylaLog.i(LOG_TAG, "Unable to delete LAN OTA Image for " + getDsn());
                }
                return deleted;
            }
        }
        return false;
    }

    /**
     * Connects the mobile device back to the Original wifi network. User needs to call this once
     * OTA Image update is done.Also the status of the Image update is sent to Cloud
     * @param timeoutInSeconds Maximum time to spend trying to reconnect.
     * @param successListener  Listener to receive on Successful update of status after we have
     *                         connected to Original Network
     * @param errorListener    Listener called if we fail to join the network in the specified time
     *                         period or if we are not able to send the status to Cloud
     * @return the AylaAPIRequest that may be canceled to stop this operation
     */
    public AylaAPIRequest reconnectToOriginalNetwork(int timeoutInSeconds,
                                                     final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                             successListener,
                                                     final ErrorListener errorListener) {
        if (_aylaSetup == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("aylaSetup is null"));
            }
            return null;
        }

        return _aylaSetup.reconnectToOriginalNetwork(timeoutInSeconds, successListener,
                errorListener);

    }

    /**
     * Update the cloud service if we succeeded or failed in updating OTA Image.
     * @param successListener Listener to receive on Successful update of status
     * @param errorListener   Listener to receive an Error should one occur
     */

    public AylaAPIRequest updateOTADownloadStatus(final Response.Listener<AylaAPIRequest.EmptyResponse>
                                                 successListener,
                                         final ErrorListener errorListener) {

        //First check if there is a _statusCode from the Module
        if(_statusCode == -1) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("The Status Code from module " +
                        "is not updated, Make sure that pushOTAImageToDevice method is called " +
                        "first before invoking this method"));
            }
            return null;
        }
        final AylaDeviceManager deviceManager = _deviceManagerRef.get();
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

        boolean updateSuccess = false;
        if (NanoHTTPD.Response.Status.OK.getRequestStatus() == _statusCode) {
            updateSuccess = true;
        }

        String url = deviceManager.deviceServiceUrl("apiv1/lan_ota/dsn/" + getDsn() + ".json");

        final JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("status", updateSuccess);
            if (updateSuccess) {
                jsonBody.put("reason", "success");
            } else {
                jsonBody.put("reason", _otaImageError);
            }
        } catch (JSONException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create "
                        + "lanota json message for updating download status", e));
            }
            return null;
        }
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                jsonBody.toString(),
                null,
                AylaAPIRequest.EmptyResponse.class,
                sessionManager,
                successListener,
                errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }

    private String lanOTAURL() {
        return "http://" + _lanIP + "/" + "lanota.json";
    }

    private String mobileFileURL() {
        String lanServerIP = _deviceManagerRef.get().getLANServerIP();
        return "http://" + lanServerIP + _localFilePath;
    }

    private int lanServerPort() {
        return _deviceManagerRef.get().getLANServerPort();
    }

    /**
     * Internal method to get the first 256 bytes of the Image file
     * @param file Image File
     * @param errorListener Listener to receive an Error should one occur
     * @return the first 256 bytes
     */
    private byte[] getHeader(File file, final ErrorListener errorListener) {
        try {
            byte[] buffer = new byte[HEADER_FILE_SIZE];
            int bytesRead;
            int bufferSize = 0;
            BufferedInputStream bufInput = new BufferedInputStream(new FileInputStream(file));
            while ((bytesRead = bufInput.read(buffer)) != -1) {
                bufferSize += bytesRead;
                if (bufferSize == HEADER_FILE_SIZE) {
                    break;
                }
            }
            return buffer;
        } catch (FileNotFoundException ex) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("FileNotFoundException getHeader"
                        + ex.getMessage()));
            }
        } catch (IOException ex) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("IOException getHeader"
                        + ex.getMessage()));
            }
        }
        return null;
    }
    /**
     * Internal method to get string with first 256 bytes of the Image file (base64 encoded)
     * @param file Image File
     * @param errorListener Listener to receive an Error should one occur
     * @return the first 256 bytes base64 encoded string
     */
    private String getHeaderBase64(File file, final ErrorListener errorListener) {
        try {
            byte[] buffer = new byte[HEADER_FILE_SIZE];
            int bytesRead;
            int bufferSize = 0;
            BufferedInputStream bufInput = new BufferedInputStream(new FileInputStream(file));
            while ((bytesRead = bufInput.read(buffer)) != -1) {
                bufferSize += bytesRead;
                if (bufferSize == HEADER_FILE_SIZE) {
                    break;
                }
            }
            return Base64.encodeToString(buffer, Base64.NO_WRAP);
        } catch (FileNotFoundException ex) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("FileNotFoundException getHeader"
                        + ex.getMessage()));
            }
        } catch (IOException ex) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InternalError("IOException getHeader"
                        + ex.getMessage()));
            }
        }
        return null;
    }


    //This is the listner for LAN OTA image update
    public interface LanOTAListener {
        void updateStatus(int statusCode, String error);
    }

    static class OTATask extends AsyncTask<String, Long, String> {
        private WeakReference<AylaLanOTADevice> _device;
        MultipartProgressListener _progressListener;
        String _authHeaderValue;
        String _localFilePath;
        AylaOTAImageInfo _otaImageInfo;
        Response.Listener<AylaAPIRequest.EmptyResponse> _successListener;
        ErrorListener _errorListener;

        public OTATask(AylaLanOTADevice device,
                       final AylaOTAImageInfo otaImageInfo,
                       final String authHeaderValue,
                       final String localFilePath,
                       final MultipartProgressListener progressListener,
                       final Response.Listener<AylaAPIRequest.EmptyResponse>
                               successListener,
                       final ErrorListener errorListener) {

            _device = new WeakReference<>(device);
            _authHeaderValue = authHeaderValue;
            _localFilePath = localFilePath;
            _progressListener = progressListener;
            _successListener = successListener;
            _errorListener = errorListener;
            _otaImageInfo = otaImageInfo;
        }

        @Override
        protected String doInBackground(String... params) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                //check if operation is cancelled by user
                if (_progressListener != null && _progressListener.isCanceled()) {
                    return MultipartProgressListener.PROGRESS_CANCELED;
                }
                URL url = new URL(_otaImageInfo.getUrl());
                connection = (HttpURLConnection) url.openConnection();
                //Need Authorization just for local. For S3 no Auth token needed
                if(LOCATION_LOCAL.equalsIgnoreCase(_otaImageInfo.getLocation())) {
                    connection.setRequestProperty("Authorization", _authHeaderValue);
                }
                connection.setRequestProperty("Accept", "application/octet-stream");
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }
                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(_localFilePath);
                // Due to Chunked response the cloud not give the length in Content-Length and
                // we need to use Image-Length parameter.
                // refer to https://aylanetworks.atlassian.net/browse/SVC-3138.
                long fileLength = connection.getHeaderFieldInt("Image-Length", -1);


                byte data[] = new byte[4096];
                long total = 0;
                int count;
                Long[] arrayProgress = new Long[2];
                arrayProgress[0] =0L; //Initially it is 0 Bytes
                arrayProgress[1]= fileLength;

                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button or through progress Listener
                    if (isCancelled() || (_progressListener != null &&
                            _progressListener.isCanceled())) {
                        input.close();
                        return MultipartProgressListener.PROGRESS_CANCELED;
                    }
                    total += count;
                    // publishing the progress....
                    output.write(data, 0, count);
                    arrayProgress[0] = total;
                    // update progress
                    publishProgress(arrayProgress);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Long... progress) {
            super.onProgressUpdate(progress);
            if (_progressListener != null) {
                _progressListener.updateProgress(progress[0],progress[1]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                if (_errorListener != null) {
                    _errorListener.onErrorResponse(new InternalError("LAN OTA download " +
                            "error:" + result));
                }
            } else {
                _device.get()._localFilePath = _localFilePath;
                _device.get()._otaImageInfo = _otaImageInfo;
                _successListener.onResponse(new AylaAPIRequest.EmptyResponse());
            }
        }
    }
}
