package com.aylanetworks.aylasdk.localdevice;

/*
 * AylaSDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */


import android.os.Environment;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.AylaJsonRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanModule;
import com.aylanetworks.aylasdk.util.EmptyListener;
import com.google.gson.annotations.Expose;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;


/**
 * The AylaLocalDevice class represents an AylaDevice that does not have a persistent connection
 * to the Ayla Cloud Service, but rather relies on local connectivity (e.g. Bluetooth, NFC, etc.)
 * to talk to the device.
 *
 * In addition to providing connectivity with the IoT device, an AylaLocalDevice is also
 * responsible for updating the Ayla Cloud Service with datapoints and status from the local IoT
 * device when it is connected.
 *
 * Implementers must extend the AylaLocalDevice class to implement
 */
@SuppressWarnings("WeakerAccess")
public class AylaLocalDevice extends AylaDevice {
    private final static String LOG_TAG = "AylaLocalDevice";

    protected final Map<String, AylaLocalProperty> _localPropertyMap = new HashMap<>();

    // OTA
    private static final int HTTP_BUF_SIZE = 8192;
    private AylaAPIRequest _otaRequest;

    @Expose
    protected String unique_hardware_id;

    /**
     * Connects to the device locally. If the device cannot be reached, the errorListener will be
     * called with a PreconditionError. Otherwise, the successListener will be called with an
     * EmptyResponse indicating that the connection has been made.
     *
     * @param successListener Listener called when the connection is complete
     * @param errorListener Listener called with an error should one occur
     *
     * @return an AylaAPIRequest, which may be used to cancel the operation
     */
    public AylaAPIRequest connectLocal(Listener<AylaAPIRequest.EmptyResponse> successListener,
                                       ErrorListener errorListener) {
        return null;
    }

    /**
     * Disconnects the local connection to the device.
     * @param successListener Listener to receive the response when the operation succeeds
     * @param errorListener Listener called if an error occurs
     *
     * @return the AylaAPIRequest, which may be used to cancel the operation
     */
    public AylaAPIRequest disconnectLocal(Listener<AylaAPIRequest.EmptyResponse>
                                               successListener,
                                       ErrorListener errorListener){
        return null;
    }

    /**
     * Returns true if the mobile device is connected to the IoT device using the local
     * connection protocol (e.g. Bluetooth, etc.)
     *
     * @return true if the device is currently connected, false otherwise
     */
    public boolean isConnectedLocal(){
        return false;
    }

