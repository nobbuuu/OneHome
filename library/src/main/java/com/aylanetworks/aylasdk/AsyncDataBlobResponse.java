package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.AylaAPIRequest.EmptyResponse;

/**
 * AsyncDataBlobResponse is used by AylaUploadTask and AylaDownloadTask classes. The method
 * downloadFinish is called by  AylaDownloadTask once the file is downloaded and similarly
 * uploadFinish is called by AylaUploadTask when the upload of file is finished.
 */

public interface AsyncDataBlobResponse {
    void downloadFinish(String error,
                        Response.Listener<EmptyResponse> successListener,
                        ErrorListener errorListener,
                        MultipartProgressListener progressListener);

    void uploadFinish(String error,
                      Response.Listener<EmptyResponse> successListener,
                      ErrorListener errorListener,
                      MultipartProgressListener progressListener);
}
