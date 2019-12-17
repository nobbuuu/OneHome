package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;

/**
 * AylaDatapointBlob is used for upload and download of file properties to service
 */

public class AylaDatapointBlob extends AylaDatapoint<String> implements AsyncDataBlobResponse {
    private static final String LOG_TAG = "AylaDatapointBlob";
    //Cloud URL of the datapoint
    @Expose
    private String file;
    //Indicates if file has been uploaded completely
    @Expose
    private boolean closed;

    private AylaProperty property;

    //The flag to indicate weather to send markAsFetched to Cloud after the file download. Once we
    // call markAsFetched method is success it deletes the file data point
    private boolean _sendFetchFlagStatus = true;

    public void setSendFetchFlag(boolean flag) {
        _sendFetchFlagStatus = flag;
    }

    /**
     * Constructor with a {@link AylaProperty}, whose value will be set into value field.
     *
     * @param property A file property
     */
    public AylaDatapointBlob(AylaProperty property) {
        this.property = property;

        this.value = String.valueOf(property.getValue());
    }

    AylaDatapointBlob(AylaDatapoint other) {
        updateFrom(other);
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public void setProperty(AylaProperty property) {
        this.property = property;
    }

    /**
     * Uploads the data blob(byte array) from the local file to the cloud.
     *
     * @param progressListener Listener for the upload progress
     * @param localFilePath    local file path that has the byte data
     * @param successListener  Listener to receive on successful Upload of data
     * @param errorListener    Listener to receive an Error should one occur
     */
    public void uploadBlob(final MultipartProgressListener progressListener,
                           final String localFilePath,
                           final Response.Listener<EmptyResponse> successListener,
                           final ErrorListener errorListener) {
        if (this.property == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid property"));
            }
            return;
        }

        if (file == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid file URL"));
            }
            return;
        }
        String urlPath = file;
        final File localFile = new File(localFilePath);
        if (!localFile.exists()) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("Invalid file path"));
            }
            return;
        }

        new AylaUploadTask(this, progressListener, successListener, errorListener).execute(urlPath,
                localFilePath);
    }

    /**
     * After the Blob upload is successful Mark a stream data point operation as finished on the
     * device service
     *
     * @param successListener Listener to receive on success
     * @param errorListener   Listener to receive an Error should one occur
     * @return the AylaAPIRequest object used to mark a stream data point operation as complete
     */
    private AylaAPIRequest markAsComplete(final Response.Listener<EmptyResponse> successListener,
                                          final ErrorListener errorListener,
                                          final MultipartProgressListener progressListener) {
        if (this.property == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid property"));
            }
            return null;
        }
        final AylaDeviceManager deviceManager = this.property.getDeviceManager(errorListener);
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
        String url = value;
        if (url == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("Location path is null"));
            }
            return null;
        }

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.PUT,
                url,
                null,
                EmptyResponse.class,
                sessionManager,
                new Response.Listener<EmptyResponse>() {
                    @Override
                    public void onResponse(EmptyResponse response) {
                        successListener.onResponse(response);
                    }
                }, errorListener);

        if (progressListener != null && progressListener.isCanceled()) {
                request.cancel();
        } else {
            deviceManager.sendDeviceServiceRequest(request);
        }
        return request;
    }

    /**
     * downloads the data blob(byte array) from the cloud to the local file. There are 3 steps
     * involved
     * 1) Data from a file datapoint is downloaded by calling an HTTP GET on the location URL
     * representing the datapoint
     * 2) then an HTTP GET on the file URL returned in step 1.(This url is on path to an amazon s3
     * service hosting the file)
     * 3) Finally After the Blob download is successful Mark a stream data point operation as
     * fetched on the device service
     *
     * @param filePath         path to the local file to download to
     * @param progressListener Listener for download progress
     * @param successListener  Listener to receive on successful download
     * @param errorListener    Listener to receive an Error should one occur
     * @return the AylaAPIRequest object
     */
    public AylaAPIRequest downloadToFile(final String filePath,
                                         final MultipartProgressListener progressListener,
                                         final Response.Listener<EmptyResponse> successListener,
                                         final ErrorListener errorListener) {
        if (filePath == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid file Path"));
            }
            return null;
        }
        if (this.property == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid property"));
            }
            return null;
        }
        final AylaDeviceManager deviceManager = this.property.getDeviceManager(errorListener);
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
        String url = value;
        if (value == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid file URL"));
            }
            return null;
        }
        AylaAPIRequest<Wrapper> request = new
                AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                Wrapper.class,
                sessionManager,
                new Response.Listener<Wrapper>() {
                    @Override
                    public void onResponse(Wrapper response) {
                        AylaDatapointBlob datapointBlob = response.datapoint;
                        downLoadFromAWS(progressListener, datapointBlob.file, filePath,
                                successListener, errorListener);
                    }
                }, errorListener);

        if (progressListener != null && progressListener.isCanceled()) {
            request.cancel();
        } else {
            deviceManager.sendDeviceServiceRequest(request);
        }
        return request;
    }

    /**
     * Download the file hosted on the Amazon S3 Service.
     *
     * @param progressListener Listener for download progress
     * @param urlPath          the URL Path to download from
     * @param localFilePath    path to the local file to download to
     * @param successListener  Listener to receive on successful download
     * @param errorListener    errorListener Listener to receive an Error should one occur
     */
    private void downLoadFromAWS(final MultipartProgressListener progressListener,
                                 final String urlPath,
                                 final String localFilePath,
                                 final Response.Listener<EmptyResponse> successListener,
                                 final ErrorListener errorListener) {
        if (this.property == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid property"));
            }
            return;
        }

        if (urlPath == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid file URL"));
            }
            return;
        }
        new AylaDownloadTask(this, progressListener, successListener, errorListener).execute(
                urlPath, localFilePath);
    }

    /**
     * After the Blob download is successful Mark a stream data point operation as fetched on the
     * device service
     *
     * @param successListener Listener to receive on success
     * @param errorListener   Listener to receive an Error should one occur
     * @return the AylaAPIRequest object used to mark a stream data point operation as fetched
     */
    private AylaAPIRequest markAsFetched(final Response.Listener<EmptyResponse> successListener,
                                         final ErrorListener errorListener,
                                         final MultipartProgressListener progressListener) {
        if (this.property == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new InvalidArgumentError("Invalid property"));
            }
            return null;
        }
        final AylaDeviceManager deviceManager = this.property.getDeviceManager(errorListener);
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
        final JSONObject jsonBody = new JSONObject();
        final JSONObject bodyObject = new JSONObject();
        try {
            bodyObject.put("fetched", "true");
            jsonBody.put("datapoint", bodyObject);
        } catch (JSONException e) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new JsonError(null, "JSONException trying to create "
                        + "markAsFetched json " + "message", e));
            }
            return null;
        }
        String url = value;
        if (url == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new PreconditionError("Location path is null"));
            }
            return null;
        }
        AylaAPIRequest request = new AylaJsonRequest<>(
                Request.Method.PUT,
                url,
                jsonBody.toString(),
                null,
                EmptyResponse.class,
                sessionManager,
                new Response.Listener<EmptyResponse>() {
                    @Override
                    public void onResponse(EmptyResponse response) {
                        successListener.onResponse(response);
                    }
                }, errorListener);

        if (progressListener != null && progressListener.isCanceled()) {
            request.cancel();
        } else {
            deviceManager.sendDeviceServiceRequest(request);
        }
        return request;
    }

    /**
     * downloadFinish is called from AsynTask after download of file is finished
     *
     * @param error           any error in downloading of file
     * @param successListener Listener to receive on success
     * @param errorListener   Listener to receive an Error should one occur
     */
    public void downloadFinish(String error,
                               final Response.Listener<EmptyResponse> successListener,
                               final ErrorListener errorListener,
                               final MultipartProgressListener progressListener) {
        if (error != null) {
            errorListener.onErrorResponse(new AylaError(AylaError.ErrorType
                    .AylaError, error));
        } else {
            if (_sendFetchFlagStatus) {
                markAsFetched(successListener, errorListener, progressListener);
            } else {
                successListener.onResponse(new EmptyResponse());
            }
        }
    }

    /**
     * uploadFinish is called from AsynTask after upload of file is finished
     *
     * @param error           any error in uploading of file
     * @param successListener Listener to receive on success
     * @param errorListener   Listener to receive an Error should one occur
     */
    public void uploadFinish(String error,
                             final Response.Listener<EmptyResponse> successListener,
                             final ErrorListener errorListener,
                             final MultipartProgressListener progressListener) {
        if (error != null) {
            errorListener.onErrorResponse(new AylaError(AylaError.ErrorType
                    .AylaError, error));
        } else {
            markAsComplete(successListener, errorListener,progressListener);
        }

    }

    public static class Wrapper {
        @Expose
        public AylaDatapointBlob datapoint;

        public static AylaDatapointBlob[] unwrap(Wrapper[] container) {
            AylaDatapointBlob[] datapoints = new AylaDatapointBlob[container.length];
            for (int i = 0; i < container.length; i++) {
                datapoints[i] = container[i].datapoint;
            }
            return datapoints;
        }
    }
}