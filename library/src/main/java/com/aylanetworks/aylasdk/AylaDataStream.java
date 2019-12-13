package com.aylanetworks.aylasdk;

import com.google.gson.annotations.Expose;

/*
 * Android_AylaSDK
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */
public class AylaDataStream<T> {
    @Expose
    private String seq;
    @Expose
    private AylaDatapoint<T> datapoint;
    @Expose
    private AylaDSSMetadata metadata;
    @Expose
    private AylaDeviceConnection connection;

    public String getSeq() {
        return seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    public AylaDatapoint getDatapoint() {
        return datapoint;
    }

    public void setDatapoint(AylaDatapoint datapoint) {
        this.datapoint = datapoint;
    }

    public AylaDSSMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(AylaDSSMetadata metadata) {
        this.metadata = metadata;
    }

    public AylaDeviceConnection getConnection() {
        return connection;
    }
}
