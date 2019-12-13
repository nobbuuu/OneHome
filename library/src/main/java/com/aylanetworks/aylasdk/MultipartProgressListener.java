package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * MultipartProgressListener is used by AylaUploadTask and AylaDownloadTask classes. The progress is
 * the percentage of file uploaded or downloaded and it varies from 0 to 100. The client apps
 * need to use this to track the progress.
 */
public interface MultipartProgressListener {
    String PROGRESS_CANCELED ="File Transfer is canceled";
    boolean isCanceled();
    void updateProgress(long sentOrRecvd, long total);
}
