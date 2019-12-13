package com.aylanetworks.aylasdk.ota;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.google.gson.annotations.Expose;

/**
 * Represents a lanota object from the Cloud for a specific Ayla device
 */
public class AylaOTAImageInfo {
    @Expose
    private String url;
    @Expose
    private String version;
    @Expose
    private String type;
    @Expose
    private String location;
    @Expose
    private long size;

    public String getType() { return type; }

    public String getVersion() { return version; }

    public String getUrl() { return url; }

    public String getLocation() { return location; }

    public long getSize() { return size; }

    public static class Wrapper {
        @Expose
        public AylaOTAImageInfo lanota;
    }
}