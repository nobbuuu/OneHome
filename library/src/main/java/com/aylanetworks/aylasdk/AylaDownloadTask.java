package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.os.AsyncTask;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * AylaDownloadTask is used to download a file . Currently AylaDatapointBlob uses this to download a
 * Blob file from Amazon S3 server.
 */

public class AylaDownloadTask extends AsyncTask<String, Long, String> {
    private AsyncDataBlobResponse _delegate = null;
    private Response.Listener<EmptyResponse> _successListener = null;
    private ErrorListener _errorListener = null;
    private MultipartProgressListener _progresslistener = null;

    /**
     * Constructor for AylaDownloadTask
     * @param asyncResponse class that implements AsyncDataBlobResponse object
     * @param progresslistener progress listener for the file download
     * @param successListener Listener to receive on successful download of data
     * @param errorListener Listener to receive an Error should one occur
     */
    public AylaDownloadTask(AsyncDataBlobResponse asyncResponse,
                            MultipartProgressListener progresslistener,
                            Response.Listener<EmptyResponse> successListener,
                            ErrorListener errorListener) {
        _delegate = asyncResponse;
        _successListener = successListener;
        _errorListener = errorListener;
        if (progresslistener != null) {
            _progresslistener = progresslistener;
        }
    }

    @Override
    protected String doInBackground(String... params) {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            if(params.length <2) {
                return "Invalid Parameters";
            }
            //check if operation is cancelled by user
            if(_progresslistener != null && _progresslistener.isCanceled()) {
                return MultipartProgressListener.PROGRESS_CANCELED;
            }
            URL url = new URL(params[0]);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage();
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            long fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            output = new FileOutputStream(params[1]);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            Long[] arrayProgress = new Long[2];
            arrayProgress[0] = Long.valueOf(0); //Initially it is 0 Bytes
            arrayProgress[1]= fileLength;

            while ((count = input.read(data)) != -1) {
                // allow canceling with back button or through progress Listener
                if (isCancelled() || (_progresslistener != null &&
                        _progresslistener.isCanceled())) {
                    input.close();
                    return MultipartProgressListener.PROGRESS_CANCELED;
                }
                total += count;
                // publishing the progress....
                output.write(data, 0, count);
                arrayProgress[0] = total;// Update total bytes downloaded
                // update progress
                publishProgress((arrayProgress));
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
        if (_progresslistener != null) {
            _progresslistener.updateProgress(progress[0],progress[1]);
        }
    }

    @Override
    protected void onPostExecute(String result) {
        _delegate.downloadFinish(result, _successListener, _errorListener,_progresslistener);
    }
}

