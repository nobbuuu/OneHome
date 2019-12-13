package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.google.gson.annotations.Expose;

public class AylaDatapointBatchResponse<T> {
    @Expose
    private String dsn;
    @Expose
    private String name;
    @Expose
    private int status;
    @Expose
    private AylaDatapoint<T> datapoint;

    public String getDsn() { return dsn; }

    public String getName() { return name; }

    public int getStatus() { return status; }

    public AylaDatapoint<T> getDatapoint() { return datapoint; }
}