    /**
     * Called by the local device when a local connection is established, this method checks the
     * Ayla device service to see if there any pending commands for this device. Implementers of
     * LocalDevice classes should call this method whenever a local connection has been established.
     */
    protected void checkQueuedCommands() {
        // Fetch any pending commands from the service
        String path = "apiv1/dsns/" + getDsn() + "/cmds.json";
        if (getDeviceManager() == null) {
            AylaLog.e(LOG_TAG, "No device manager present to check queued commands");
            return;
        }

        String url = getDeviceManager().deviceServiceUrl(path);

        AylaAPIRequest<AylaDeviceCommand.Wrapper[]> request =
                new AylaAPIRequest<>(Request.Method.GET, url, null,
                        AylaDeviceCommand.Wrapper[].class, getSessionManager(),
                        new Listener<AylaDeviceCommand.Wrapper[]>() {
                            @Override
                            public void onResponse(AylaDeviceCommand.Wrapper[] response) {
                                processCommands(AylaDeviceCommand.unwrap(response));
                            }
                        },
                        new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError error) {
                                AylaLog.e(LOG_TAG, getDsn() + ": Error response when trying to " +
                                        "fetch commands:" + error.getMessage());
                            }
                        });
        getDeviceManager().sendDeviceServiceRequest(request);
    }

    protected void processCommands(AylaDeviceCommand[] commands) {
        // See if there are any commands we care about
        AylaLog.i(LOG_TAG, "Got commands!");
        for (AylaDeviceCommand cmd : commands) {
            AylaLog.d(LOG_TAG, "Command: " + cmd.resource + ": " + cmd.data);
            switch (cmd.resource) {
                case AylaDeviceCommand.CMD_OTA:
                LocalOTACommand otaCommand = (LocalOTACommand)cmd.getCommand();
                if (otaCommand != null) {
                    processOTA(otaCommand);
                }
                break;

                case AylaDeviceCommand.CMD_FACTORY_RESET:
                    processFactoryReset(cmd);
                    break;

                default:
                    AylaLog.e(LOG_TAG, "Unsupported command: " + cmd.resource);
            }
        }
    }

    /**
     * Called when a factory reset command has been received. Implementers should respond to this
     * call by performing a factory reset on the local device and acknowledging the command by
     * calling {@link #ackCommand(int, int, Listener, ErrorListener)} with the results.
     * @param cmd AylaDeviceCommand for the factory reset as received from the cloud service
     */
    protected void processFactoryReset(AylaDeviceCommand cmd) {
        AylaLog.i(LOG_TAG, "Factory reset command received. Subclasses should implement " +
                "processFactoryReset to handle.");
    }

    /**
     * Called when an OTA command has been received, this method will fetch the referenced image,
     * verify its checksum and call {@link #onOTAReceived(LocalOTACommand, String)}.
     * @param otaCommand
     */
    protected void processOTA(final LocalOTACommand otaCommand) {
        if (_otaRequest != null) {
            AylaLog.e(LOG_TAG, "processOTA called while OTA in progress!");
            return;
        }

        final AylaDeviceManager dm = getDeviceManager();
        if (dm == null) {
            AylaLog.e(LOG_TAG, "No device manager present for OTA fetch");
            return;
        }

        final String filename = Environment.getExternalStorageDirectory().toString()
                + "/" + getDsn() + "-ota.bin";

        // Request the OTA image from our cloud service. We will get back the filename where the
        // downloaded data is stored.
        _otaRequest = new AylaAPIRequest<String>(Request.Method.GET,
                otaCommand.api_url,
                null, String.class, getSessionManager(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                _otaRequest = null;
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError error) {
                _otaRequest = null;
            }
        }) {
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                // TODO: Seems like Volley / HttpURLConnection are automatically processing the
                // redirects? If that's the case, then we can remove this block.
                if (response.statusCode == NanoHTTPD.Response.Status.REDIRECT.getRequestStatus()) {
                    // Redirects should not use AylaAPIRequests, as they are not to Ayla servers
                    final String redirectURL = response.headers.get("Location");
                    if (redirectURL == null) {
                        AylaLog.e(LOG_TAG, "No redirect URL for OTA");
                        return Response.error(new VolleyError("No redirect URL for OTA"));
                    }

                    // Download the image
                    URL url;
                    URLConnection conn;
                    try {
                        // Download the OTA image (without device manager)
                        url = new URL(otaCommand.api_url);
                        conn = url.openConnection();
                        InputStream is = url.openStream();
                        OutputStream os = new FileOutputStream(filename);

                        int bytesRead;
                        byte[] buf = new byte[HTTP_BUF_SIZE];
                        while ((bytesRead = is.read(buf)) != -1) {
                            os.write(buf, 0, bytesRead);
                        }

                        os.close();
                        is.close();
                        AylaLog.d(LOG_TAG, "Finished downloading OTA: " + filename);

                        return Response.success(filename, HttpHeaderParser.parseCacheHeaders(response));
                    } catch (MalformedURLException e) {
                        AylaLog.e(LOG_TAG, "Failed to create URL: " + otaCommand.api_url + " " +
                                e.getMessage());
                        return Response.error(new VolleyError(
                                "Failed to create URL: " + otaCommand.api_url +
                                        " " + e.getMessage()));
                    } catch (IOException e) {
                        AylaLog.e(LOG_TAG, "Failed to open URL: " + otaCommand.api_url + " " +
                                e.getMessage());
                        return Response.error(new VolleyError(
                                "Failed to open URL: " + otaCommand.api_url +
                                        " " + e.getMessage()));
                    }
                } else {
                    // We were not redirected; we have the data now. Write it to the file.

                    try {
                        OutputStream os = new BufferedOutputStream(new FileOutputStream(filename));
                        os.write(response.data);
                        os.flush();
                        os.close();
                    } catch (FileNotFoundException e) {
                        AylaLog.e(LOG_TAG, "Failed writing OTA to file: " + e.getMessage());
                        return Response.error(new VolleyError("Failed writing OTA to file", e));
                    } catch (IOException e) {
                        AylaLog.e(LOG_TAG, "Failed writing OTA to file: " + e.getMessage());
                        return Response.error(new VolleyError("Failed writing OTA to file", e));
                    }

                    return Response.success(filename, HttpHeaderParser.parseCacheHeaders(response));
                }
            }

            @Override
            public void deliverResponse(String response) {
                if (verifyChecksum(filename, otaCommand.checksum)) {
                    onOTAReceived(otaCommand, filename);
                    _successListener.onResponse(response);
                } else {
                    AylaLog.e(LOG_TAG, "Image failed checksum: " + otaCommand.api_url);
                    File f = new File(filename);
                    if (f.delete()) {
                        AylaLog.i(LOG_TAG, "Downloaded file deleted");
                    } else {
                        AylaLog.i(LOG_TAG, "Downloaded file could not be deleted");
                    }
                }
            }
        };

        dm.sendDeviceServiceRequest(_otaRequest);
    }

    /**
     * Verifies the MD5 checksum of a downloaded image
     * @param filename Path to the file to verify
     * @param checksum Checksum to check
     * @return true if the checksum matches, false otherwise
     */
    private boolean verifyChecksum(String filename, String checksum) {
        if (filename == null || checksum == null) {
            return false;
        }

        if (checksum.length() != 32) {
            return false;
        }

        File file = new File(filename);
        byte[] buf = new byte[8192];
        int read;
        InputStream is;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            is = new FileInputStream(file);
            while ((read = is.read(buf)) > 0) {
                digest.update(buf, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            output = String.format("%32s", output).replace(' ', '0');
            AylaLog.d(LOG_TAG, "Calculated: " + output + "  Provided: " + checksum);
            return TextUtils.equals(output, checksum);
        } catch (NoSuchAlgorithmException e) {
            AylaLog.e(LOG_TAG, "verifyChecksum: No such algorithm");
            return false;
        } catch (FileNotFoundException e) {
            AylaLog.e(LOG_TAG, "File not found: " + filename);
        } catch (IOException e) {
            AylaLog.e(LOG_TAG, "IO exception reading " + filename + ": " + e.getMessage());

        }
        return false;
    }

    /**
     * Developers should implement this method to handle an OTA image update. This method will be
     * called when an OTA image has been detected and downloaded. Once the image has been
     * downloaded, the OTA command should be acknowledged by calling {@link #setOTAStatus}.
     * @param otaCommand Command contained within the device command, in this case an OTA command
     * @param filename Local path to the file containing the OTA image
     */
    protected void onOTAReceived(LocalOTACommand otaCommand, String filename) {

    }

    /**
     * Sets the status of an OTA software update. After performing the OTA update of the local
     * device, the application must call setOTAStatus to report the success or failure of the
     * update.
     *
     * Successful updates should set the status to 0. Any non-zero value is considered a failure
     * and is defined by the manufacturer.
     *
     * This method will also ack the command, so a separate call to
     * {@link #ackCommand(int, int, Listener, ErrorListener)} is not needed.
     *
     * @param status Zero for success, or an app-specific error code
     * @param commandId ID of the OTA command that started this OTA update
     * @param successListener Listener called if the operation is successful
     * @param errorListener Listener called with an error should one occur
     * @return the AylaAPIRequest, which may be used to cancel the operation.
     */
    public AylaAPIRequest setOTAStatus(final int status,
                                       final int commandId,
                                       final Listener<AylaAPIRequest.EmptyResponse> successListener,
                                       final ErrorListener errorListener) {
        final int cmd_status = (status == 0 ? 200 : 400);

        final AylaDeviceManager deviceManager = getDeviceManager();

        // Endpoint for sending OTA status
        String url = deviceManager.deviceServiceUrl("apiv1/dsns/" + getDsn() +
                "/ota_status.json");

        String json = "{\"type\":\"host_mcu\", \"status\": " + status + "}";


        AylaAPIRequest statusRequest = new AylaJsonRequest<AylaAPIRequest.EmptyResponse>(
                Request.Method.PUT, url, json, null, AylaAPIRequest.EmptyResponse.class,
                getSessionManager(), new Listener<AylaAPIRequest.EmptyResponse>() {
            @Override
            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                ackCommand(commandId, cmd_status, successListener, errorListener);
            }
        }, errorListener);

        deviceManager.sendDeviceServiceRequest(statusRequest);

        return statusRequest;
    }

    /**
     * Sets the status of a device command. This method should be called after a device command
     * (OTA update, for example) has completed. The command ID can be found in the processed
     * command, and the status should reflect an HTTP status (e.g. 200 for success).
     * @param commandId ID of the command that was processed
     * @param status HTTP status for the result of the command operation
     * @param successListener Listener called if the operation is successful
     * @param errorListener Listener called if the operation failed
     * @return the AylaAPIRequest, which may be used to cancel the operation
     */
    public AylaAPIRequest ackCommand(int commandId, int status,
                                     Listener<AylaAPIRequest.EmptyResponse> successListener,
                                     ErrorListener errorListener) {
        AylaDeviceManager dm = getDeviceManager();
        if (dm == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("Command may not be null"));
            }
            return null;
        }
        String url = dm.deviceServiceUrl("apiv1/dsns/" + getDsn() + "/cmds/" + commandId
                + "/ack.json");
        String json = String.format(Locale.US, "{\"status\": %d}", status);
        AylaJsonRequest<AylaAPIRequest.EmptyResponse> request = new AylaJsonRequest<>(
                Request.Method.PUT, url, json, null, AylaAPIRequest.EmptyResponse.class,
                getSessionManager(), successListener, errorListener);
        dm.sendDeviceServiceRequest(request);
        return request;
    }

    /**
     * Converts an AylaProperty into an AylaLocalProperty. Local devices should override this
     * method to return the appropriate AylaLocalProperty for their device.
     *
     * @param property the AylaProperty received from the Ayla Cloud Service to be converted into
     *                 an AylaLocalProperty
     * @param <T> The base type of the property
     * @return the AylaLocalProperty for the device.
     */
    @SuppressWarnings("unchecked")
    protected <T> AylaLocalProperty<T> convertPropertyToLocal(AylaProperty<T> property) {
        // Find our local version of the property
        AylaLocalProperty<T> localProperty;
        synchronized (_localPropertyMap) {
            localProperty = (AylaLocalProperty<T>) _localPropertyMap.get
                    (property.getName());
            if (localProperty == null) {
                localProperty = new AylaLocalProperty<>(property, this);
                _localPropertyMap.put(property.getName(), localProperty);
            }
        }
        return localProperty;
    }

    /**
     * Callback from AylaLocalProperty to obtain its current value.
     * @param property Property being queried
     * @param <T> Type of the property being queried
     * @return the value this property should indicate
     */
    public <T> T getValueForProperty(AylaLocalProperty<T> property) {
        return null;
    }

    /**
     * Returns true if the given property is writable. The LocalDevice cloud implementation
     * requires that all properties are "To Device", which usually implies read-only. This method
     * allows each property's writability to be determined by the owning device.
     *
     * @param property Property to determine writability of
     * @return true if read-only, false if writable
     */
    public boolean isPropertyReadOnly(AylaLocalProperty property) {
        return true;
    }

    /**
     * Callback from AylaLocalProperty to write a value. Implementers must set the corresponding
     * value on the physical device as well as echo the new property value to the cloud in the
     * form of a datapoint. See
     * {@link AylaProperty#createDatapoint(Object, Map, int, Listener, ErrorListener)} for details.
     * @param property Property whose value is to be set
     * @param value Value to set the property to
     * @param <T> Base type of the property
     * @param successListener Listener to receive the results of a successful operation. The
     *                        response should be called with the new value that was set.
     * @param errorListener Listener called with an error should one occur
     *
     * @return an AylaAPIRequest representing the asynchronous request, which may be canceled
     */
    public <T> AylaAPIRequest<T> setValueForProperty(AylaLocalProperty<T> property,
                                                     T value,
                                                     Listener<T> successListener,
                                                     ErrorListener errorListener) {
        return null;
    }
    /**
     * Returns the hardware address for this device. This identifier is used during device
     * registration.
     *
     * @return the unique identifier for this device
     */
    public String getHardwareAddress() {
        return unique_hardware_id;
    }

    /**
     * Indicates whether or not this device requires additional local configuration. This might
     * be Bluetooth pairing, mobile device authentication with the IoT device, etc. Devices that
     * require local configuration should return true from this method until the local
     * configuration steps have been completed.
     *
     * @return false if no additional local configuration is requried, true if local
     * configuration is required to interact with the local device
     */
    public boolean requiresLocalConfiguration() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AylaLocalDevice)) {
            return false;
        }
        AylaLocalDevice localDevice = (AylaLocalDevice)other;
        return getHardwareAddress().equals(localDevice.getHardwareAddress());
    }

    @Override
    public int hashCode() {
        if (getHardwareAddress() == null) return 0;
        return getHardwareAddress().hashCode();
    }

    @Override
    public boolean isLanEnabled() {
        return false;
    }

    @Override
    public String getLanIp() {
        return null;
    }

    @Override
    public void setLanIp(String ip) {
        throw new RuntimeException("setLanIp does not apply for AylaLocalDevices");
    }

    @Override
    public String getSsid() {
        return null;
    }

    @Override
    public RegistrationType getRegistrationType() {
        return RegistrationType.Local;
    }

    @Override
    public boolean isLanModePermitted() {
        return false;
    }

    @Override
    public boolean startPolling() {
        return false;
    }

    @Override
    public void stopPolling() {
        // Do nothing
    }

    @Override
    public void startLanSession(AylaHttpServer httpServer) {
        // Do nothing
    }

    @Override
    public void stopLanSession() {
        // Do nothing
    }

    @Override
    public AylaLanModule getLanModule() {
        return null;
    }


    /**
     * All requests to fetch properties (including fetchPropertiesCloud and fetchPropertiesLAN)
     * are routed through this method for local devices.
     *
     * The local device implementation must return an array of AylaLocalProperty objects to the
     * success listener in order for the property management to work.
     *
     * The implementation in AylaLocalDevice requests the properties from the cloud, and calls
     * {@link #convertPropertyToLocal(AylaProperty)}
     * for each property and returns the array of AylaLocalProperty objects to the caller.
     *
     * @param propertyNames   Array of names of properties to fetch, or null to fetch all properties
     * @param successListener Listener to receive the successful result
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest, which may be canceled
     */
    @Override
    public AylaAPIRequest fetchProperties(String[] propertyNames,
                                          final Listener<AylaProperty[]> successListener,
                                          final ErrorListener errorListener){
        return super.fetchPropertiesCloud(propertyNames, new Listener<AylaProperty[]>() {
            @Override
            public void onResponse(AylaProperty[] response) {
                if (response.length == 0) {
                    successListener.onResponse(response);
                    return;
                }

                // Convert the returned property array to AylaLocalProperties
                AylaLocalProperty[] localProperties = new AylaLocalProperty[response.length];
                for (int i = 0; i < response.length; i++) {
                    localProperties[i] = convertPropertyToLocal(response[i]);
                }

                successListener.onResponse(localProperties);
            }
        }, errorListener);
    }

    /**
     * Pass-through method to fetchProperties. AylaLocalDevices do not use LAN mode per-se, and
     * only use the cloud service to obtain values that were previously uploaded by the mobile
     * application.
     *
     * @param propertyNames   Array of names of properties to fetch, or null to fetch all properties
     * @param successListener Listener to receive the successful result
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest, which may be canceled
     */
    @Override
    public AylaAPIRequest fetchPropertiesCloud(String[] propertyNames,
                                               final Listener<AylaProperty[]> successListener,
                                               ErrorListener errorListener){
        return fetchProperties(propertyNames, successListener, errorListener);
    }

    /**
     * Pass-through method to fetchProperties. AylaLocalDevices do not use LAN mode per-se, and
     * only use the cloud service to obtain values that were previously uploaded by the mobile
     * application.
     *
     * @param propertyNames   Array of names of properties to fetch, or null to fetch all properties
     * @param successListener Listener to receive the successful result
     * @param errorListener   Listener to receive errors in case of failure
     * @return the AylaAPIRequest, which may be canceled
     */
    @Override
    public AylaAPIRequest fetchPropertiesLAN(String[] propertyNames,
                                                      Listener<AylaProperty[]> successListener,
                                                      ErrorListener errorListener){
        return fetchProperties(propertyNames, successListener, errorListener);
    }

    /**
     * Overridden to return our list of local properties instead of the list of properties
     * @return the list of properties as AylaLocalProperty objects
     */
    @Override
    public List<AylaProperty> getProperties() {
        synchronized (_localPropertyMap) {
            List<AylaProperty> props = new ArrayList<>(_localPropertyMap.size());
            for (AylaProperty prop : _localPropertyMap.values()) {
                props.add(prop);
            }
            return props;
        }
    }

    @Override
    public AylaProperty getProperty(String propertyName) {
        synchronized (_localPropertyMap) {
            return _localPropertyMap.get(propertyName);
        }
    }

    public AylaAPIRequest factoryReset(Listener<AylaAPIRequest.EmptyResponse> successListener,
                                       ErrorListener errorListener) {
        // Implementers should implement this method if the device supports factory reset
        return null;
    }

    @Override
    public AylaAPIRequest unregister(Listener<AylaAPIRequest.EmptyResponse> successListener, ErrorListener errorListener) {
        EmptyListener<AylaAPIRequest.EmptyResponse> emptyListener = new EmptyListener<>();
        disconnectLocal(emptyListener, emptyListener);
        return super.unregister(successListener, errorListener);
    }

    @Override
    public AylaAPIRequest deleteWifiProfile(String profileSsid,
                                            Listener<AylaAPIRequest.EmptyResponse> successListener,
                                            ErrorListener errorListener) {
        errorListener.onErrorResponse(new PreconditionError("Local devices do not support " +
                "deletion of wifi profiles"));
        return null;
    }

    /**
     * Returns JSON sent to the service to obtain a registration candidate for devices of this type.
     * @param oem OEM of the account the device should be registered to
     * @return JSON used to register the device. Subclasses must override this method to return
     * the correct JSON to obtain a registration candidate for this device.
     */
    public String getCandidateJson(String oem) {
        AylaLocalRegistrationCandidate rc = new AylaLocalRegistrationCandidate();
        rc.device.unique_hardware_id = getHardwareAddress();
        rc.device.oem_model = getOemModel();
        rc.device.oem = oem;
        rc.device.model = getModel();
        rc.device.sw_version = getSwVersion();

        return AylaNetworks.sharedInstance().getGson().toJson(rc);
    }

    // Used internally during registration
    public void setDsn(String newDsn) {
        dsn = newDsn;
    }
}
