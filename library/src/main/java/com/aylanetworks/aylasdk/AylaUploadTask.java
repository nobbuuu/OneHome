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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * AylaUploadTask is used to upload a file . Currently AylaDatapointBlob uses this to upload a
 * Blob file to Amazon S3 server.
 */
public class AylaUploadTask extends AsyncTask<String, Long, String> {
    private ErrorListener _errorListener = null;
    private MultipartProgressListener _progresslistener = null;
    private AsyncDataBlobResponse _delegate = null;
    private Response.Listener<EmptyResponse> _successListener = null;

    /**
     * Constructor for AylaUploadTask
     *
     * @param asyncResponse    class that implements AsyncDataBlobResponse object
     * @param progresslistener progress listener for the file upload
     * @param successListener  Listener to receive on successful upload of data
     * @param errorListener    Listener to receive an Error should one occur
     */
    public AylaUploadTask(AsyncDataBlobResponse asyncResponse,
                          MultipartProgressListener progresslistener,
                          Response.Listener<EmptyResponse> successListener,
                          ErrorListener errorListener) {
        _delegate = asyncResponse;
        _successListener = successListener;
        _errorListener = errorListener;
        if (progresslistener != null) {
            _progresslistener = progresslistener;
        }
        try {
            Runtime.getRuntime().exec("adb logcat AndroidRuntime:E *:S");
        }catch(Exception e) {

        }
    }

    @Override
    protected String doInBackground(String... params) {
        HttpURLConnection connection = null;
        DataOutputStream out=null;
        try {
            //check if operation is cancelled by user
            if(_progresslistener != null && _progresslistener.isCanceled()) {
                return MultipartProgressListener.PROGRESS_CANCELED;
            }
            File file = new File(params[1]);
            connection = (HttpURLConnection) new URL(params[0]).openConnection();
            connection.setRequestMethod("PUT");
            connection.setReadTimeout(10000);
            connection.setConnectTimeout(15000);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.addRequestProperty("Content-length", file.length() + "");
            connection.setRequestProperty("Content-Type", "application/octet-stream");

            out = new DataOutputStream(connection.getOutputStream());
            long total = 0;
            int bytesRead;
            byte buf[] = new byte[1024];
            long fileLength=file.length();
            BufferedInputStream bufInput = new BufferedInputStream(new FileInputStream(params[1]));
            Long[] arrayProgress = new Long[2];
            arrayProgress[0] = Long.valueOf(0); //Initially it is 0 Bytes
            arrayProgress[1]= fileLength;
            while ((bytesRead = bufInput.read(buf)) != -1) {
                //check if operation is cancelled by user
                if(_progresslistener != null && _progresslistener.isCanceled()) {
                    return MultipartProgressListener.PROGRESS_CANCELED;
                }
                // write output
                out.write(buf, 0, bytesRead);
                out.flush();
                total += bytesRead;
                arrayProgress[0] = total;//Update total bytes uploaded
                // update progress
                publishProgress((arrayProgress));
            }

            out.flush();
            out.close();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return readStream(connection.getInputStream());
            }

        } catch (Exception e) {
            return e.toString();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                return e.toString();
            }
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
        _delegate.uploadFinish(result, _successListener, _errorListener,_progresslistener);
    }

    private static String readStream(InputStream in) {
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            return e.toString();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

}

