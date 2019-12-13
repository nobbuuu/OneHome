package com.aylanetworks.aylasdk;

import com.google.gson.annotations.Expose;

/*
 * Android_AylaSDK
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */
public class AylaDSSMetadata {
    @Expose
    private String oemId;
    @Expose
    private String oemModel;
    @Expose
    private String dsn;
    @Expose
    private String propertyName;
    @Expose
    private String displayName;
    @Expose
    private String baseType;
    @Expose
    private String eventType;

    public String getDsn() {
        return dsn;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getEventType() {
        return eventType;
    }
}
